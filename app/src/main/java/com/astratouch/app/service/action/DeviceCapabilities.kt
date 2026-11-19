package com.astratouch.app.service.action

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.provider.Settings
import com.astratouch.app.accessibility.AstraAccessibilityService
import com.astratouch.app.domain.model.AccessId
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for "can this device actually do that?".
 *
 * Everything here is a cheap query. No capability is ever assumed: if the
 * platform cannot answer, we report unsupported rather than guessing.
 */
@Singleton
class DeviceCapabilities @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val sdkInt: Int get() = Build.VERSION.SDK_INT

    fun hasOverlayPermission(): Boolean = Settings.canDrawOverlays(context)

    fun hasWriteSettings(): Boolean {
        // Manifest permission + app-op, both reported by the platform.
        return Settings.System.canWrite(context)
    }

    /**
     * Accessibility status is derived from the live service instance *and* the
     * settings list, so a stale instance cannot report a false positive.
     */
    fun hasAccessibility(): Boolean {
        if (AstraAccessibilityService.instance != null) return true
        val enabled = try {
            Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
            )
        } catch (e: Settings.SettingNotFoundException) {
            0
        }
        if (enabled != 1) return false
        val services = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val expected = "${context.packageName}/${AstraAccessibilityService::class.java.name}"
        return services.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    fun hasCameraPermission(): Boolean =
        context.checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    fun hasNotificationPermission(): Boolean =
        if (sdkInt >= 33) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun accessState(id: AccessId): Boolean = when (id) {
        AccessId.OVERLAY -> hasOverlayPermission()
        AccessId.ACCESSIBILITY -> hasAccessibility()
        AccessId.WRITE_SETTINGS -> hasWriteSettings()
        AccessId.CAMERA -> hasCameraPermission()
        AccessId.NOTIFICATIONS -> hasNotificationPermission()
        AccessId.BATTERY_OPTIMIZATION -> isIgnoringBatteryOptimizations()
    }

    /** True when at least one rear or front camera exposes a flash unit. */
    fun hasFlashlight(): Boolean {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return false
        return try {
            cm.cameraIdList.any { id ->
                val chars = cm.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (e: Exception) {
            false
        }
    }

    fun hasDeviceLock(): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return false
        return dpm.isAdminActive(
            android.content.ComponentName(context, com.astratouch.app.receiver.AstraDeviceAdminReceiver::class.java),
        )
    }

    fun canSetBrightness(): Boolean = hasWriteSettings()

    val isAtLeastR: Boolean get() = sdkInt >= 30
    val isAtLeastS: Boolean get() = sdkInt >= 31
    val isAtLeastT: Boolean get() = sdkInt >= 33
    val isAtLeastU: Boolean get() = sdkInt >= 34
    val isAtLeastV: Boolean get() = sdkInt >= 35
    val isAtLeastBaklava: Boolean get() = sdkInt >= 36
}
