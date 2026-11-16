/**
 * Copyright (c) 2025 tas33n
 *
 * Licensed under the MIT License
 * See LICENSE file or https://opensource.org/licenses/MIT
 *
 * @author tas33n
 * @see <a href="https://github.com/tas33n/droidwright">GitHub</a>
 */
package com.tas33n.droidwright.domain

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import app.cash.quickjs.QuickJs
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.tas33n.droidwright.data.models.AutomationScript
import com.tas33n.droidwright.data.models.LogEntry
import com.tas33n.droidwright.data.models.LogLevel
import com.tas33n.droidwright.data.models.ScriptResult
import com.tas33n.droidwright.data.repository.PermissionRepository
import com.tas33n.droidwright.domain.ScriptMetadataParser
import com.tas33n.droidwright.domain.api.createDroidWrightApi
import com.tas33n.droidwright.domain.api.installDroidWrightBindings
import com.tas33n.droidwright.service.AutomationAccessibilityService
import kotlin.coroutines.coroutineContext
import kotlin.jvm.Volatile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object UIAutomatorEngine {

    private const val TAG = "AutomationEngine"

    private val gson = Gson()

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs = _logs.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning = _isRunning.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused = _isPaused.asStateFlow()

    private val _currentTask = MutableStateFlow("")
    val currentTask = _currentTask.asStateFlow()

    @Volatile
    private var executionJob: Job? = null
    @Volatile
    private var isCancellationRequested = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
        AutomationAccessibilityService.enableTouchVisualization(true)
    }

    suspend fun executeScript(
        script: AutomationScript,
        intentPayload: Map<String, Any?>? = null
    ): ScriptResult {
        val context = appContext ?: return ScriptResult("error", "Engine not initialized")

        if (!PermissionRepository.hasAccessibility(context)) {
            log(LogLevel.ERROR, "Accessibility service is not enabled in system settings.")
            return ScriptResult("error", "Accessibility service not enabled. Please enable it in system settings.")
        }

        // Android may take time to connect the service after it's enabled.
        // Try to trigger connection by accessing the service info.
        // Wait up to 15 seconds (30 tries × 500ms) for the service to connect.
        var connectionTries = 0
        val maxTries = 30
        log(LogLevel.INFO, "Checking accessibility service connection...")
        
        // Try to trigger service connection by checking if service is enabled
        // Sometimes Android needs a small nudge to connect the service
        try {
            AutomationAccessibilityService.isAccessibilityServiceEnabled(context)
        } catch (e: Exception) {
            log(LogLevel.DEBUG, "Service check exception: ${e.message}")
        }
        
        while (!AutomationAccessibilityService.isConnected() && connectionTries < maxTries) {
            if (connectionTries % 4 == 0) { // Log every 2 seconds
                log(LogLevel.INFO, "Waiting for accessibility service to connect... (attempt ${connectionTries + 1}/$maxTries)")
            }
            delay(500)
            connectionTries++
        }

        if (!AutomationAccessibilityService.isConnected()) {
            log(LogLevel.ERROR, "Accessibility service failed to connect after ${maxTries * 500}ms.")
            log(LogLevel.ERROR, "Service is enabled but not connected. Try:")
            log(LogLevel.ERROR, "1. Disable and re-enable the accessibility service in system settings")
            log(LogLevel.ERROR, "2. Restart the app")
            log(LogLevel.ERROR, "3. Restart your device")
            return ScriptResult("error", "Accessibility service connection timeout. Service is enabled but not connected. Please try disabling and re-enabling the service in system settings, or restart the app.")
        }
        
        log(LogLevel.INFO, "Accessibility service connected successfully!")

        _isRunning.value = true
        _isPaused.value = false
        _currentTask.value = script.name
        isCancellationRequested = false
        log(LogLevel.INFO, "Starting automation script: ${script.name}")

        // Store current coroutine context job for cancellation
        val currentJob = coroutineContext[Job]
        executionJob = currentJob

        val result = try {
            val scriptResult = executeScriptInternal(script, context, intentPayload)
            scriptResult ?: ScriptResult("ok", "Script completed")
        } catch (e: kotlinx.coroutines.CancellationException) {
            log(LogLevel.INFO, "Script execution cancelled")
            ScriptResult("error", "Script cancelled")
        } catch (e: Exception) {
            e.printStackTrace()
            log(LogLevel.ERROR, "Script execution failed: ${e.message}")
            ScriptResult("error", e.message ?: "Unknown error")
        } finally {
            executionJob = null
            _isRunning.value = false
            _isPaused.value = false
            _currentTask.value = ""
        }
        
        log(LogLevel.INFO, "Automation finished: ${result.status}")
        return result
    }
    
    private suspend fun executeScriptInternal(
        script: AutomationScript,
        context: Context,
        intentPayload: Map<String, Any?>? = null
    ): ScriptResult? = withContext(Dispatchers.Default) {
        var quickJs: QuickJs? = null
        try {
            // Check for cancellation before starting
            coroutineContext.ensureActive()
            
            quickJs = QuickJs.create()

            val api = createDroidWrightApi(
                engine = this@UIAutomatorEngine,
                appContext = context
            )
            quickJs.installDroidWrightBindings(
                engine = this@UIAutomatorEngine,
                api = api,
                appContext = context,
                intentPayload = intentPayload
            )
            
            quickJs.set("__logBridge", QuickJsLogger::class.java, QuickJsLogger { message ->
                log(LogLevel.INFO, message ?: "null")
            })
            quickJs.evaluate(
                """
                (function (global) {
                  const bridge = global.__logBridge;
                  global.log = function (message) {
                    if (!bridge) {
                      return;
                    }
                    const text = message == null ? "" : String(message);
                    if (typeof bridge === "function") {
                      bridge(text);
                      return;
                    }
                    if (typeof bridge.log === "function") {
                      bridge.log(text);
                    }
                  };
                })(globalThis);
                """.trimIndent(),
                "log-bridge.js"
            )

            val sanitizedScript = preprocessScript(script.code)
            val metadataBlock = buildMetadataBootstrap(script.code)

            val scriptSource = buildString {
                if (metadataBlock.isNotBlank()) {
                    appendLine(metadataBlock)
                    appendLine()
                }
                appendLine(sanitizedScript)
                appendLine()
                append(
                    """
                    (function() {
                      let entryPoint = null;
                      if (typeof droidRun === 'function') {
                        entryPoint = droidRun;
                      } else if (typeof run === 'function') {
                        entryPoint = run;
                      }
                      if (entryPoint === null) {
                        throw new Error("Script must export a 'droidRun(ctx)' function");
                      }
                      const value = entryPoint(ctx);
                      if (value && typeof value.then === 'function') {
                        throw new Error("Async droidRun(ctx) is not supported yet.");
                      }
                      const result = value ?? { status: "ok" };
                      return typeof result === 'string' ? result : JSON.stringify(result);
                    })();
                    """.trimIndent()
                )
            }

            coroutineContext.ensureActive()
            val rawResult = quickJs.evaluate(scriptSource, script.name)
            val jsonPayload = rawResult?.toString()
            if (!jsonPayload.isNullOrBlank()) {
                log(LogLevel.INFO, "Script result payload: $jsonPayload")
            }
            parseScriptResult(jsonPayload)
        } catch (e: kotlinx.coroutines.CancellationException) {
            log(LogLevel.INFO, "Script execution cancelled")
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            log(LogLevel.ERROR, "Script execution failed: ${e.message}")
            throw e
        } finally {
            quickJs?.close()
        }
    }
    

    fun stopExecution() {
        log(LogLevel.INFO, "Stop execution requested")
        isCancellationRequested = true
        _isPaused.value = false // Resume first so cancellation can proceed
        executionJob?.cancel()
        _isRunning.value = false
        _currentTask.value = ""
        log(LogLevel.INFO, "Execution stopped")
    }

    fun pauseExecution() {
        if (_isRunning.value) {
            log(LogLevel.INFO, "Pause execution requested")
            _isPaused.value = true
        }
    }

    fun resumeExecution() {
        if (_isRunning.value && _isPaused.value) {
            log(LogLevel.INFO, "Resume execution requested")
            _isPaused.value = false
        }
    }

    fun isCancellationRequested(): Boolean {
        return isCancellationRequested
    }

    fun log(level: LogLevel, message: String) {
        val formatted = "[${level.name}] $message"
        when (level) {
            LogLevel.ERROR -> Log.e(TAG, formatted)
            LogLevel.WARNING -> Log.w(TAG, formatted)
            LogLevel.INFO -> Log.i(TAG, formatted)
            LogLevel.DEBUG -> Log.d(TAG, formatted)
            LogLevel.SUCCESS -> Log.i(TAG, formatted)
        }
        mainHandler.post {
            _logs.value = _logs.value + LogEntry(System.currentTimeMillis(), message, level)
        }
    }

    fun clearLogs() {
        mainHandler.post {
            _logs.value = emptyList()
        }
    }

    fun showToast(message: String) {
        val service = AutomationAccessibilityService.getInstance()
        if (service != null) {
            service.showToast(message)
            return
        }
        val context = appContext ?: return
        mainHandler.post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun preprocessScript(source: String): String {
        var result = source
        val exportDefaultFunctionRegex = Regex("""^(\s*)export\s+default\s+function\s+""", RegexOption.MULTILINE)
        val exportFunctionRegex = Regex("""^(\s*)export\s+(async\s+)?function\s+""", RegexOption.MULTILINE)
        val exportVariableRegex = Regex("""^(\s*)export\s+(const|let|var)\s+""", RegexOption.MULTILINE)

        result = exportDefaultFunctionRegex.replace(result) { match ->
            "${match.groupValues[1]}function "
        }
        result = exportFunctionRegex.replace(result) { match ->
            val asyncFragment = match.groupValues[2]
            val prefix = match.groupValues[1] + asyncFragment
            prefix + "function "
        }
        result = exportVariableRegex.replace(result) { match ->
            "${match.groupValues[1]}${match.groupValues[2]} "
        }
        return result
    }

    private fun buildMetadataBootstrap(source: String): String {
        val metadata = ScriptMetadataParser.parse(source)
        if (metadata.isEmpty()) {
            return ""
        }
        val jsValues = metadata.toJsValueMap()
        val declarations = StringBuilder()
        val usedNames = mutableSetOf<String>()
        jsValues.forEach { (key, value) ->
            val identifier = sanitizeIdentifier(key, usedNames)
            val valueJson = gson.toJson(value)
            declarations.append("const $identifier = $valueJson;\n")
        }
        val metadataJson = gson.toJson(jsValues)
        return buildString {
            appendLine("const __droidMetadata = $metadataJson;")
            appendLine("if (typeof globalThis !== 'undefined') { globalThis.__droidMetadata = __droidMetadata; }")
            append(declarations.toString())
        }.trim()
    }

    private fun sanitizeIdentifier(raw: String, usedNames: MutableSet<String>): String {
        val cleaned = raw.trim().replace(Regex("[^A-Za-z0-9_]"), " ")
        val camel = cleaned
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .mapIndexed { index, segment ->
                val lower = segment.lowercase()
                if (index == 0) lower else lower.replaceFirstChar { it.uppercaseChar() }
            }
            .joinToString("")
            .ifBlank { "meta" }
        val base = if (camel.first().isDigit()) "_$camel" else camel
        var candidate = base
        var counter = 1
        while (!usedNames.add(candidate)) {
            candidate = "${base}_${counter++}"
        }
        return candidate
    }

    private fun parseScriptResult(payload: String?): ScriptResult {
        if (payload.isNullOrBlank()) {
            return ScriptResult("ok")
        }
        return try {
            gson.fromJson(payload, ScriptResult::class.java)?.let { parsed ->
                val status = parsed.status.ifBlank { "ok" }
                ScriptResult(status, parsed.note)
            } ?: ScriptResult("ok", payload)
        } catch (jsonError: JsonSyntaxException) {
            log(LogLevel.WARNING, "Unable to parse script result JSON: ${jsonError.message}")
            ScriptResult("ok", payload)
        }
    }
}

fun interface QuickJsLogger {
    fun log(message: String?)
}
