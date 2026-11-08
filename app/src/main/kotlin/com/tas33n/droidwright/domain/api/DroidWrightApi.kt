/**
 * Copyright (c) 2025 tas33n
 *
 * Licensed under the MIT License
 * See LICENSE file or https://opensource.org/licenses/MIT
 *
 * @author tas33n
 * @see <a href="https://github.com/tas33n/droidwright">GitHub</a>
 */
package com.tas33n.droidwright.domain.api

import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.DisplayMetrics
import app.cash.quickjs.QuickJs
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tas33n.droidwright.data.models.LogLevel
import com.tas33n.droidwright.domain.ResourceMonitor
import com.tas33n.droidwright.domain.Selector
import com.tas33n.droidwright.domain.SelectorParser
import com.tas33n.droidwright.domain.UIAutomatorEngine
import com.tas33n.droidwright.service.AutomationAccessibilityService
import com.tas33n.droidwright.util.release
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient 
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

interface DroidWrightApi {
    val device: DeviceApi
    val app: AppApi
    val ui: UiApi
    val network: NetworkApi
    val storage: StorageApi
}

interface DeviceApi {
    fun press(key: String): Boolean
    fun getClipboard(): String
    fun setClipboard(text: String)
    fun getScreenSize(): Map<String, Int>
    fun sleep(ms: Long)
    fun showToast(message: String)
}

interface AppApi {
    fun launch(packageName: String): Boolean
    fun close(packageName: String): Boolean
    fun getPackageName(): String?
    fun openUri(uri: String): Boolean
    fun launchActivity(options: Map<String, Any?>): Boolean
}

interface UiApi {
    fun tap(selector: Map<String, String>): Boolean
    fun longTap(selector: Map<String, String>): Boolean
    fun setText(selector: Map<String, String>, text: String): Boolean
    fun scroll(selector: Map<String, String>, direction: String): Boolean
    fun swipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long): Boolean
    fun find(selector: Map<String, String>): Map<String, Any?>?
    fun findAll(selector: Map<String, String>): List<Map<String, Any?>>
    fun exists(selector: Map<String, String>): Boolean
    fun waitFor(selector: Map<String, String>, timeoutMs: Long, maxScrolls: Int = 5, scrollContainer: Map<String, String>? = null): Boolean
    fun waitForIdle(timeoutMs: Long): Boolean
    fun dumpTree(maxDepth: Int = Int.MAX_VALUE): List<Map<String, Any?>>
}

interface NetworkApi {
    fun fetch(url: String, options: Map<String, Any?> = emptyMap()): String
}

interface StorageApi {
    fun put(key: String, value: String)
    fun get(key: String): String?
    fun listKeys(): List<String>
}

interface DroidWrightBridge {
    fun invoke(action: String, payloadJson: String?): String?
}

