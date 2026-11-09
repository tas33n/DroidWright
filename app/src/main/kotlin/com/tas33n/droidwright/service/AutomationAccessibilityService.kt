/**
 * Copyright (c) 2025 tas33n
 *
 * Licensed under the MIT License
 * See LICENSE file or https://opensource.org/licenses/MIT
 *
 * @author tas33n
 * @see <a href="https://github.com/tas33n/droidwright">GitHub</a>
 */
package com.tas33n.droidwright.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import com.tas33n.droidwright.R
import com.tas33n.droidwright.domain.Selector
import com.tas33n.droidwright.domain.toPredicate
import com.tas33n.droidwright.util.release

class AutomationAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AutomationService"
        @Volatile
        private var instance: AutomationAccessibilityService? = null
        
        // Rate limiting for swipe operations
        @Volatile
        private var lastSwipeTime = 0L
        private const val MIN_SWIPE_INTERVAL_MS = 3000L // Minimum 3 seconds between swipes

        fun isConnected(): Boolean = instance != null

        fun getInstance(): AutomationAccessibilityService? = instance

        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            val service = context.packageName + "/" + AutomationAccessibilityService::class.java.canonicalName
            val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            if (enabledServices != null) {
                colonSplitter.setString(enabledServices)
                while (colonSplitter.hasNext()) {
                    val componentName = colonSplitter.next()
                    if (componentName.equals(service, ignoreCase = true)) {
                        return true
                    }
                }
            }
            return false
        }

        fun enableTouchVisualization(enabled: Boolean) {
            instance?.setTouchVisualization(enabled)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility service connected!")
        instance = this
        
        // Ensure service info is properly configured
        val info = AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_WINDOWS_CHANGED or 
                          AccessibilityEvent.TYPE_VIEW_SCROLLED or
                          AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                          AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                     AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                     AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.notificationTimeout = 100
        setServiceInfo(info)
        
        Log.d(TAG, "Service info updated - eventTypes: ${info.eventTypes}, flags: ${info.flags}")
        Log.d(TAG, "Service can retrieve window content: true")
    }

    fun findNode(selector: Selector): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return findNodeRecursive(root, selector.toPredicate())
    }

    fun findAll(selector: Selector): List<AccessibilityNodeInfo> {
        val root = rootInActiveWindow ?: return emptyList()
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        findAllRecursive(root, selector.toPredicate(), nodes)
        return nodes
    }

    fun click(selector: Selector): Boolean {
        val node = findNode(selector) ?: return false
        var tapPoint: Pair<Float, Float>? = null
        Log.d(TAG, "Attempting tap for selector=$selector")
        val result = node.useForAction {
            val bounds = Rect()
            getBoundsInScreen(bounds)
            if (!bounds.isEmpty) {
                tapPoint = bounds.centerX().toFloat() to bounds.centerY().toFloat()
            }
            val actionPerformed = performActionUpwards(AccessibilityNodeInfo.ACTION_CLICK)
            if (!actionPerformed) {
                Log.d(TAG, "ACTION_CLICK failed, attempting gesture selector=$selector bounds=$bounds")
            }
            actionPerformed || tapPoint?.let { performTapGesture(it.first, it.second) } ?: false
        }
        if (result) {
            Log.d(TAG, "Tap succeeded selector=$selector point=$tapPoint")
            tapPoint?.let { showTouchIndicatorAt(it.first, it.second) }
        } else {
            Log.w(TAG, "Tap failed selector=$selector")
        }
        return result
    }

    fun longClick(selector: Selector): Boolean {
        val node = findNode(selector) ?: return false
        return node.useForAction { performActionUpwards(AccessibilityNodeInfo.ACTION_LONG_CLICK) }
    }

    fun setText(selector: Selector, text: String): Boolean {
        val node = findNode(selector) ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.useForAction { performActionUpwards(AccessibilityNodeInfo.ACTION_SET_TEXT, args) }
    }

    fun scroll(selector: Selector, direction: String): Boolean {
        val node = findNode(selector) ?: return false
        val normalized = direction.lowercase()
        Log.d(TAG, "Attempting scroll selector=$selector direction=$direction")
        val action = when (normalized) {
            "up", "backward" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            "down", "forward" -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            "left" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            "right" -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            else -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        }
        return node.useForAction {
            val actionPerformed = performActionUpwards(action)
            if (!actionPerformed) {
                Log.d(TAG, "Accessibility scroll failed, falling back to gesture selector=$selector")
            }
            val result = actionPerformed || performScrollGesture(this, normalized)
            Log.d(TAG, "Scroll result=$result selector=$selector direction=$normalized")
            result
        }
    }

    fun swipe(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        
        // Rate limiting: Ensure minimum interval between swipes
        val currentTime = SystemClock.uptimeMillis()
        val timeSinceLastSwipe = currentTime - lastSwipeTime
        if (timeSinceLastSwipe < MIN_SWIPE_INTERVAL_MS) {
            val waitTime = MIN_SWIPE_INTERVAL_MS - timeSinceLastSwipe
            Log.w(TAG, "Rate limiting swipe: waiting ${waitTime}ms (last swipe was ${timeSinceLastSwipe}ms ago)")
            try {
                Thread.sleep(waitTime)
            } catch (e: InterruptedException) {
                Log.e(TAG, "Swipe rate limit wait interrupted")
                return false
            }
        }
        
        // Check if app is responsive before swiping
        val root = rootInActiveWindow
        if (root == null) {
            Log.w(TAG, "App not responsive (no root window), skipping swipe")
            try {
                Thread.sleep(2000) // Wait longer if app is not responsive
            } catch (e: InterruptedException) {
                return false
            }
            return false
        }
        root.release()
        
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, duration)
        showSwipeIndicator(startX, startY, endX, endY)
        val result = dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
        
        // Update last swipe time
        if (result) {
            lastSwipeTime = SystemClock.uptimeMillis()
        }
        
        return result
    }

    private fun performTapGesture(x: Float, y: Float): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val path = Path().apply {
            moveTo(x, y)
            lineTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 80L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val result = dispatchGesture(gesture, null, null)
        Log.d(TAG, "Gesture tap dispatched result=$result x=$x y=$y")
        return result
    }

    private fun performScrollGesture(node: AccessibilityNodeInfo, direction: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.isEmpty) return false

        val width = bounds.width().coerceAtLeast(1)
        val height = bounds.height().coerceAtLeast(1)
        val horizontalPadding = width * 0.15f
        val verticalPadding = height * 0.15f

        val startX: Float
        val startY: Float
        val endX: Float
        val endY: Float

        when (direction) {
            "up", "backward" -> {
                startX = bounds.centerX().toFloat()
                startY = bounds.top + verticalPadding
                endX = startX
                endY = bounds.bottom - verticalPadding
            }

            "down", "forward" -> {
                startX = bounds.centerX().toFloat()
                startY = bounds.bottom - verticalPadding
                endX = startX
                endY = bounds.top + verticalPadding
            }

            "left" -> {
                startY = bounds.centerY().toFloat()
                startX = bounds.left + horizontalPadding
                endX = bounds.right - horizontalPadding
                endY = startY
            }

            "right" -> {
                startY = bounds.centerY().toFloat()
                startX = bounds.right - horizontalPadding
                endX = bounds.left + horizontalPadding
                endY = startY
            }

            else -> {
                startX = bounds.centerX().toFloat()
                startY = bounds.bottom - verticalPadding
                endX = startX
                endY = bounds.top + verticalPadding
            }
        }

        val result = swipe(startX, startY, endX, endY, 320L)
        Log.d(TAG, "Gesture scroll dispatched result=$result direction=$direction start=(${startX},${startY}) end=(${endX},${endY})")
        return result
    }

    fun waitFor(
        selector: Selector,
        timeoutMs: Long,
        pollDelayMs: Long = 250L,
        maxScrolls: Int = 5, // New parameter for max scrolls
        scrollContainer: Selector? = null // New parameter for scrollable container
    ): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        var scrollsPerformed = 0
        var lastScrollAttemptTime = SystemClock.uptimeMillis()
        var previousRootHash: Int? = null

        while (SystemClock.uptimeMillis() < deadline) {
            val root = rootInActiveWindow
            val currentRootHash = root?.hashCode()
            root?.release() // Release root immediately if not used further

            val node = findNode(selector)
            if (node != null) {
                node.release()
                return true
            }

            // Check if we need to scroll
            if (scrollsPerformed < maxScrolls && SystemClock.uptimeMillis() - lastScrollAttemptTime > pollDelayMs * 2) {
                val scrollableNode = if (scrollContainer != null) {
                    findNode(scrollContainer)
                } else {
                    rootInActiveWindow // Try to scroll the root window
                }

                if (scrollableNode != null) {
                    // Only scroll if the root content has changed or if it's the first scroll attempt.
                    // This prevents infinite scrolling on static pages or when no scrollable container is found.
                    if (currentRootHash == null || currentRootHash != previousRootHash || scrollsPerformed == 0) {
                        Log.d(TAG, "Scrolling down to find element: $selector (scrolls: ${scrollsPerformed + 1})")
                        // Use scroll() function if the container is scrollable, otherwise use swipe gesture
                        val scrolled = scrollableNode.useForAction {
                            if (scrollableNode.isActuallyScrollable()) { // Changed to call extension function
                                performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                            } else {
                                performScrollGesture(scrollableNode, "down")
                            }
                        }

                        if (scrolled) {
                            scrollsPerformed++
                            lastScrollAttemptTime = SystemClock.uptimeMillis()
                            previousRootHash = currentRootHash
                            Thread.sleep(pollDelayMs) // Give time for UI to settle after scroll
                            continue // Re-check for node immediately after scroll
                        } else {
                            Log.d(TAG, "Scroll action failed for selector: $selector. No more scrolling.")
                            scrollableNode.release()
                            break // Can't scroll further, break the loop
                        }
                    } else {
                        Log.d(TAG, "Root content hasn't changed. No more scrolling for selector: $selector.")
                        scrollableNode.release()
                        break // Content is static, no point in further scrolling
                    }
                } else {
                    Log.d(TAG, "No scrollable container found for selector: $selector. No more scrolling.")
                    break // No scrollable node, break the loop
                }
            }

            Thread.sleep(pollDelayMs)
        }
        return false
    }

    fun waitForIdle(timeoutMs: Long): Boolean = try {
        Thread.sleep(timeoutMs)
        true
    } catch (_: InterruptedException) {
        false
    }

    fun dumpTree(maxDepth: Int = Int.MAX_VALUE): List<Map<String, Any?>> {
        val root = rootInActiveWindow ?: return emptyList()
        val results = mutableListOf<Map<String, Any?>>()

        fun traverse(node: AccessibilityNodeInfo, depth: Int) {
            val entry = asNodeMap(node).toMutableMap()
            entry["depth"] = depth
            entry["childCount"] = node.childCount
            entry["checkable"] = node.isCheckable
            entry["checked"] = node.isChecked
            entry["selected"] = node.isSelected
            entry["scrollable"] = node.isScrollable
            entry["editable"] = node.isEditable
            entry["longClickable"] = node.isLongClickable
            entry["password"] = node.isPassword
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                entry["stateDescription"] = node.stateDescription?.toString()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                entry["hintText"] = node.hintText?.toString()
            }
            entry["actions"] = node.actionList.map { it.describe() }
            results += entry

            if (depth >= maxDepth) {
                return
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                traverse(child, depth + 1)
                child.release()
            }
        }

        traverse(root, 0)
        root.release()
        return results
    }

    fun asNodeMap(node: AccessibilityNodeInfo): Map<String, Any?> {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        return mapOf(
            "text" to node.text?.toString(),
            "desc" to node.contentDescription?.toString(),
            "id" to node.viewIdResourceName,
            "className" to node.className?.toString(),
            "packageName" to node.packageName?.toString(),
            "bounds" to mapOf(
                "left" to bounds.left,
                "top" to bounds.top,
                "right" to bounds.right,
                "bottom" to bounds.bottom
            ),
            "clickable" to node.isClickable,
            "enabled" to node.isEnabled,
            "focused" to node.isFocused,
            "focusable" to node.isFocusable,
            "visible" to node.isVisibleToUser
        )
    }

    private fun findNodeRecursive(node: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (predicate(node) && node.isEffectivelyVisible()) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeRecursive(child, predicate)
            if (found != null) return found
        }
        return null
    }

    private fun findAllRecursive(node: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean, list: MutableList<AccessibilityNodeInfo>) {
        if (predicate(node) && node.isEffectivelyVisible()) list.add(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findAllRecursive(child, predicate, list)
        }
    }

    fun launchActivity(intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch activity", e)
            false
        }
    }

    fun launchApp(packageName: String): Boolean {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
        return launchActivity(intent)
    }

    fun pressBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    fun pressHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Service is active when it receives events
        // This helps ensure the service stays connected
        if (instance == null) {
            instance = this
            Log.d(TAG, "Service instance set via accessibility event")
        }
    }
    
    override fun onInterrupt() {
        Log.w(TAG, "Service interrupted")
    }
    override fun onDestroy() {
        Log.w(TAG, "Accessibility service being destroyed!")
        if (instance == this) instance = null
        touchOverlay?.remove()
        toastOverlay?.remove()
        touchOverlay = null
        toastOverlay = null
        super.onDestroy()
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var touchOverlay: TouchOverlay? = null
    private var toastOverlay: ToastOverlay? = null
    private var touchVisualizationEnabled = false

    private fun setTouchVisualization(enabled: Boolean) {
        if (touchVisualizationEnabled == enabled) return
        touchVisualizationEnabled = enabled
        if (!enabled) {
            mainHandler.post {
                touchOverlay?.remove()
                touchOverlay = null
            }
        }
    }

    private fun ensureOverlay(): TouchOverlay? {
        if (!touchVisualizationEnabled) return null
        val overlay = touchOverlay
        if (overlay != null) return overlay
        return TouchOverlay(this).also {
            if (it.attach()) {
                touchOverlay = it
            }
        }
    }

    private fun ensureToastOverlay(): ToastOverlay? {
        if (!Settings.canDrawOverlays(this)) {
            return null
        }
        val overlay = toastOverlay
        if (overlay != null) return overlay
        return ToastOverlay(this).also {
            if (it.attach()) {
                toastOverlay = it
            }
        }
    }

    fun showToast(message: String, durationMs: Long = 2500L) {
        mainHandler.post {
            val overlay = ensureToastOverlay()
            if (overlay != null) {
                overlay.show(message, durationMs)
            } else {
                Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun showTouchIndicator(selector: Selector) {
        val node = findNode(selector) ?: return
        node.useForAction {
            val bounds = Rect()
            getBoundsInScreen(bounds)
            if (!bounds.isEmpty) {
                showTouchIndicatorAt(bounds.centerX().toFloat(), bounds.centerY().toFloat())
            }
            true
        }
    }

    private fun showSwipeIndicator(startX: Float, startY: Float, endX: Float, endY: Float) {
        val overlay = ensureOverlay() ?: return
        mainHandler.post { overlay.showSwipe(startX, startY, endX, endY) }
    }

    private fun showTouchIndicatorAt(x: Float, y: Float) {
        val overlay = ensureOverlay() ?: return
        mainHandler.post { overlay.showTap(x, y) }
    }
}

private inline fun AccessibilityNodeInfo.useForAction(block: AccessibilityNodeInfo.() -> Boolean): Boolean {
    return try {
        block()
    } finally {
        recycle()
    }
}

private fun AccessibilityNodeInfo.performActionUpwards(
    actionId: Int,
    args: Bundle? = null
): Boolean {
    val visited = mutableListOf<AccessibilityNodeInfo>()
    var current: AccessibilityNodeInfo? = this
    while (current != null) {
        val performed = if (args != null) {
            current.performAction(actionId, args)
        } else {
            current.performAction(actionId)
        }
        if (performed) {
            visited.forEach { it.release() }
            if (current !== this) {
                current.release()
            }
            return true
        }
        val parent = current.parent
        if (current !== this) {
            visited += current
        }
        current = parent
    }
    visited.forEach { it.release() }
    return false
}

private fun AccessibilityNodeInfo.isEffectivelyVisible(): Boolean {
    if (isVisibleToUser) return true
    val bounds = Rect()
    getBoundsInScreen(bounds)
    return !bounds.isEmpty && bounds.width() > 0 && bounds.height() > 0
}

private fun AccessibilityNodeInfo.isActuallyScrollable(): Boolean {
    return isScrollable || 
           (actionList.contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD) ||
            actionList.contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD))
}

private fun AccessibilityNodeInfo.AccessibilityAction.describe(): String {
    val name = actionName(id)
    val labelText = label?.toString()?.takeIf { it.isNotBlank() }
    return if (labelText != null) "$name($labelText)" else name
}

@SuppressLint("InlinedApi")
private fun actionName(actionId: Int): String = when (actionId) {
    AccessibilityNodeInfo.ACTION_FOCUS -> "ACTION_FOCUS"
    AccessibilityNodeInfo.ACTION_CLEAR_FOCUS -> "ACTION_CLEAR_FOCUS"
    AccessibilityNodeInfo.ACTION_SELECT -> "ACTION_SELECT"
    AccessibilityNodeInfo.ACTION_CLEAR_SELECTION -> "ACTION_CLEAR_SELECTION"
    AccessibilityNodeInfo.ACTION_CLICK -> "ACTION_CLICK"
    AccessibilityNodeInfo.ACTION_LONG_CLICK -> "ACTION_LONG_CLICK"
    AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS -> "ACTION_ACCESSIBILITY_FOCUS"
    AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS -> "ACTION_CLEAR_ACCESSIBILITY_FOCUS"
    AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY -> "ACTION_NEXT_AT_MOVEMENT_GRANULARITY"
    AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY -> "ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY"
    AccessibilityNodeInfo.ACTION_NEXT_HTML_ELEMENT -> "ACTION_NEXT_HTML_ELEMENT"
    AccessibilityNodeInfo.ACTION_PREVIOUS_HTML_ELEMENT -> "ACTION_PREVIOUS_HTML_ELEMENT"
    AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> "ACTION_SCROLL_FORWARD"
    AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> "ACTION_SCROLL_BACKWARD"
    AccessibilityNodeInfo.ACTION_COPY -> "ACTION_COPY"
    AccessibilityNodeInfo.ACTION_PASTE -> "ACTION_PASTE"
    AccessibilityNodeInfo.ACTION_CUT -> "ACTION_CUT"
    AccessibilityNodeInfo.ACTION_SET_SELECTION -> "ACTION_SET_SELECTION"
    AccessibilityNodeInfo.ACTION_EXPAND -> "ACTION_EXPAND"
    AccessibilityNodeInfo.ACTION_COLLAPSE -> "ACTION_COLLAPSE"
    AccessibilityNodeInfo.ACTION_DISMISS -> "ACTION_DISMISS"
    AccessibilityNodeInfo.ACTION_SET_TEXT -> "ACTION_SET_TEXT"
    // Note: Some actions below may not be available in all API levels
    // They will fall through to the else case if not available
    else -> {
        // Handle API-specific actions using numeric constants
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && actionId == 0x00000010 -> "ACTION_SHOW_ON_SCREEN"
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && actionId == 0x00001000 -> "ACTION_SCROLL_UP"
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && actionId == 0x00002000 -> "ACTION_SCROLL_DOWN"
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && actionId == 0x00004000 -> "ACTION_SCROLL_LEFT"
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && actionId == 0x00008000 -> "ACTION_SCROLL_RIGHT"
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && actionId == 0x00010000 -> "ACTION_CONTEXT_CLICK"
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && actionId == 0x00020000 -> "ACTION_PRESS_AND_HOLD"
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && actionId == 0x00040000 -> "ACTION_PAGE_UP"
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && actionId == 0x00080000 -> "ACTION_PAGE_DOWN"
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && actionId == 0x00100000 -> "ACTION_PAGE_LEFT"
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && actionId == 0x00200000 -> "ACTION_PAGE_RIGHT"
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && actionId == 0x00400000 -> "ACTION_MOVE_WINDOW"
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && actionId == 0x00800000 -> "ACTION_SCROLL_TO_POSITION"
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && actionId == 0x00080001 -> "ACTION_SHOW_TEXT_SUGGESTIONS"
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && actionId == 0x00080002 -> "ACTION_DRAG_START"
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && actionId == 0x00080003 -> "ACTION_DRAG_DROP"
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && actionId == 0x00080004 -> "ACTION_DRAG_CANCEL"
            else -> "ACTION_$actionId"
        }
    }
}

