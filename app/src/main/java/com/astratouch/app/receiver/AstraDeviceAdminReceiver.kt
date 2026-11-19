package com.astratouch.app.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * Device-admin receiver used *only* by the optional "Lock screen" action.
 *
 * AstraTouch requests no device-admin capabilities beyond `force-lock`, and the
 * action is unavailable until the user explicitly activates the admin in
 * Android Settings (see docs/PERMISSIONS.md).
 */
class AstraDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        Toast.makeText(context, "AstraTouch can now lock the screen.", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Toast.makeText(context, "AstraTouch lock-screen action disabled.", Toast.LENGTH_SHORT).show()
    }
}
