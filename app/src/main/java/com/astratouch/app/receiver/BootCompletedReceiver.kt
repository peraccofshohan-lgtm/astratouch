package com.astratouch.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
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
                if (shouldStart) {
                    FloatingOverlayService.start(context)
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
}
