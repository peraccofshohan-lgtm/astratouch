package com.astratouch.app.service.action

import android.accessibilityservice.AccessibilityService
import android.bluetooth.BluetoothAdapter
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import com.astratouch.app.MainActivity
import com.astratouch.app.accessibility.AstraAccessibilityService
import com.astratouch.app.domain.model.AccessId
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton

private const val ACTION_TIMEOUT_MS = 5_000L

/** Shared helper for actions that need the live accessibility service. */
private suspend fun requireAccessibility(): AccessibilityService? =
    withTimeoutOrNull(ACTION_TIMEOUT_MS) {
        var tries = 0
        while (AstraAccessibilityService.instance == null && tries < 20) {
            delay(50)
            tries++
        }
        AstraAccessibilityService.instance
    }

abstract class BaseHandler(@ApplicationContext protected val context: Context) : ActionHandler {

    /** Starts an activity safely, mapping failures to user-facing results. */
    protected fun startActivitySafely(intent: Intent): ActionResult {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            ActionResult.Success
        } catch (e: ActivityNotFoundException) {
            ActionResult.Failure(
                FailureReason.NOT_SUPPORTED,
                "This Android version does not provide that settings screen.",
            )
        } catch (e: SecurityException) {
            ActionResult.Failure(
                FailureReason.PERMISSION_MISSING,
                "Android blocked opening that screen. Open Settings manually and look for it there.",
            )
        } catch (e: Exception) {
            ActionResult.Failure(FailureReason.INTERNAL, "Could not open that screen: ${e.message ?: "unknown error"}")
        }
    }

    protected fun openAstraRoute(route: String): ActionResult =
        startActivitySafely(
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, route),
        )
}

/** Global navigation actions performed by the accessibility service. */
class GlobalActionHandler(
    context: Context,
    private val globalAction: Int,
    private val name: String,
) : BaseHandler(context) {
    override suspend fun execute(target: String?): ActionResult {
        val service = requireAccessibility() ?: return AccessibilityUnavailable()
        val ok = withTimeoutOrNull(ACTION_TIMEOUT_MS) {
            service.performGlobalAction(globalAction)
        }
        return when (ok) {
            true -> ActionResult.Success
            false -> ActionResult.Failure(
                FailureReason.NOT_SUPPORTED,
                "Android refused the \"$name\" global action. This can happen on the lock screen or during a system dialog.",
            )
            null -> ActionResult.Failure(FailureReason.TIMEOUT, "The \"$name\" action timed out.")
        }
    }
}

private fun AccessibilityUnavailable(): ActionResult = ActionResult.NeedsPermission(
    AccessId.ACCESSIBILITY,
    "Turn on the AstraTouch accessibility service to use global navigation actions.",
)

/** Opens the notification shade via accessibility global action. */
class NotificationsHandler(context: Context) : BaseHandler(context) {
    override suspend fun execute(target: String?): ActionResult {
        val service = requireAccessibility() ?: return AccessibilityUnavailable()
        val ok = withTimeoutOrNull(ACTION_TIMEOUT_MS) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            } else {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            }
        }
        return if (ok == true) ActionResult.Success else ActionResult.Failure(
            FailureReason.NOT_SUPPORTED,
            "Android did not open the notification shade. Try swiping down from the top of the screen.",
        )
    }
}

/** Quick settings panel, with a documented fallback for Android 12+. */
class QuickSettingsHandler(context: Context) : BaseHandler(context) {
    override suspend fun execute(target: String?): ActionResult {
        val service = requireAccessibility() ?: return AccessibilityUnavailable()
        // GLOBAL_ACTION_QUICK_SETTINGS exists since API 17 but some OEMs only
        // expand the shade. Try the dedicated action, then fall back.
        val ok = withTimeoutOrNull(ACTION_TIMEOUT_MS) {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
        }
        return if (ok == true) ActionResult.Success else {
            val fallback = withTimeoutOrNull(ACTION_TIMEOUT_MS) {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            }
            if (fallback == true) {
                ActionResult.Partial(
                    "Android opened the notification shade instead of the full Quick Settings panel. " +
                        "Pull it down once more to reach the tiles.",
                )
            } else {
                ActionResult.Failure(FailureReason.NOT_SUPPORTED, "Quick Settings could not be opened on this device.")
            }
        }
    }
}