private class TouchOverlay(private val service: AutomationAccessibilityService) {
    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var container: FrameLayout? = null
    private var tapView: View? = null
    private var swipeView: View? = null

    fun attach(): Boolean {
        if (!Settings.canDrawOverlays(service)) {
            return false
        }

        val layoutInflater = LayoutInflater.from(service)
        val frame = FrameLayout(service).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }

        val tapBubble = layoutInflater.inflate(R.layout.touch_indicator_tap, frame, false)
        val swipeBubble = layoutInflater.inflate(R.layout.touch_indicator_swipe, frame, false)
        frame.addView(tapBubble)
        frame.addView(swipeBubble)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.graphics.PixelFormat.TRANSLUCENT
        )

        windowManager.addView(frame, params)
        container = frame
        tapView = tapBubble
        swipeView = swipeBubble
        hideIndicators()
        return true
    }

    fun remove() {
        container?.let {
            windowManager.removeView(it)
        }
        container = null
        tapView = null
        swipeView = null
    }

    fun showTap(x: Float, y: Float) {
        hideSwipe()
        val tap = tapView ?: return
        tap.apply {
            visibility = View.VISIBLE
            animate().cancel()
            translationX = x - width / 2f
            translationY = y - height / 2f
            alpha = 1f
            scaleX = 1f
            scaleY = 1f
            animate()
                .alpha(0f)
                .scaleX(1.4f)
                .scaleY(1.4f)
                .setDuration(450)
                .withEndAction { visibility = View.GONE }
                .start()
        }
    }

    fun showSwipe(startX: Float, startY: Float, endX: Float, endY: Float) {
        hideTap()
        val swipe = swipeView ?: return
        swipe.apply {
            visibility = View.VISIBLE
            animate().cancel()
            findViewById<View>(R.id.swipe_line)?.let { line ->
                val dx = endX - startX
                val dy = endY - startY
                val length = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                line.layoutParams = line.layoutParams.apply {
                    width = length.toInt()
                }
                val angle = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                translationX = startX
                translationY = startY
                rotation = angle
            }
            alpha = 1f
            animate()
                .alpha(0f)
                .setDuration(500)
                .withEndAction { visibility = View.GONE }
                .start()
        }
    }

    private fun hideIndicators() {
        hideTap()
        hideSwipe()
    }

    private fun hideTap() {
        tapView?.visibility = View.GONE
    }

    private fun hideSwipe() {
        swipeView?.visibility = View.GONE
    }
}