fun createDroidWrightApi(
    engine: UIAutomatorEngine,
    appContext: Context
): DroidWrightApi {
    val clipboardManager = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val httpClient = OkHttpClient()
    val sharedPrefs = appContext.getSharedPreferences("droidwright_storage", Context.MODE_PRIVATE)

    fun buildSelector(selector: Map<String, String>) = Selector(
        // Support multiple keys for resource ID: "id", "resourceId", "resource-id"
        id = selector["id"] ?: selector["resourceId"] ?: selector["resource-id"],
        // Support text matching
        text = selector["text"],
        // Support content-desc/accessibility ID: "desc", "contentDesc", "content-desc", "accessibilityId", "accessibility-id"
        desc = selector["desc"] ?: selector["contentDesc"] ?: selector["content-desc"] 
            ?: selector["accessibilityId"] ?: selector["accessibility-id"],
        // Support class name: "className", "class", "class-name"
        className = selector["className"] ?: selector["class"] ?: selector["class-name"],
        // Support attribute filters (Playwright/Puppeteer style)
        checked = selector["checked"]?.toBooleanStrictOrNull(),
        selected = selector["selected"]?.toBooleanStrictOrNull(),
        enabled = selector["enabled"]?.toBooleanStrictOrNull(),
        clickable = selector["clickable"]?.toBooleanStrictOrNull(),
        focused = selector["focused"]?.toBooleanStrictOrNull(),
        visible = selector["visible"]?.toBooleanStrictOrNull(),
        packageName = selector["package"] ?: selector["packageName"]
    )
    
    /**
     * Builds selector from string (supports UiAutomator and XPath formats)
     */
    fun buildSelectorFromString(selectorString: String): Selector {
        val parsed = SelectorParser.parseSelector(selectorString)
        return buildSelector(parsed)
    }

    return object : DroidWrightApi {
        override val device = object : DeviceApi {
            override fun press(key: String): Boolean {
                val normalized = key.uppercase()
                engine.log(LogLevel.DEBUG, "device.press($normalized)")
                val instance = AutomationAccessibilityService.getInstance()
                return when (normalized) {
                    "BACK" -> instance?.pressBack() ?: false
                    "HOME" -> instance?.pressHome() ?: false
                    else -> false
                }
            }

            override fun getClipboard(): String {
                val clip = clipboardManager.primaryClip
                return if (clip != null && clip.itemCount > 0) {
                    clip.getItemAt(0).coerceToText(appContext).toString()
                } else {
                    ""
                }
            }

            override fun setClipboard(text: String) {
                clipboardManager.setPrimaryClip(android.content.ClipData.newPlainText("droidwright", text))
            }

            override fun getScreenSize(): Map<String, Int> {
                val metrics: DisplayMetrics = appContext.resources.displayMetrics
                return mapOf(
                    "width" to metrics.widthPixels,
                    "height" to metrics.heightPixels
                )
            }

            override fun sleep(ms: Long) {
                // Enforce minimum sleep duration for safety (prevent too-short sleeps that cause ANR)
                val actualSleep = maxOf(ms, if (ms > 0) 100L else 0L) // Minimum 100ms for any non-zero sleep
                
                // Use non-blocking sleep that checks for cancellation and pause
                // Sleep in small chunks to allow cancellation and pause checks
                val chunkSize = 100L // Sleep in 100ms chunks
                val maxPressureExtension = 3000L // Cap extra wait added due to pressure
                var remaining = actualSleep
                var pressureExtensionBudget = maxPressureExtension
                var lastPressureLogTs = 0L
                
                while (remaining > 0 && !engine.isCancellationRequested()) {
                    val now = SystemClock.elapsedRealtime()
                    
                    // Check if system is under pressure - extend sleep if needed (with cap)
                    if (pressureExtensionBudget > 0 && ResourceMonitor.isSystemUnderPressure(appContext)) {
                        val extension = minOf(1000L, pressureExtensionBudget)
                        remaining += extension
                        pressureExtensionBudget -= extension
                        
                        if (now - lastPressureLogTs > 2000L) {
                            engine.log(
                                LogLevel.WARNING,
                                "System under pressure during sleep, extending delay by ${extension}ms (budget left: ${pressureExtensionBudget}ms)"
                            )
                            lastPressureLogTs = now
                        }
                    }
                    
                    // Check if paused
                    while (engine.isPaused.value && !engine.isCancellationRequested()) {
                        Thread.sleep(50) // Small delay while paused
                    }
                    
                    if (engine.isCancellationRequested()) {
                        break
                    }
                    
                    val sleepTime = minOf(chunkSize, remaining)
                    Thread.sleep(sleepTime)
                    remaining -= sleepTime
                }
            }

            override fun showToast(message: String) {
                engine.showToast(message)
            }
        }

        override val app = object : AppApi {
            override fun launch(packageName: String): Boolean {
                engine.log(LogLevel.INFO, "app.launch($packageName)")
                return AutomationAccessibilityService.getInstance()?.launchApp(packageName) ?: false
            }

            override fun close(packageName: String): Boolean {
                engine.log(LogLevel.INFO, "app.close($packageName)")
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                    activityManager.killBackgroundProcesses(packageName)
                    true
                } else {
                    false
                }
            }

            override fun getPackageName(): String? {
                return AutomationAccessibilityService.getInstance()?.rootInActiveWindow?.packageName?.toString()
            }

            override fun openUri(uri: String): Boolean {
                engine.log(LogLevel.INFO, "app.openUri($uri)")
                return launchActivity(mapOf("action" to Intent.ACTION_VIEW, "uri" to uri))
            }

            override fun launchActivity(options: Map<String, Any?>): Boolean {
                engine.log(LogLevel.INFO, "app.launchActivity($options)")
                val service = AutomationAccessibilityService.getInstance() ?: return false
                return service.launchActivity(buildIntent(options))
            }
        }

        override val ui = object : UiApi {
            override fun tap(selector: Map<String, String>): Boolean {
                engine.log(LogLevel.DEBUG, "ui.tap($selector)")
                
                // Check for cancellation before starting
                if (engine.isCancellationRequested()) {
                    throw IllegalStateException("Script execution cancelled")
                }
                
                val service = AutomationAccessibilityService.getInstance() ?: return false
                
                // Wait for UI to be idle before tap (with cancellation check)
                if (!safeWaitForIdle(1000, engine)) {
                    return false
                }
                
                // Check for cancellation before performing tap
                if (engine.isCancellationRequested()) {
                    throw IllegalStateException("Script execution cancelled")
                }
                
                val result = service.click(buildSelector(selector))
                
                if (result) {
                    // Wait after tap for UI to respond
                    if (!safeSleep(ResourceMonitor.Delays.AFTER_TAP, engine)) {
                        return false
                    }
                }
                
                return result
            }

            override fun longTap(selector: Map<String, String>): Boolean {
                engine.log(LogLevel.DEBUG, "ui.longTap($selector)")
                return AutomationAccessibilityService.getInstance()?.longClick(buildSelector(selector)) ?: false
            }

            override fun setText(selector: Map<String, String>, text: String): Boolean {
                engine.log(LogLevel.DEBUG, "ui.setText($selector, $text)")
                return AutomationAccessibilityService.getInstance()?.setText(buildSelector(selector), text) ?: false
            }

            override fun scroll(selector: Map<String, String>, direction: String): Boolean {
                engine.log(LogLevel.DEBUG, "ui.scroll($selector, $direction)")
                
                // Check for cancellation before starting
                if (engine.isCancellationRequested()) {
                    throw IllegalStateException("Script execution cancelled")
                }
                
                val service = AutomationAccessibilityService.getInstance() ?: return false
                
                // Wait for UI to be idle before scroll (with cancellation check)
                if (!safeWaitForIdle(1000, engine)) {
                    return false
                }
                
                // Check for cancellation before performing scroll
                if (engine.isCancellationRequested()) {
                    throw IllegalStateException("Script execution cancelled")
                }
                
                val result = service.scroll(buildSelector(selector), direction)
                
                if (result) {
                    // Wait after scroll for content to load
                    if (!safeSleep(ResourceMonitor.Delays.AFTER_SCROLL, engine)) {
                        return false
                    }
                    
                    // Wait for UI to settle (with cancellation check)
                    if (!safeWaitForIdle(1000, engine)) { // Reduced timeout
                        return false
                    }
                }
                
                return result
            }

            override fun swipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long): Boolean {
                engine.log(LogLevel.DEBUG, "ui.swipe($startX,$startY -> $endX,$endY duration=$durationMs)")
                
                // Check for cancellation before starting
                if (engine.isCancellationRequested()) {
                    throw IllegalStateException("Script execution cancelled")
                }
                
                val service = AutomationAccessibilityService.getInstance() ?: return false
                
                // Check system resources before performing swipe
                if (ResourceMonitor.isSystemUnderPressure(appContext)) {
                    engine.log(LogLevel.WARNING, "System under memory pressure, pausing before swipe")
                    if (!safeSleep(ResourceMonitor.Delays.ON_ERROR, engine)) {
                        return false
                    }
                }
                
                // Check if app is responsive with retry limit
                var retryCount = 0
                val maxRetries = 2
                while (!ResourceMonitor.isAppResponsive() && retryCount < maxRetries) {
                    engine.log(LogLevel.WARNING, "App not responsive, waiting before swipe (attempt ${retryCount + 1}/$maxRetries)")
                    if (!safeSleep(1000, engine)) { // Reduced wait time
                        return false
                    }
                    retryCount++
                }
                
                // If still not responsive after retries, skip this swipe to prevent freeze
                if (!ResourceMonitor.isAppResponsive()) {
                    engine.log(LogLevel.WARNING, "App still not responsive after $maxRetries attempts, skipping swipe to prevent freeze")
                    return false
                }
                
                // Wait for UI to be idle before swipe (with cancellation check)
                if (!safeWaitForIdle(1000, engine)) { // Reduced timeout
                    return false
                }
                
                // Check for cancellation before performing swipe
                if (engine.isCancellationRequested()) {
                    throw IllegalStateException("Script execution cancelled")
                }
                
                // Perform swipe
                val result = service.swipe(startX, startY, endX, endY, durationMs)
                
                if (result) {
                    // Wait for swipe animation to complete
                    if (!safeSleep(500, engine)) {
                        return false
                    }
                    
                    // Wait for UI to settle after swipe (with cancellation check)
                    if (!safeWaitForIdle(1000, engine)) { // Reduced timeout
                        return false
                    }
                    
                    // Additional delay to let content load (enforced minimum)
                    if (!safeSleep(ResourceMonitor.Delays.AFTER_SWIPE, engine)) {
                        return false
                    }
                } else {
                    // On failure, wait longer before retry
                    engine.log(LogLevel.WARNING, "Swipe failed, waiting before potential retry")
                    if (!safeSleep(ResourceMonitor.Delays.ON_ERROR, engine)) {
                        return false
                    }
                }
                
                return result
            }
            
            // Helper function for safe sleep with cancellation checks
            private fun safeSleep(durationMs: Long, engine: UIAutomatorEngine): Boolean {
                val checkInterval = 200L // Check every 200ms
                var remaining = durationMs
                
                while (remaining > 0) {
                    if (engine.isCancellationRequested()) {
                        return false
                    }
                    
                    val sleepTime = minOf(checkInterval, remaining)
                    try {
                        Thread.sleep(sleepTime)
                    } catch (e: InterruptedException) {
                        return false
                    }
                    remaining -= sleepTime
                }
                return true
            }
            
            // Helper function for safe waitForIdle with cancellation checks
            private fun safeWaitForIdle(timeoutMs: Long, engine: UIAutomatorEngine): Boolean {
                val checkInterval = 200L // Check every 200ms
                var remaining = timeoutMs
                
                while (remaining > 0) {
                    if (engine.isCancellationRequested()) {
                        return false
                    }
                    
                    // Check if app became responsive
                    if (ResourceMonitor.isAppResponsive()) {
                        // App is responsive, can proceed
                        return true
                    }
                    
                    val sleepTime = minOf(checkInterval, remaining)
                    try {
                        Thread.sleep(sleepTime)
                    } catch (e: InterruptedException) {
                        return false
                    }
                    remaining -= sleepTime
                }
                
                // Timeout reached, check one more time
                return ResourceMonitor.isAppResponsive()
            }

            override fun find(selector: Map<String, String>): Map<String, Any?>? {
                val svc = AutomationAccessibilityService.getInstance() ?: return null
                val node = svc.findNode(buildSelector(selector)) ?: return null
                val data = svc.asNodeMap(node)
                node.release()
                return data
            }

            override fun findAll(selector: Map<String, String>): List<Map<String, Any?>> {
                val svc = AutomationAccessibilityService.getInstance() ?: return emptyList()
                val nodes = svc.findAll(buildSelector(selector))
                return nodes.map { node ->
                    svc.asNodeMap(node).also { node.release() }
                }
            }

            override fun exists(selector: Map<String, String>): Boolean {
                val svc = AutomationAccessibilityService.getInstance() ?: return false
                val node = svc.findNode(buildSelector(selector)) ?: return false
                node.release()
                return true
            }

            override fun waitFor(selector: Map<String, String>, timeoutMs: Long, maxScrolls: Int, scrollContainer: Map<String, String>?): Boolean {
                engine.log(LogLevel.DEBUG, "ui.waitFor($selector, timeout=$timeoutMs, maxScrolls=$maxScrolls, scrollContainer=$scrollContainer)")
                val svc = AutomationAccessibilityService.getInstance() ?: return false
                val containerSelector = scrollContainer?.let { buildSelector(it) }
                return svc.waitFor(buildSelector(selector), timeoutMs, maxScrolls = maxScrolls, scrollContainer = containerSelector)
            }

            override fun waitForIdle(timeoutMs: Long): Boolean {
                engine.log(LogLevel.DEBUG, "ui.waitForIdle($timeoutMs)")
                val svc = AutomationAccessibilityService.getInstance() ?: return false
                return svc.waitForIdle(timeoutMs)
            }

            override fun dumpTree(maxDepth: Int): List<Map<String, Any?>> {
                val svc = AutomationAccessibilityService.getInstance() ?: return emptyList()
                return svc.dumpTree(maxDepth)
            }
        }

        override val network = object : NetworkApi {
            override fun fetch(url: String, options: Map<String, Any?>): String {
                engine.log(LogLevel.INFO, "network.fetch($url)")
                return runBlocking {
                    try {
                        val method = (options["method"] as? String)?.uppercase() ?: "GET"
                        val body = options["body"] as? String
                        @Suppress("UNCHECKED_CAST")
                        val headersMap = options["headers"] as? Map<String, String> ?: emptyMap()

                        val requestBuilder = Request.Builder().url(url)
                        headersMap.forEach { (key, value) -> requestBuilder.addHeader(key, value) }

                        when {
                            method == "POST" && body != null -> {
                                val mediaType = headersMap["Content-Type"]?.toMediaType() ?: "application/json".toMediaType()
                                requestBuilder.post(body.toRequestBody(mediaType))
                            }

                            method == "PUT" && body != null -> {
                                val mediaType = headersMap["Content-Type"]?.toMediaType() ?: "application/json".toMediaType()
                                requestBuilder.put(body.toRequestBody(mediaType))
                            }

                            method == "DELETE" && body != null -> {
                                val mediaType = headersMap["Content-Type"]?.toMediaType() ?: "application/json".toMediaType()
                                requestBuilder.delete(body.toRequestBody(mediaType))
                            }

                            method == "DELETE" -> requestBuilder.delete()
                            method == "GET" -> requestBuilder.get()
                            else -> requestBuilder.method(method, null)
                        }

                        httpClient.newCall(requestBuilder.build()).execute().use { response ->
                            response.body?.string() ?: throw IOException("Empty response body")
                        }
                    } catch (e: Exception) {
                        engine.log(LogLevel.ERROR, "Fetch failed: ${e.message}")
                        val safeMessage = e.message?.replace("\"", "\\\"") ?: "unknown error"
                        """{"error":"$safeMessage"}"""
                    }
                }
            }
        }

        override val storage = object : StorageApi {
            override fun put(key: String, value: String) {
                sharedPrefs.edit().putString(key, value).apply()
            }

            override fun get(key: String): String? = sharedPrefs.getString(key, null)

            override fun listKeys(): List<String> = sharedPrefs.all.keys.toList()
        }
    }
}