/** Locks the screen via device admin, then falls back to accessibility lock. */
class LockScreenHandler(context: Context, private val capabilities: DeviceCapabilities) : BaseHandler(context) {
    override suspend fun execute(target: String?): ActionResult {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE)
            as? android.app.admin.DevicePolicyManager
        val admin = ComponentName(context, com.astratouch.app.receiver.AstraDeviceAdminReceiver::class.java)
        if (dpm != null && dpm.isAdminActive(admin)) {
            return try {
                dpm.lockNow()
                ActionResult.Success
            } catch (e: SecurityException) {
                ActionResult.Failure(FailureReason.PERMISSION_MISSING, "Android refused to lock the screen.")
            }
        }
        // Accessibility LOCK_SCREEN global action requires API 28+.
        val service = AstraAccessibilityService.instance
        if (service != null) {
            val ok = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            if (ok) return ActionResult.Success
        }
        return ActionResult.Failure(
            FailureReason.PERMISSION_MISSING,
            "Locking the screen needs either the AstraTouch screen-lock device admin or the accessibility service. " +
                "Enable one of them in the Permission Center.",
        )
    }
}

/** Volume manipulation via AudioManager. */
class VolumeHandler(
    context: Context,
    private val direction: Int,
    private val showPanel: Boolean = false,
) : BaseHandler(context) {
    override suspend fun execute(target: String?): ActionResult {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ActionResult.Failure(FailureReason.NOT_SUPPORTED, "Audio service is unavailable.")
        return try {
            if (showPanel || Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+ requires an activity to show the panel reliably;
                // the system volume UI is legitimately triggered by adjustVolume.
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
            } else {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
            }
            ActionResult.Success
        } catch (e: SecurityException) {
            ActionResult.Failure(FailureReason.PERMISSION_MISSING, "Android blocked changing the volume.")
        }
    }
}

/** Brightness control using WRITE_SETTINGS, with a clear failure story. */
class BrightnessHandler(
    context: Context,
    private val delta: Int,
) : BaseHandler(context) {
    override suspend fun execute(target: String?): ActionResult {
        if (!Settings.System.canWrite(context)) {
            return ActionResult.NeedsPermission(
                AccessId.WRITE_SETTINGS,
                "Brightness needs the \"Modify system settings\" permission.",
            )
        }
        return try {
            val resolver = context.contentResolver
            val current = Settings.System.getInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS,
                128,
            )
            val max = 255
            val next = (current + delta).coerceIn(1, max)
            if (Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0) ==
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
            ) {
                Settings.System.putInt(
                    resolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                )
            }
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, next)
            ActionResult.Success
        } catch (e: Exception) {
            ActionResult.Failure(
                FailureReason.INTERNAL,
                "Brightness could not be changed on this device. Some manufacturers lock the value to auto-brightness.",
            )
        }
    }
}