private class ToastOverlay(private val service: AutomationAccessibilityService) {
    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var container: FrameLayout? = null
    private var textView: TextView? = null
    private var hideRunnable: Runnable? = null

    fun attach(): Boolean {
        if (!Settings.canDrawOverlays(service)) {
            return false
        }

        val layoutInflater = LayoutInflater.from(service)
        val frame = FrameLayout(service).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }
        val toastText = layoutInflater.inflate(R.layout.toast_overlay, frame, false) as TextView
        frame.addView(toastText)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_TOAST
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            val density = service.resources.displayMetrics.density
            y = (density * 64).toInt()
        }

        windowManager.addView(frame, params)
        container = frame
        textView = toastText.apply {
            alpha = 0f
            visibility = View.GONE
        }
        return true
    }

    fun show(message: String, durationMs: Long) {
        val view = textView ?: return
        hideRunnable?.let { view.removeCallbacks(it) }
        view.text = message
        if (view.visibility != View.VISIBLE) {
            view.visibility = View.VISIBLE
        }
        view.animate().cancel()
        view.alpha = 0f
        view.translationY = view.resources.displayMetrics.density * 12
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(160)
            .start()
        val hideTask = Runnable {
            view.animate()
                .alpha(0f)
                .setDuration(180)
                .withEndAction {
                    view.visibility = View.GONE
                }
                .start()
        }
        hideRunnable = hideTask
        view.postDelayed(hideTask, durationMs)
    }

    fun remove() {
        textView?.let { view ->
            hideRunnable?.let { view.removeCallbacks(it) }
        }
        container?.let {
            windowManager.removeView(it)
        }
        container = null
        textView = null
        hideRunnable = null
    }
}