private data class BridgeResponse(
    val ok: Boolean,
    val result: Any? = null,
    val error: String? = null
)

fun QuickJs.installDroidWrightBindings(
    engine: UIAutomatorEngine,
    api: DroidWrightApi,
    appContext: Context
) {
    val gson = Gson()
    val payloadType = object : TypeToken<Map<String, Any?>>() {}.type

    val bridge = object : DroidWrightBridge {
        override fun invoke(action: String, payloadJson: String?): String? {
            // Check if execution is cancelled
            if (engine.isCancellationRequested()) {
                throw IllegalStateException("Script execution cancelled")
            }
            
            // Check if paused - wait until resumed
            while (engine.isPaused.value) {
                if (engine.isCancellationRequested()) {
                    throw IllegalStateException("Script execution cancelled")
                }
                Thread.sleep(100)
            }
            
            // Check system resources before executing action
            if (ResourceMonitor.isSystemUnderPressure(appContext)) {
                engine.log(LogLevel.WARNING, "System under memory pressure, pausing before action: $action")
                try {
                    Thread.sleep(ResourceMonitor.Delays.ON_ERROR)
                } catch (e: InterruptedException) {
                    throw IllegalStateException("Script execution cancelled")
                }
            }
            
            val payload = parsePayload(gson, payloadType, payloadJson)
            return try {
                val result: Any? = when (action) {
                    "device.press" -> api.device.press(payload.requireString("key"))
                    "device.getClipboard" -> api.device.getClipboard()
                    "device.setClipboard" -> {
                        api.device.setClipboard(payload.requireString("text"))
                        null
                    }
                    "device.getScreenSize" -> api.device.getScreenSize()
                    "device.sleep" -> {
                        val ms = payload.requireNumber("ms").toLong()
                        // Only log if sleep is longer than 1 second to reduce log spam
                        if (ms > 1000) {
                            engine.log(LogLevel.DEBUG, "device.sleep($ms)")
                        }
                        api.device.sleep(ms)
                        null
                    }
                    "device.showToast" -> {
                        api.device.showToast(payload.requireString("message"))
                        null
                    }

                    "app.launch" -> api.app.launch(payload.requireString("packageName"))
                    "app.close" -> api.app.close(payload.requireString("packageName"))
                    "app.getPackageName" -> api.app.getPackageName()
                    "app.openUri" -> api.app.openUri(payload.requireString("uri"))
                    "app.launchActivity" -> api.app.launchActivity(payload.getMap("options"))

                    "ui.tap" -> api.ui.tap(payload.requireSelector())
                    "ui.longTap" -> api.ui.longTap(payload.requireSelector())
                    "ui.setText" -> api.ui.setText(payload.requireSelector(), payload.requireString("text"))
                    "ui.scroll" -> api.ui.scroll(
                        selector = payload.requireSelector(),
                        direction = payload.optionalString("direction") ?: "down"
                    )
                    "ui.swipe" -> api.ui.swipe(
                        startX = payload.requireNumber("startX").toFloat(),
                        startY = payload.requireNumber("startY").toFloat(),
                        endX = payload.requireNumber("endX").toFloat(),
                        endY = payload.requireNumber("endY").toFloat(),
                        durationMs = payload.requireNumber("durationMs").toLong()
                    )
                    "ui.find" -> api.ui.find(payload.requireSelector())
                    "ui.findAll" -> api.ui.findAll(payload.requireSelector())
                    "ui.exists" -> api.ui.exists(payload.requireSelector())
                    "ui.waitFor" -> api.ui.waitFor(
                        selector = payload.requireSelector(),
                        timeoutMs = payload.requireNumber("timeoutMs").toLong(),
                        maxScrolls = payload.optionalNumber("maxScrolls")?.toInt() ?: 5,
                        scrollContainer = payload.optionalSelector("scrollContainer")
                    )
                    "ui.waitForIdle" -> api.ui.waitForIdle(payload.requireNumber("timeoutMs").toLong())
                    "ui.dumpTree" -> api.ui.dumpTree(payload.optionalNumber("maxDepth")?.toInt() ?: Int.MAX_VALUE)

                    "network.fetch" -> api.network.fetch(
                        url = payload.requireString("url"),
                        options = payload.getMap("options")
                    )

                    "storage.put" -> {
                        api.storage.put(payload.requireString("key"), payload.requireString("value"))
                        null
                    }
                    "storage.get" -> api.storage.get(payload.requireString("key"))
                    "storage.listKeys" -> api.storage.listKeys()
                    else -> throw IllegalArgumentException("Unknown action: $action")
                }

                gson.toJson(BridgeResponse(ok = true, result = result))
            } catch (e: Exception) {
                engine.log(LogLevel.ERROR, "Action $action failed: ${e.message}")
                gson.toJson(BridgeResponse(ok = false, error = e.message ?: "unknown error"))
            }
        }
    }

    set("droid", DroidWrightBridge::class.java, bridge)
    evaluate(buildBootstrapScript(), "droidwright-bootstrap.js")
}