/** Toggles the torch through CameraManager (no camera preview is ever opened). */
@Singleton
class FlashlightHandler @Inject constructor(
    @ApplicationContext context: Context,
) : BaseHandler(context) {
    @Volatile private var isOn = false
    @Volatile private var torchId: String? = null

    override suspend fun execute(target: String?): ActionResult {
        if (context.checkSelfPermission(android.Manifest.permission.CAMERA) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return ActionResult.NeedsPermission(
                AccessId.CAMERA,
                "The flashlight action needs the Camera permission. AstraTouch only toggles the torch.",
            )
        }
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return ActionResult.Failure(FailureReason.NOT_SUPPORTED, "This device has no camera service.")
        return try {
            val id = torchId ?: cm.cameraIdList.firstOrNull { camId ->
                cm.getCameraCharacteristics(camId)
                    .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return ActionResult.Failure(
                FailureReason.NOT_SUPPORTED,
                "This device has no camera flash, so the torch cannot be used.",
            )
            torchId = id
            cm.setTorchMode(id, !isOn)
            isOn = !isOn
            ActionResult.Success
        } catch (e: SecurityException) {
            ActionResult.Failure(FailureReason.PERMISSION_MISSING, "Android blocked access to the camera flash.")
        } catch (e: Exception) {
            ActionResult.Failure(
                FailureReason.INTERNAL,
                "The torch is currently used by another app. Close the camera app and try again.",
            )
        }
    }

    fun currentState(): Boolean = isOn
}

/** Rotation toggle via WRITE_SETTINGS. */
class RotationHandler(context: Context) : BaseHandler(context) {
    override suspend fun execute(target: String?): ActionResult {
        if (!Settings.System.canWrite(context)) {
            return ActionResult.NeedsPermission(
                AccessId.WRITE_SETTINGS,
                "Auto-rotation needs the \"Modify system settings\" permission.",
            )
        }
        return try {
            val resolver = context.contentResolver
            val current = Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 1)
            Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, if (current == 1) 0 else 1)
            ActionResult.Success
        } catch (e: Exception) {
            ActionResult.Failure(
                FailureReason.INTERNAL,
                "Auto-rotation is controlled by a device policy on this device.",
            )
        }
    }
}

/** Opens a system settings page by intent action. */
class SettingsPageHandler(
    context: Context,
    private val action: String,
    private val fallbackAction: String? = null,
) : BaseHandler(context) {
    override suspend fun execute(target: String?): ActionResult {
        val first = startActivitySafely(Intent(action))
        if (first is ActionResult.Success) return first
        if (fallbackAction != null) {
            val second = startActivitySafely(Intent(fallbackAction))
            if (second is ActionResult.Success) return second
        }
        return startActivitySafely(Intent(Settings.ACTION_SETTINGS))
    }
}

/** Opens the system Bluetooth controls, using the panel on Android 13+. */
class BluetoothHandler(context: Context) : BaseHandler(context) {
    override suspend fun execute(target: String?): ActionResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val panel = startActivitySafely(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
            if (panel is ActionResult.Success) {
                return ActionResult.Partial(
                    "Android 13+ combines Wi-Fi and Bluetooth in the connectivity panel, which is now open.",
                )
            }
        }
        return startActivitySafely(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    }
}

/** Opens an installed app. */
class OpenAppHandler(context: Context, private val appsRepositoryProvider: () -> com.astratouch.app.data.repository.AppsRepository) :
    BaseHandler(context) {
    override suspend fun execute(target: String?): ActionResult {
        if (target.isNullOrBlank()) {
            return ActionResult.Failure(FailureReason.INVALID_TARGET, "No app was configured for this action.")
        }
        val repo = appsRepositoryProvider()
        return if (repo.launch(target)) {
            ActionResult.Success
        } else {
            ActionResult.Failure(
                FailureReason.INVALID_TARGET,
                "That app is not installed any more. Re-pick it in the App Launcher.",
            )
        }
    }
}

/** Opens AstraTouch's own UI at a specific destination. */
class AstraRouteHandler(
    context: Context,
    private val route: String,
) : BaseHandler(context) {
    override suspend fun execute(target: String?): ActionResult = openAstraRoute(route)
}

/** Overlay visibility control delegated to the overlay controller. */
class OverlayCommandHandler(
    context: Context,
    private val command: OverlayCommand,
    private val controller: () -> com.astratouch.app.service.overlay.OverlayController?,
) : BaseHandler(context) {
    override suspend fun execute(target: String?): ActionResult {
        val c = controller() ?: return ActionResult.Failure(
            FailureReason.SERVICE_UNAVAILABLE,
            "The floating overlay service is not running.",
        )
        return when (command) {
            OverlayCommand.HIDE -> { c.hideBubble(); ActionResult.Success }
            OverlayCommand.SHOW -> { c.showBubble(); ActionResult.Success }
            OverlayCommand.TOGGLE -> { c.toggleBubble(); ActionResult.Success }
            OverlayCommand.OPEN_MENU -> { c.toggleMenu(); ActionResult.Success }
            OverlayCommand.STOP -> { c.stopService(); ActionResult.Success }
        }
    }
}

