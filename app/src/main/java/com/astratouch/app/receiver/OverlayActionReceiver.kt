package com.astratouch.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Internal broadcast endpoint for overlay control from notifications and timers.
 *
 * Not exported: only AstraTouch's own PendingIntents can target it, and it never
 * accepts an arbitrary "run this action" payload from outside the app. Only the
 * three hard-coded commands below are honoured.
 */
class OverlayActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val controller = com.astratouch.app.service.overlay.OverlayController.instance
        if (controller == null) {
            Log.w(TAG, "Overlay is not running; ignoring ${intent.action}")
            return
        }
        when (intent.action) {
            ACTION_SHOW -> controller.showBubble()
            ACTION_HIDE -> controller.hideBubble()
            ACTION_TOGGLE_MENU -> controller.toggleMenu()
            else -> Log.w(TAG, "Unknown overlay action: ${intent.action}")
        }
    }

    companion object {
        private const val TAG = "AstraOverlayReceiver"
        const val ACTION_SHOW = "com.astratouch.app.internal.SHOW_BUBBLE"
        const val ACTION_HIDE = "com.astratouch.app.internal.HIDE_BUBBLE"
        const val ACTION_TOGGLE_MENU = "com.astratouch.app.internal.TOGGLE_MENU"
    }
}