private fun parsePayload(
    gson: Gson,
    type: java.lang.reflect.Type,
    payloadJson: String?
): Map<String, Any?> {
    if (payloadJson.isNullOrBlank()) return emptyMap()
    return gson.fromJson(payloadJson, type)
}

private fun Map<String, Any?>.requireString(key: String): String {
    val value = this[key] ?: throw IllegalArgumentException("Missing required '$key'")
    if (value !is String) throw IllegalArgumentException("Expected '$key' to be a string")
    return value
}

private fun Map<String, Any?>.requireNumber(key: String): Double {
    val value = this[key] ?: throw IllegalArgumentException("Missing required '$key'")
    return when (value) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull() ?: throw IllegalArgumentException("Expected '$key' to be numeric")
        else -> throw IllegalArgumentException("Expected '$key' to be numeric")
    }
}

private fun Map<String, Any?>.optionalNumber(key: String): Double? {
    val value = this[key] ?: return null
    return when (value) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else -> null
    }
}

private fun Map<String, Any?>.requireSelector(): Map<String, String> {
    val raw = this["selector"] ?: return emptyMap()
    
    // Handle string selectors (UiAutomator/XPath)
    if (raw is String) {
        return SelectorParser.parseSelector(raw)
    }
    
    // Handle object selectors
    if (raw !is Map<*, *>) throw IllegalArgumentException("Expected selector to be an object or string")
    return raw.entries.mapNotNull { (key, value) ->
        if (key is String) {
            when (value) {
                is String -> key to value
                is Boolean -> key to value.toString()
                is Number -> key to value.toString()
                else -> null
            }
        } else null
    }.toMap()
}

