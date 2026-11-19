package com.astratouch.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AstraTouch accessibility service.
 *
 * Design constraints (Part 8 / Part 19):
 *  * It exists so the user can perform global actions from the floating bubble.
 *  * It subscribes only to window-state events, which tell us which app is in
 *    the foreground. It does not read view text, does not traverse the tree
 *    (`canRetrieveWindowContent="false"`) and never stores screen content.
 *  * If the user revokes the service, [instance] becomes null and every
 *    dependent action immediately reports "needs permission".
 */
class AstraAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _connected.value = true
        _foregroundPackage.value = null
        Log.i(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val pkg = event.packageName?.toString()
                if (!pkg.isNullOrBlank() && pkg != packageName) {
                    _foregroundPackage.value = pkg
                }
            }
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> Unit
            else -> Unit
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        _connected.value = false
        _foregroundPackage.value = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        _connected.value = false
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AstraAccessibility"

        @Volatile
        var instance: AstraAccessibilityService? = null
            private set

        private val _connected = MutableStateFlow(false)
        val connected: StateFlow<Boolean> = _connected.asStateFlow()

        private val _foregroundPackage = MutableStateFlow<String?>(null)

        /** Foreground app package, used by profile auto-switching. Never persisted. */
        val foregroundPackage: StateFlow<String?> = _foregroundPackage.asStateFlow()
    }
}