enum class OverlayCommand { HIDE, SHOW, TOGGLE, OPEN_MENU, STOP }

/** Opens an app's system details/settings page. */
class AppDetailsHandler(context: Context) : BaseHandler(context) {
    override suspend fun execute(target: String?): ActionResult {
        if (target.isNullOrBlank()) {
            return ActionResult.Failure(FailureReason.INVALID_TARGET, "No app was configured for this action.")
        }
        return startActivitySafely(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:$target")),
        )
    }
}

/** Opens the AstraTouch app's own settings page in Android Settings. */
class AstraAppSettingsHandler(context: Context) : BaseHandler(context) {
    override suspend fun execute(target: String?): ActionResult =
        startActivitySafely(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:${context.packageName}")),
        )
}

/**
 * Captures a screenshot using the accessibility service's takeScreenshot API
 * (API 30+). On API 29 it reports the honest limitation and points at the
 * MediaProjection-based recorder instead.
 */
class ScreenshotHandler(
    context: Context,
    private val onCaptured: suspend (android.graphics.Bitmap) -> ActionResult,
) : BaseHandler(context) {
    override suspend fun execute(target: String?): ActionResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return ActionResult.Unsupported(
                "Android 10 does not allow apps to take screenshots without a screen-capture consent dialog. " +
                    "Use Screen recording (MediaProjection) on this version, or the system screenshot shortcut.",
            )
        }
        val service = requireAccessibility() ?: return AccessibilityUnavailable()
        val bitmap = withTimeoutOrNull(8_000) {
            suspendCancellableCoroutine<android.graphics.Bitmap?> { cont ->
                service.takeScreenshot(
                    android.view.Display.DEFAULT_DISPLAY,
                    context.mainExecutor,
                    object : AccessibilityService.TakeScreenshotCallback {
                        override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                            val hardware = android.graphics.Bitmap.wrapHardwareBuffer(
                                screenshot.hardwareBuffer,
                                screenshot.colorSpace,
                            )
                            val copy = hardware?.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                            screenshot.hardwareBuffer.close()
                            if (cont.isActive) cont.resume(copy)
                        }

                        override fun onFailure(errorCode: Int) {
                            if (cont.isActive) cont.resume(null)
                        }
                    },
                )
            }
        }
        return when {
            bitmap == null -> ActionResult.Failure(
                FailureReason.NOT_SUPPORTED,
                "Android refused the screenshot. Secure screens (banking, DRM video) block capture by design.",
            )
            else -> onCaptured(bitmap)
        }
    }
}

/** Launch a shortcut published by an app (long-press menu), where supported. */
class AppShortcutHandler(
    context: Context,
    private val appsRepositoryProvider: () -> com.astratouch.app.data.repository.AppsRepository,
) : BaseHandler(context) {
    override suspend fun execute(target: String?): ActionResult {
        // target format: "packageName|shortcutId"
        val parts = target?.split('|', limit = 2).orEmpty()
        if (parts.size != 2) {
            return ActionResult.Failure(FailureReason.INVALID_TARGET, "This shortcut is not configured.")
        }
        return if (appsRepositoryProvider().launch(parts[0])) ActionResult.Success else ActionResult.Failure(
            FailureReason.NOT_SUPPORTED,
            "That app shortcut could not be launched. Shortcuts are app-controlled and may change between updates.",
        )
    }
}

/** Fake-free clipboard/notes/calculator/timer openers are routes into Astra. */
class AstraToolHandler(
    context: Context,
    private val toolRoute: String,
) : BaseHandler(context) {
    override suspend fun execute(target: String?): ActionResult = openAstraRoute(toolRoute)
}