private fun Map<String, Any?>.optionalSelector(key: String): Map<String, String>? {
    val raw = this[key] ?: return null
    
    // Handle string selectors (UiAutomator/XPath)
    if (raw is String) {
        return SelectorParser.parseSelector(raw)
    }
    
    // Handle object selectors
    if (raw !is Map<*, *>) throw IllegalArgumentException("Expected '$key' to be an object or string")
    return raw.entries.mapNotNull { (subKey, subValue) ->
        if (subKey is String) {
            when (subValue) {
                is String -> subKey to subValue
                is Boolean -> subKey to subValue.toString()
                is Number -> subKey to subValue.toString()
                else -> null
            }
        } else null
    }.toMap()
}

private fun Map<String, Any?>.optionalString(key: String): String? {
    val value = this[key] ?: return null
    return when (value) {
        is String -> value
        is Number -> value.toString()
        else -> null
    }
}

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.getMap(key: String): Map<String, Any?> {
    val raw = this[key] ?: return emptyMap()
    if (raw is Map<*, *>) {
        return raw as Map<String, Any?>
    }
    throw IllegalArgumentException("Expected '$key' to be an object")
}

private fun buildIntent(options: Map<String, Any?>): Intent = Intent().apply {
    val action = options["action"] as? String
    val uriStr = (options["uri"] ?: options["data"]) as? String
    val type = options["type"] as? String
    val packageName = options["package"] as? String
    val component = options["component"] as? String

    if (action != null) {
        setAction(action)
    }

    val uri = uriStr?.let { Uri.parse(it) }
    if (uri != null && type != null) {
        setDataAndType(uri, type)
    } else if (uri != null) {
        data = uri
    } else if (type != null) {
        setType(type)
    }

    if (packageName != null) {
        setPackage(packageName)
    }
    if (component != null) {
        ComponentName.unflattenFromString(component)?.let {
            setComponent(it)
        }
    }

    (options["categories"] as? List<*>)?.filterIsInstance<String>()?.forEach {
        addCategory(it)
    }

    val extras = options["extras"] as? Map<*, *>
    extras?.forEach { (key, value) ->
        val k = key.toString()
        when (value) {
            is String -> putExtra(k, value)
            is Boolean -> putExtra(k, value)
            is Int -> putExtra(k, value)
            is Long -> putExtra(k, value)
            is Float -> putExtra(k, value)
            is Double -> {
                if (value % 1.0 == 0.0 && value in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()) {
                    putExtra(k, value.toInt())
                } else {
                    putExtra(k, value)
                }
            }
            else -> if (value != null) putExtra(k, value.toString())
        }
    }
}

