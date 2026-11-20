package com.astratouch.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.astratouch.app.R
import com.astratouch.app.data.prefs.PreferencesStore
import com.astratouch.app.data.repository.ProfileRepository
import com.astratouch.app.service.FloatingOverlayService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Restores the floating bubble after a reboot or an app update, but only when
 * the user has both enabled the bubble and granted the overlay permission.
 * Nothing is started silently against the user's wishes.
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject lateinit var preferencesStore: PreferencesStore
    @Inject lateinit var profileRepository: ProfileRepository

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (!Settings.canDrawOverlays(context)) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val prefs = preferencesStore.preferences.first()
                val profile = profileRepository.getActiveProfile()
                val bubble = profile?.let { profileRepository.getBubbleConfig(it.bubbleConfigId) }
                val shouldStart = prefs.overlayEnabled && (bubble?.startupOnBoot ?: false)
                if (!shouldStart) return@launch

                if (canStartForegroundFromBackground(action)) {
                    FloatingOverlayService.start(context)
                } else {
                    // Android 12+ forbids starting a `specialUse` foreground service
                    // from BOOT_COMPLETED (only a few types are exempt, and the list
                    // changes between releases). Rather than crash the receiver, post
                    // a tap-to-restore notification: the user's tap is a foreground
                    // interaction, so the service may legally start from there.
                    Log.i(TAG, "Post-boot FGS start not permitted; posting restore prompt")
                    BootRestoreNotifier.show(context)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Boot restore failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "AstraBootReceiver"
    }

    private fun canStartForegroundFromBackground(action: String): Boolean {
        // MY_PACKAGE_REPLACED is an exempt broadcast; BOOT_COMPLETED is not for
        // specialUse services on API 31+.
        if (action == Intent.ACTION_MY_PACKAGE_REPLACED) return true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return false
    }
}

/**
 * "Tap to restore the bubble" notification used when Android will not let the app
 * start its foreground service straight from a boot broadcast.
 */
internal object BootRestoreNotifier {
    private const val CHANNEL_ID = "astra_boot_restore"
    private const val NOTIFICATION_ID = 4201

    fun show(context: Context) {
        val manager = context.getSystemService(android.app.NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                android.app.NotificationChannel(
                    CHANNEL_ID,
                    "Bubble restore",
                    android.app.NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Offers to bring back the floating bubble after a reboot."
                    setShowBadge(false)
                },
            )
        }
        val tap = android.app.PendingIntent.getActivity(
            context,
            0,
            Intent(context, com.astratouch.app.MainActivity::class.java)
                .putExtra(com.astratouch.app.MainActivity.EXTRA_RESTORE_OVERLAY, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = androidx.core.app.NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_astra)
            .setContentTitle("Bubble is ready")
            .setContentText("Tap to bring AstraTouch's floating bubble back after the restart.")
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }
}
