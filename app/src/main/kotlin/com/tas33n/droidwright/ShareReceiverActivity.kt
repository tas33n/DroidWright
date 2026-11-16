/**
 * Copyright (c) 2026 wi99
 *
 * Licensed under the MIT License
 * See LICENSE file or https://opensource.org/licenses/MIT
 *
 * @author wi99
 * @see <a href="https://github.com/tas33n/droidwright">GitHub</a>
 */
package com.tas33n.droidwright

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.tas33n.droidwright.data.models.AutomationScript
import com.tas33n.droidwright.data.repository.ScriptRepository
import com.tas33n.droidwright.domain.ShareIntentPayload
import com.tas33n.droidwright.domain.ShareScriptMatcher
import com.tas33n.droidwright.domain.UIAutomatorEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Entry point for content received through the Android share sheet
 * (ACTION_SEND text, ACTION_SENDTO links). Looks up scripts whose
 * // @match / @include / @exclude headers match the shared content and runs
 * the selected script, exposing the triggering intent as ctx.intent.
 *
 * Runs outside the main app task, so sharing from another app doesn't pulls up DroidWright's UI, 
 * except for the chooser dialog when more than one script matches.
 */
class ShareReceiverActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        when (intent.action) {
            Intent.ACTION_SEND, Intent.ACTION_SENDTO, Intent.ACTION_VIEW -> handleShareIntent()
            else -> finish()
        }
    }

    private fun handleShareIntent() {
        val payload = ShareIntentPayload.fromIntent(intent)

        lifecycleScope.launch(Dispatchers.IO) {
            val candidates = runCatching { ScriptRepository.getAllScripts() }
                .getOrDefault(emptyList())
                .filter { ShareScriptMatcher.matches(payload, it.code) }

            withContext(Dispatchers.Main) {
                when {
                    candidates.isEmpty() -> {
                        showToast("No DroidWright script matches this share")
                        finish()
                    }
                    candidates.size == 1 -> runScript(candidates.first(), payload)
                    else -> chooseScript(candidates, payload)
                }
            }
        }
    }

    private fun chooseScript(candidates: List<AutomationScript>, payload: ShareIntentPayload) {
        val names = candidates.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Run which script?")
            .setItems(names) { _, which ->
                runScript(candidates[which], payload)
            }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun runScript(script: AutomationScript, payload: ShareIntentPayload) {
        val payloadMap = payload.toMap()

        // Engine runs are app-lifetime singletons, and this activity finishes
        // as soon as the share source app is restored - so run in a detached
        // scope, not the lifecycle one.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            if (UIAutomatorEngine.isRunning.value) {
                showToast("DroidWright is already running a script")
                return@launch
            }

            ScriptRepository.updateScriptRunningState(script.id, true)

            val result = runCatching { UIAutomatorEngine.executeScript(script, payloadMap) }
                .getOrDefault(
                    com.tas33n.droidwright.data.models.ScriptResult(
                        "error",
                        "Unexpected error while running shared script"
                    )
                )

            ScriptRepository.updateScriptRunningState(script.id, false)
            if (result.status == "ok") {
                ScriptRepository.updateScriptLastExecuted(script.id, System.currentTimeMillis())
            }
            showToast("${script.name} ${result.status}: ${result.note ?: ""}".trim())

            finish()
        }
    }

    private fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
}