private fun buildBootstrapScript(): String {
    return """
        (function (global) {
          const __call = (action, payload) => {
            const encoded = payload === undefined ? null : JSON.stringify(payload);
            const raw = global.droid.invoke(action, encoded);
            if (!raw) {
              return undefined;
            }
            const response = JSON.parse(raw);
            if (!response.ok) {
              throw new Error(response.error || `Action ${'$'}{action} failed`);
            }
            return response.result;
          };

          const ctx = {
            app: {
              launch: packageName => __call("app.launch", { packageName }),
              close: packageName => __call("app.close", { packageName }),
              getPackageName: () => __call("app.getPackageName"),
              openUri: uri => __call("app.openUri", { uri }),
              launchActivity: options => __call("app.launchActivity", { options: options || {} })
            },
            device: {
              press: key => __call("device.press", { key }),
              getClipboard: () => __call("device.getClipboard"),
              setClipboard: text => __call("device.setClipboard", { text }),
              getScreenSize: () => __call("device.getScreenSize"),
              sleep: ms => __call("device.sleep", { ms }),
              showToast: message => __call("device.showToast", { message })
            },
            ui: {
              // Selectors can be objects { id: "..." } or strings (UiAutomator/XPath)
              tap: selector => __call("ui.tap", { selector }),
              longTap: selector => __call("ui.longTap", { selector }),
              setText: (selector, text) => __call("ui.setText", { selector, text }),
              scroll: (selector, direction) => __call("ui.scroll", { selector, direction }),
              swipe: (startX, startY, endX, endY, durationMs) =>
                __call("ui.swipe", { startX, startY, endX, endY, durationMs }),
              find: selector => __call("ui.find", { selector }),
              findAll: selector => __call("ui.findAll", { selector }),
              exists: selector => __call("ui.exists", { selector }),
              waitFor: (selector, timeoutMs, maxScrolls, scrollContainer) =>
                __call("ui.waitFor", { selector, timeoutMs, maxScrolls, scrollContainer }),
              waitForIdle: timeoutMs => __call("ui.waitForIdle", { timeoutMs }),
              dumpTree: maxDepth => __call("ui.dumpTree", { maxDepth })
            },
            network: {
              fetch: (url, options) => __call("network.fetch", { url, options: options || {} })
            },
            storage: {
              put: (key, value) => __call("storage.put", { key, value }),
              get: key => __call("storage.get", { key }),
              listKeys: () => __call("storage.listKeys", {})
            }
          };

          global.ctx = ctx;
          global.app = ctx.app;
          global.device = ctx.device;
          global.ui = ctx.ui;
          global.network = ctx.network;
          global.storage = ctx.storage;
        })(globalThis);
    """.trimIndent()
}
