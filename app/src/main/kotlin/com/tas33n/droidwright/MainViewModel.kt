/**
 * Copyright (c) 2025 tas33n
 *
 * Licensed under the MIT License
 * See LICENSE file or https://opensource.org/licenses/MIT
 *
 * @author tas33n
 * @see <a href="https://github.com/tas33n/droidwright">GitHub</a>
 */
package com.tas33n.droidwright

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tas33n.droidwright.data.models.AutomationScript
import com.tas33n.droidwright.data.models.LogLevel
import com.tas33n.droidwright.data.models.PermissionOverview
import com.tas33n.droidwright.data.models.ScriptValidationMessage
import com.tas33n.droidwright.data.models.ScriptValidationResult
import com.tas33n.droidwright.data.repository.PermissionRepository
import com.tas33n.droidwright.data.repository.ScriptRepository
import com.tas33n.droidwright.domain.UIAutomatorEngine
import com.tas33n.droidwright.domain.ScriptMetadata
import com.tas33n.droidwright.domain.ScriptMetadataParser
import com.tas33n.droidwright.domain.ScriptTemplates
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel : ViewModel() {

    val automatorEngine = UIAutomatorEngine

    private val _scripts = MutableStateFlow<List<AutomationScript>>(emptyList())
    val scripts: StateFlow<List<AutomationScript>> = _scripts.asStateFlow()

    private val _activeScriptId = MutableStateFlow<String?>(null)
    val activeScriptId: StateFlow<String?> = _activeScriptId.asStateFlow()

    private val _permissionOverview = MutableStateFlow<PermissionOverview?>(null)
    val permissionOverview: StateFlow<PermissionOverview?> = _permissionOverview.asStateFlow()

    private val _toastEvents = MutableSharedFlow<String>()
    val toastEvents = _toastEvents.asSharedFlow()

    init {
        ScriptRepository.scriptsFlow()
            .onEach { _scripts.value = it }
            .launchIn(viewModelScope)
    }

    fun refreshPermissions(context: Context) {
        // This can be called from any thread - StateFlow is thread-safe
        _permissionOverview.value = PermissionRepository.getPermissionOverview(context)
    }

    fun handleRuntimePermissionResult(context: Context, result: Map<String, Boolean>) {
        val granted = result.values.all { it }
        viewModelScope.launch {
            if (granted) {
                _toastEvents.emit("All runtime permissions granted")
            } else {
                _toastEvents.emit("Some runtime permissions were denied")
            }
        }
        refreshPermissions(context)
    }

    fun saveScript(draft: ScriptDraft) {
        if (draft.code.isBlank()) {
            viewModelScope.launch { _toastEvents.emit("Script code cannot be empty") }
            return
        }

        val parsed = try {
            parseScriptMetadataOrThrow(draft.code)
        } catch (e: IllegalArgumentException) {
            viewModelScope.launch { _toastEvents.emit("Invalid script: ${e.message}") }
            return
        }
        val metadata = parsed.metadata

        viewModelScope.launch {
            val existing = draft.id?.let { ScriptRepository.getScriptById(it) }
            val scriptId = existing?.id ?: ScriptRepository.nextScriptId()

            val script = AutomationScript(
                id = scriptId,
                name = metadata.name!!.trim(),
                description = metadata.description!!.trim(),
                code = draft.code,
                tags = parsed.tags,
                lastExecuted = existing?.lastExecuted ?: 0L,
                isRunning = existing?.isRunning ?: false,
                lastValidatedAt = draft.lastValidatedAt ?: existing?.lastValidatedAt ?: 0L
            )

            if (existing == null) {
                ScriptRepository.addScript(script)
                _toastEvents.emit("Created script ${script.name}")
            } else {
                ScriptRepository.updateScript(script)
                _toastEvents.emit("Updated script ${script.name}")
            }
        }
    }

    fun deleteScript(scriptId: String) {
        viewModelScope.launch {
            val name = ScriptRepository.getScriptById(scriptId)?.name
            ScriptRepository.deleteScript(scriptId)
            _toastEvents.emit("Deleted script ${name ?: ""}".trim())
            if (_activeScriptId.value == scriptId) {
                stopAutomation()
            }
        }
    }

    fun importScript(context: Context, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val content = readContent(context, uri)
                if (content.isBlank()) {
                    _toastEvents.emit("File is empty or could not be read")
                    return@runCatching
                }
                val parsed = parseScriptMetadataOrThrow(content)
                val script = AutomationScript(
                    id = ScriptRepository.nextScriptId(),
                    name = parsed.metadata.name!!.trim(),
                    description = parsed.metadata.description!!.trim(),
                    code = content,
                    tags = parsed.tags
                )
                ScriptRepository.addScript(script)
                _toastEvents.emit("Imported ${script.name}")
            }.onFailure { e ->
                _toastEvents.emit("Failed to import script: ${e.message ?: "Unknown error"}")
            }
        }
    }

    fun importScriptFromUrl(url: String) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) {
            viewModelScope.launch { _toastEvents.emit("URL cannot be empty") }
            return
        }

        viewModelScope.launch {
            runCatching {
                val content = fetchRemoteScript(trimmed)
                if (content.isBlank()) {
                    throw IllegalStateException("Remote script is empty")
                }
                val parsed = parseScriptMetadataOrThrow(content)
                val script = AutomationScript(
                    id = ScriptRepository.nextScriptId(),
                    name = parsed.metadata.name!!.trim(),
                    description = parsed.metadata.description!!.trim(),
                    code = content,
                    tags = parsed.tags
                )
                ScriptRepository.addScript(script)
                _toastEvents.emit("Imported ${script.name}")
            }.onFailure { e ->
                _toastEvents.emit("Failed to import URL: ${e.message ?: "Unknown error"}")
            }
        }
    }

    fun executeScript(context: Context, script: AutomationScript) {
        if (automatorEngine.isRunning.value) {
            viewModelScope.launch { _toastEvents.emit("Automation already running") }
            return
        }

        viewModelScope.launch {
            _activeScriptId.value = script.id
            markScriptRunning(script.id, true)
            _toastEvents.emit("Starting ${script.name}")
            
            val result = automatorEngine.executeScript(script)
            if (result.status == "ok") {
                markScriptExecuted(script.id)
                _toastEvents.emit("${script.name} finished: ${result.note ?: "Completed"}")
            } else {
                _toastEvents.emit("${script.name} failed: ${result.note ?: "Unknown error"}")
            }
            
            markScriptRunning(script.id, false)
            _activeScriptId.value = null
        }
    }

    suspend fun stopAutomation() {
        automatorEngine.stopExecution()
        viewModelScope.launch {
            _toastEvents.emit("Automation stopped")
        }
        val activeId = _activeScriptId.value
        if (activeId != null) {
            markScriptRunning(activeId, false)
            _activeScriptId.value = null
        }
    }

    fun pauseAutomation() {
        automatorEngine.pauseExecution()
        viewModelScope.launch {
            _toastEvents.emit("Automation paused")
        }
    }

    fun resumeAutomation() {
        automatorEngine.resumeExecution()
        viewModelScope.launch {
            _toastEvents.emit("Automation resumed")
        }
    }

    fun validateScript(code: String): ScriptValidationResult = ScriptValidator.validate(code)

    fun exportScriptById(context: Context, scriptId: String, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val script = ScriptRepository.getScriptById(scriptId)
                    ?: throw IllegalStateException("Script not found")
                writeContent(context, uri, script.code)
                _toastEvents.emit("Exported ${script.name}")
            }.onFailure { e ->
                _toastEvents.emit("Failed to export script: ${e.message ?: "Unknown error"}")
            }
        }
    }

    fun exportScript(context: Context, script: AutomationScript, uri: Uri) {
        exportScriptById(context, script.id, uri)
    }

    private suspend fun readContent(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).readText()
        } ?: ""
    }

    private suspend fun writeContent(context: Context, uri: Uri, content: String) = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(content)
                    writer.flush()
                }
            } ?: throw IllegalStateException("Failed to open output stream for writing")
        } catch (e: Exception) {
            android.util.Log.e("ExportScript", "Error writing file: ${e.message}", e)
            throw IllegalStateException("Failed to write file: ${e.message}", e)
        }
    }

    private suspend fun fetchRemoteScript(url: String): String = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 10000
            requestMethod = "GET"
            instanceFollowRedirects = true
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code")
            }
            connection.inputStream.use { stream ->
                stream.bufferedReader().readText()
            }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun markScriptRunning(scriptId: String, running: Boolean) {
        ScriptRepository.updateScriptRunningState(scriptId, running)
    }

    private suspend fun markScriptExecuted(scriptId: String) {
        ScriptRepository.updateScriptLastExecuted(scriptId, System.currentTimeMillis())
        ScriptRepository.updateScriptRunningState(scriptId, false)
    }

    suspend fun getScript(scriptId: String?): AutomationScript? =
        scriptId?.let { ScriptRepository.getScriptById(it) }

    suspend fun draftFrom(scriptId: String?): ScriptDraft {
        val script = getScript(scriptId)
        val code = script?.code ?: ScriptTemplates.blankTemplate()
        return ScriptDraft(
            id = script?.id,
            code = code,
            lastValidatedAt = script?.lastValidatedAt
        )
    }

    data class ScriptDraft(
        val id: String? = null,
        val code: String = "",
        val lastValidatedAt: Long? = null
    )

    private data class ParsedScriptMetadata(
        val metadata: ScriptMetadata,
        val tags: List<String>
    )

    private fun parseScriptMetadataOrThrow(code: String): ParsedScriptMetadata {
        if (!code.contains("droidRun(")) {
            throw IllegalArgumentException("Script must define droidRun(ctx)")
        }
        val metadata = ScriptMetadataParser.parse(code)
        val missing = metadata.missingRequired()
        if (missing.isNotEmpty()) {
            throw IllegalArgumentException("Missing metadata fields: ${missing.joinToString(", ")}")
        }
        val tags = metadata.tags
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.map { it.lowercase() }
            ?.distinct()
            ?: emptyList()
        return ParsedScriptMetadata(metadata, tags)
    }

    object ScriptValidator {
        private val keywordRegex = Regex("\\b(var|let|const|function|if|else|for|while|return|async|await|try|catch|break|continue|switch|case|default|new|class|extends|import|from|export|throw|typeof|instanceof)\\b")
        private val stringRegex = Regex("\"([^\"]|\\\\\")*\"|'([^']|\\\\')*'")

        fun validate(code: String): ScriptValidationResult {
            if (code.isBlank()) {
                return ScriptValidationResult(
                    isValid = false,
                    messages = listOf(
                        ScriptValidationMessage(
                            message = "Script body is empty",
                            level = LogLevel.ERROR
                        )
                    )
                )
            }

            val messages = mutableListOf<ScriptValidationMessage>()

            if (!code.contains("droidRun(")) {
                messages += ScriptValidationMessage(
                    message = "Script must export a 'droidRun(ctx)' function",
                    level = LogLevel.ERROR
                )
            }
            var balance = 0
            code.lines().forEachIndexed { idx, line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("//")) return@forEachIndexed
                for (char in trimmed) {
                    when (char) {
                        '{' -> balance++
                        '}' -> {
                            balance--
                            if (balance < 0) {
                                messages += ScriptValidationMessage(
                                    message = "Unmatched closing brace",
                                    level = LogLevel.ERROR,
                                    line = idx + 1
                                )
                                balance = 0
                            }
                        }
                    }
                }
            }
            if (balance != 0) {
                messages += ScriptValidationMessage(
                    message = "Braces appear to be unbalanced",
                    level = LogLevel.WARNING
                )
            }

            return ScriptValidationResult(
                isValid = messages.none { it.level == LogLevel.ERROR },
                messages = messages
            )
        }
    }
}
