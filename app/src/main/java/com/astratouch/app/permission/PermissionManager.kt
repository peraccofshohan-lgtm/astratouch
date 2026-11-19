package com.astratouch.app.permission

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.astratouch.app.accessibility.AstraAccessibilityService
import com.astratouch.app.domain.model.AccessCatalogue
import com.astratouch.app.domain.model.AccessDescriptor
import com.astratouch.app.domain.model.AccessId
import com.astratouch.app.domain.model.AccessState
import com.astratouch.app.domain.model.AccessStatus
import com.astratouch.app.domain.model.PermissionSnapshot
import com.astratouch.app.service.action.DeviceCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central authority for every special access AstraTouch uses.
 *
 * States are recomputed on demand and cached in [snapshot] so Compose screens can
 * observe them. Nothing here ever *assumes* a permission: an access that has
 * been revoked since app start immediately reports DENIED, and dependent
 * features degrade with an explicit explanation.
 */
@Singleton
class PermissionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val capabilities: DeviceCapabilities,
) {
    private val _snapshot = MutableStateFlow(buildSnapshot())
    val snapshot: StateFlow<PermissionSnapshot> = _snapshot.asStateFlow()

    /** Called on resume of every screen and on service connect/disconnect. */
    fun refresh(): PermissionSnapshot {
        val next = buildSnapshot()
        _snapshot.value = next
        return next
    }

    fun current(): PermissionSnapshot = _snapshot.value

    fun stateOf(id: AccessId): AccessState = refresh().stateOf(id)

    fun isGranted(id: AccessId): Boolean = stateOf(id).isGranted

    private fun buildSnapshot(): PermissionSnapshot {
        val now = System.currentTimeMillis()
        val statuses = AccessCatalogue.all.map { descriptor ->
            AccessStatus(descriptor = descriptor, state = resolveState(descriptor.id), lastCheckedAt = now)
        }
        return PermissionSnapshot(statuses)
    }

    private fun resolveState(id: AccessId): AccessState = when (id) {
        AccessId.OVERLAY -> if (Settings.canDrawOverlays(context)) AccessState.GRANTED else AccessState.DENIED
        AccessId.ACCESSIBILITY -> if (capabilities.hasAccessibility()) AccessState.GRANTED else AccessState.DENIED
        AccessId.WRITE_SETTINGS -> if (Settings.System.canWrite(context)) AccessState.GRANTED else AccessState.DENIED
        AccessId.CAMERA -> when {
            !capabilities.hasFlashlight() -> AccessState.NOT_REQUIRED
            capabilities.hasCameraPermission() -> AccessState.GRANTED
            else -> AccessState.DENIED
        }
        AccessId.NOTIFICATIONS -> when {
            capabilities.sdkInt < 33 -> AccessState.GRANTED
            capabilities.hasNotificationPermission() -> AccessState.GRANTED
            else -> AccessState.DENIED
        }
        AccessId.BATTERY_OPTIMIZATION ->
            if (capabilities.isIgnoringBatteryOptimizations()) AccessState.GRANTED else AccessState.DENIED
    }

    /**
     * Opens the exact Android screen that grants [id], using the most specific
     * intent available on this API level. Falls back to app details.
     */
    fun openSettings(id: AccessId): Intent {
        val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return when (id) {
            AccessId.OVERLAY -> Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            AccessId.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            AccessId.WRITE_SETTINGS -> Intent(
                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            AccessId.CAMERA, AccessId.NOTIFICATIONS -> appDetails

            AccessId.BATTERY_OPTIMIZATION -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Launches the right settings screen. Returns false when the platform has no
     * handler, in which case the caller shows the app-details fallback.
     */
    fun requestAccess(id: AccessId): Boolean {
        val intents = listOf(openSettings(id)) + listOf(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        for (intent in intents) {
            try {
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                // try the next one
            }
        }
        return false
    }

    /** Direct link to AstraTouch's own accessibility entry, when available. */
    fun accessibilityComponent(): ComponentName =
        ComponentName(context, AstraAccessibilityService::class.java)

    fun descriptors(): List<AccessDescriptor> = AccessCatalogue.all

    /** True when the overlay + accessibility pair required by the core UX is met. */
    fun corePermissionsSatisfied(): Boolean {
        val s = refresh()
        return s.isGranted(AccessId.OVERLAY) && s.isGranted(AccessId.ACCESSIBILITY)
    }
}
