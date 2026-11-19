package com.astratouch.app.service.action

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.astratouch.app.data.repository.AppsRepository
import com.astratouch.app.data.repository.RecentActionRepository
import com.astratouch.app.domain.model.AccessId
import com.astratouch.app.domain.model.ActionCategory
import com.astratouch.app.domain.model.ActionIds
import com.astratouch.app.service.overlay.OverlayController
import com.astratouch.app.util.NavRoutes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The System Action Engine (Part 7).
 *
 * Every action declares its metadata, permissions, API availability and
 * capability state. Execution always runs with a timeout and always returns a
 * user-facing explanation on failure — there are no silent no-ops.
 */
@Singleton
class ActionRegistry @Inject constructor(
    @ApplicationContext private val context: Context,
    private val capabilities: DeviceCapabilities,
    private val appsRepository: AppsRepository,
    private val recentActionRepository: RecentActionRepository,
    private val flashlight: FlashlightHandler,
    private val screenshotHandlerProvider: ScreenshotSaver,
) {
    private val actions: LinkedHashMap<String, RegisteredAction> = LinkedHashMap()

    private val _executions = MutableStateFlow<ActionExecution?>(null)
    val lastExecution: StateFlow<ActionExecution?> = _executions.asStateFlow()

    init {
        registerDefaults()
    }

    fun all(): List<AstraAction> = actions.values.map { it.action }

    fun byId(id: String): AstraAction? = actions[id]?.action

    fun byCategory(category: ActionCategory): List<AstraAction> =
        all().filter { it.category == category }

    fun search(query: String): List<AstraAction> {
        if (query.isBlank()) return all()
        val q = query.trim().lowercase()
        return all().filter { action ->
            action.name.lowercase().contains(q) ||
                action.description.lowercase().contains(q) ||
                action.searchKeywords.any { it.contains(q) }
        }
    }

    /** Current capability for one action, recomputed on demand (never cached stale). */
    fun capabilityOf(id: String): CapabilityState {
        val registered = actions[id] ?: return CapabilityState.UNSUPPORTED_OS
        if (!registered.action.isApiSupported(capabilities.sdkInt)) {
            return CapabilityState.UNSUPPORTED_OS
        }
        return registered.capabilityCheck()
    }

    fun capabilitySnapshot(): Map<String, CapabilityState> =
        actions.keys.associateWith { capabilityOf(it) }

    /** Actions that are actually runnable right now (used to hide dead buttons). */
    fun availableActions(): List<AstraAction> =
        all().filter { capabilityOf(it.id) == CapabilityState.AVAILABLE }

    /**
     * Executes an action with a hard timeout. Records the usage for the radial
     * "recently used" list and publishes the result to [lastExecution].
     */
    suspend fun execute(id: String, target: String? = null): ActionResult {
        val registered = actions[id] ?: return ActionResult.Failure(
            FailureReason.INVALID_TARGET,
            "Unknown action \"$id\". It may come from a newer AstraTouch version.",
        )

        if (!registered.action.isApiSupported(capabilities.sdkInt)) {
            return ActionResult.Unsupported(
                "${registered.action.name} requires Android " +
                    "${apiToAndroidName(registered.action.minApi)} or newer.",
            )
        }

        val capability = registered.capabilityCheck()
        if (capability == CapabilityState.NEEDS_PERMISSION) {
            val missing = registered.action.requiredAccess.firstOrNull { !capabilities.accessState(it) }
            return ActionResult.NeedsPermission(
                missing ?: AccessId.OVERLAY,
                "${registered.action.name} needs an extra permission before it can run.",
            )
        }
        if (!capability.isAvailable) {
            return ActionResult.Failure(
                FailureReason.NOT_SUPPORTED,
                "${registered.action.name}: ${capability.label}.",
            )
        }

        val result = withTimeoutOrNull(EXECUTION_TIMEOUT_MS) {
            runCatching { registered.handler.execute(target) }
                .getOrElse { e ->
                    ActionResult.Failure(
                        FailureReason.INTERNAL,
                        "Unexpected error in ${registered.action.name}: ${e.message ?: e::class.simpleName}",
                    )
                }
        } ?: ActionResult.Failure(
            FailureReason.TIMEOUT,
            "${registered.action.name} took longer than ${EXECUTION_TIMEOUT_MS / 1000}s and was stopped.",
        )

        if (result is ActionResult.Success) {
            runCatching { recentActionRepository.record(id) }
        }
        _executions.value = ActionExecution(id, result, System.currentTimeMillis())
        return result
    }

    fun clearLastExecution() { _executions.value = null }

    // ---------------------------------------------------------------- registry

    private fun register(action: AstraAction, handler: ActionHandler, capability: () -> CapabilityState) {
        actions[action.id] = RegisteredAction(action, handler, capability)
    }

    private fun needs(access: AccessId): () -> CapabilityState = {
        if (capabilities.accessState(access)) CapabilityState.AVAILABLE else CapabilityState.NEEDS_PERMISSION
    }

    private fun always(): CapabilityState = CapabilityState.AVAILABLE

    private fun registerDefaults() {
        val overlay = { OverlayController.instance }
        fun nav(
            id: String,
            name: String,
            description: String,
            icon: String,
            globalAction: Int,
            keywords: List<String> = emptyList(),
        ) = register(
            AstraAction(
                id = id,
                name = name,
                description = description,
                category = ActionCategory.NAVIGATION,
                iconKey = icon,
                requiredAccess = setOf(AccessId.ACCESSIBILITY),
                searchKeywords = keywords,
            ),
            GlobalActionHandler(context, globalAction, name),
            needs(AccessId.ACCESSIBILITY),
        )

        nav(
            ActionIds.HOME, "Home", "Go to the home screen.", "ic_home",
            android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME, listOf("launcher", "desktop"),
        )
        nav(
            ActionIds.BACK, "Back", "Press the system Back button.", "ic_back",
            android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK, listOf("return", "previous"),
        )
        nav(
            ActionIds.RECENTS, "Recents", "Open the recent-apps overview.", "ic_recents",
            android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS, listOf("task switcher"),
        )

        register(
            AstraAction(
                ActionIds.NOTIFICATIONS, "Notifications", "Pull down the notification shade.", "ic_notifications",
                ActionCategory.NAVIGATION, requiredAccess = setOf(AccessId.ACCESSIBILITY),
            ),
            NotificationsHandler(context),
            needs(AccessId.ACCESSIBILITY),
        )
        register(
            AstraAction(
                ActionIds.QUICK_SETTINGS, "Quick settings", "Expand the system Quick Settings panel.", "ic_tune",
                ActionCategory.NAVIGATION, requiredAccess = setOf(AccessId.ACCESSIBILITY),
            ),
            QuickSettingsHandler(context),
            needs(AccessId.ACCESSIBILITY),
        )
        register(
            AstraAction(
                ActionIds.LOCK_SCREEN, "Lock screen", "Lock the device immediately.", "ic_lock",
                ActionCategory.NAVIGATION, requiredAccess = setOf(AccessId.ACCESSIBILITY), minApi = 29,
            ),
            LockScreenHandler(context, capabilities),
            { if (capabilities.hasDeviceLock() || capabilities.hasAccessibility()) CapabilityState.AVAILABLE else CapabilityState.NEEDS_PERMISSION },
        )
        register(
            AstraAction(
                ActionIds.OPEN_RADIAL_MENU, "Open radial menu", "Show the AstraTouch command menu.", "ic_radial",
                ActionCategory.NAVIGATION,
            ),
            OverlayCommandHandler(context, OverlayCommand.OPEN_MENU, overlay),
            { if (capabilities.hasOverlayPermission()) CapabilityState.AVAILABLE else CapabilityState.NEEDS_PERMISSION },
        )

        // ---- Device controls
        register(
            AstraAction(ActionIds.VOLUME_UP, "Volume up", "Raise media volume one step.", "ic_volume_up", ActionCategory.DEVICE),
            VolumeHandler(context, android.media.AudioManager.ADJUST_RAISE),
            ::always,
        )
        register(
            AstraAction(ActionIds.VOLUME_DOWN, "Volume down", "Lower media volume one step.", "ic_volume_down", ActionCategory.DEVICE),
            VolumeHandler(context, android.media.AudioManager.ADJUST_LOWER),
            ::always,
        )
        register(
            AstraAction(ActionIds.VOLUME_PANEL, "Volume panel", "Show the system volume UI.", "ic_volume", ActionCategory.DEVICE),
            VolumeHandler(context, android.media.AudioManager.ADJUST_SAME, showPanel = true),
            ::always,
        )
        register(
            AstraAction(
                ActionIds.BRIGHTNESS_UP, "Brightness up", "Increase screen brightness.", "ic_brightness_up",
                ActionCategory.DEVICE, requiredAccess = setOf(AccessId.WRITE_SETTINGS),
            ),
            BrightnessHandler(context, delta = 24),
            needs(AccessId.WRITE_SETTINGS),
        )
        register(
            AstraAction(
                ActionIds.BRIGHTNESS_DOWN, "Brightness down", "Decrease screen brightness.", "ic_brightness_down",
                ActionCategory.DEVICE, requiredAccess = setOf(AccessId.WRITE_SETTINGS),
            ),
            BrightnessHandler(context, delta = -24),
            needs(AccessId.WRITE_SETTINGS),
        )
        register(
            AstraAction(
                ActionIds.FLASHLIGHT, "Flashlight", "Toggle the camera torch.", "ic_flashlight",
                ActionCategory.DEVICE, requiredAccess = setOf(AccessId.CAMERA),
            ),
            flashlight,
            {
                when {
                    !capabilities.hasFlashlight() -> CapabilityState.NEEDS_DEVICE_SUPPORT
                    !capabilities.hasCameraPermission() -> CapabilityState.NEEDS_PERMISSION
                    else -> CapabilityState.AVAILABLE
                }
            },
        )
        register(
            AstraAction(
                ActionIds.ROTATION_TOGGLE, "Auto-rotation", "Toggle automatic screen rotation.", "ic_rotate",
                ActionCategory.DEVICE, requiredAccess = setOf(AccessId.WRITE_SETTINGS),
            ),
            RotationHandler(context),
            needs(AccessId.WRITE_SETTINGS),
        )

        data class SettingsLink(val id: String, val name: String, val icon: String, val action: String, val fallback: String? = null)

        listOf(
            SettingsLink(ActionIds.SOUND_SETTINGS, "Sound settings", "ic_volume", Settings.ACTION_SOUND_SETTINGS),
            SettingsLink(ActionIds.DISPLAY_SETTINGS, "Display settings", "ic_brightness_up", Settings.ACTION_DISPLAY_SETTINGS),
            SettingsLink(ActionIds.WIFI_SETTINGS, "Wi-Fi settings", "ic_wifi", Settings.ACTION_WIFI_SETTINGS),
            SettingsLink(ActionIds.AIRPLANE_SETTINGS, "Airplane mode settings", "ic_plane", Settings.ACTION_AIRPLANE_MODE_SETTINGS),
            SettingsLink(ActionIds.BATTERY_SETTINGS, "Battery settings", "ic_battery", Settings.ACTION_BATTERY_SAVER_SETTINGS),
        ).forEach { link ->
            register(
                AstraAction(link.id, link.name, "Open the Android ${link.name.lowercase()} screen.", link.icon, ActionCategory.DEVICE),
                SettingsPageHandler(context, link.action, link.fallback),
                ::always,
            )
        }
        register(
            AstraAction(ActionIds.BLUETOOTH_SETTINGS, "Bluetooth", "Open Bluetooth controls.", "ic_bluetooth", ActionCategory.DEVICE),
            BluetoothHandler(context),
            ::always,
        )
        register(
            AstraAction(ActionIds.INTERNET_PANEL, "Internet panel", "Open the system Wi-Fi / connectivity panel.", "ic_wifi", ActionCategory.DEVICE, minApi = 29),
            SettingsPageHandler(
                context,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_WIFI_SETTINGS,
                Settings.ACTION_WIFI_SETTINGS,
            ),
            ::always,
        )
        register(
            AstraAction(
                ActionIds.SOUND_PANEL, "Sound panel", "Open the system sound/volume panel.", "ic_volume", ActionCategory.DEVICE,
                minApi = 31,
            ),
            SettingsPageHandler(context, Settings.Panel.ACTION_VOLUME, Settings.ACTION_SOUND_SETTINGS),
            { if (capabilities.isAtLeastS) CapabilityState.AVAILABLE else CapabilityState.UNSUPPORTED_OS },
        )
        register(
            AstraAction(
                ActionIds.BLUETOOTH_PANEL, "Bluetooth panel", "Open the system Bluetooth panel.", "ic_bluetooth",
                ActionCategory.DEVICE, minApi = 33,
            ),
            SettingsPageHandler(context, Settings.Panel.ACTION_INTERNET_CONNECTIVITY, Settings.ACTION_BLUETOOTH_SETTINGS),
            { if (capabilities.isAtLeastT) CapabilityState.AVAILABLE else CapabilityState.UNSUPPORTED_OS },
        )
        register(
            AstraAction(
                ActionIds.DND_PANEL, "Do Not Disturb", "Open the Do Not Disturb settings page.", "ic_moon",
                ActionCategory.DEVICE, minApi = 29,
            ),
            SettingsPageHandler(context, Settings.ACTION_ZEN_MODE_SETTINGS, Settings.ACTION_SOUND_SETTINGS),
            ::always,
        )

        // ---- Capture
        register(
            AstraAction(
                ActionIds.SCREENSHOT, "Screenshot", "Capture the screen (Android 11+).", "ic_camera",
                ActionCategory.CAPTURE, requiredAccess = setOf(AccessId.ACCESSIBILITY), minApi = 30,
                searchKeywords = listOf("capture", "snap", "screen"),
            ),
            ScreenshotHandler(context) { bitmap -> screenshotHandlerProvider.save(bitmap) },
            {
                when {
                    !capabilities.isAtLeastR -> CapabilityState.UNSUPPORTED_OS
                    !capabilities.hasAccessibility() -> CapabilityState.NEEDS_PERMISSION
                    else -> CapabilityState.AVAILABLE
                }
            },
        )
        register(
            AstraAction(
                ActionIds.SCREEN_RECORD, "Screen recording", "Record the screen with MediaProjection.", "ic_record",
                ActionCategory.CAPTURE, searchKeywords = listOf("record", "video", "capture"),
            ),
            AstraRouteHandler(context, NavRoutes.SCREENSHOTS),
            ::always,
        )
        register(
            AstraAction(
                ActionIds.CAPTURE_LAST, "Open Screenshot Studio", "Review, crop and annotate captures.", "ic_image",
                ActionCategory.CAPTURE,
            ),
            AstraRouteHandler(context, NavRoutes.SCREENSHOTS),
            ::always,
        )
        register(
            AstraAction(ActionIds.CROP, "Crop last capture", "Open the crop tool for the newest screenshot.", "ic_crop", ActionCategory.CAPTURE),
            AstraRouteHandler(context, NavRoutes.SCREENSHOTS),
            ::always,
        )
        register(
            AstraAction(ActionIds.ANNOTATE, "Annotate last capture", "Draw on the newest screenshot and share it.", "ic_draw", ActionCategory.CAPTURE),
            AstraRouteHandler(context, NavRoutes.SCREENSHOTS),
            ::always,
        )

        // ---- Apps
        register(
            AstraAction(
                ActionIds.OPEN_APP, "Open app", "Launch an installed application.", "ic_apps",
                ActionCategory.APP, supportsTarget = true,
            ),
            OpenAppHandler(context) { appsRepository },
            ::always,
        )
        register(
            AstraAction(
                ActionIds.APP_DETAILS, "App info", "Open an app's system information page.", "ic_info",
                ActionCategory.APP, supportsTarget = true,
            ),
            AppDetailsHandler(context),
            ::always,
        )
        register(
            AstraAction(ActionIds.APP_SETTINGS, "AstraTouch app info", "Open AstraTouch in Android Settings.", "ic_settings", ActionCategory.APP),
            AstraAppSettingsHandler(context),
            ::always,
        )
        register(
            AstraAction(
                ActionIds.APP_SHORTCUT, "App shortcut", "Launch a shortcut published by an app.", "ic_shortcut",
                ActionCategory.APP, supportsTarget = true, minApi = 29,
            ),
            AppShortcutHandler(context) { appsRepository },
            { if (capabilities.isAtLeastR) CapabilityState.AVAILABLE else CapabilityState.UNSUPPORTED_OS },
        )
        register(
            AstraAction(ActionIds.APP_SELECTOR, "Choose an app", "Pick an app to launch from this menu entry.", "ic_search", ActionCategory.APP),
            AstraRouteHandler(context, NavRoutes.APPS),
            ::always,
        )

        // ---- Productivity
        register(
            AstraAction(ActionIds.OPEN_CALCULATOR, "Calculator", "Open the AstraTouch calculator.", "ic_calc", ActionCategory.PRODUCTIVITY),
            AstraRouteHandler(context, NavRoutes.CALCULATOR),
            ::always,
        )
        register(
            AstraAction(ActionIds.OPEN_NOTES, "Notes", "Open your local notes.", "ic_note", ActionCategory.PRODUCTIVITY),
            AstraRouteHandler(context, NavRoutes.NOTES),
            ::always,
        )
        register(
            AstraAction(ActionIds.OPEN_TIMER, "Timer", "Open the AstraTouch timer.", "ic_timer", ActionCategory.PRODUCTIVITY),
            AstraRouteHandler(context, NavRoutes.TIMER),
            ::always,
        )
        register(
            AstraAction(ActionIds.OPEN_CLIPBOARD, "Clipboard", "Open your clipboard history.", "ic_clipboard", ActionCategory.PRODUCTIVITY),
            AstraRouteHandler(context, NavRoutes.CLIPBOARD),
            ::always,
        )

        // ---- AstraTouch routes
        listOf(
            Triple(ActionIds.OPEN_HOME, NavRoutes.HOME, "Dashboard"),
            Triple(ActionIds.OPEN_SETTINGS, NavRoutes.SETTINGS, "General settings"),
            Triple(ActionIds.OPEN_PERMISSIONS, NavRoutes.PERMISSIONS, "Permission center"),
            Triple(ActionIds.OPEN_THEME_STUDIO, NavRoutes.THEMES, "Theme studio"),
            Triple(ActionIds.OPEN_PROFILES, NavRoutes.PROFILES, "Profile manager"),
            Triple(ActionIds.OPEN_MACROS, NavRoutes.MACROS, "Macro manager"),
            Triple(ActionIds.OPEN_GESTURES, NavRoutes.GESTURES, "Gesture manager"),
            Triple(ActionIds.OPEN_LAB, NavRoutes.LAB, "Experimental lab"),
            Triple(ActionIds.OPEN_SCREENSHOTS, NavRoutes.SCREENSHOTS, "Screenshot studio"),
            Triple(ActionIds.OPEN_CLIPBOARD_MANAGER, NavRoutes.CLIPBOARD, "Clipboard manager"),
            Triple(ActionIds.OPEN_ACTION_LIBRARY, NavRoutes.ACTIONS, "Action library"),
            Triple(ActionIds.OPEN_QUICK_CONTROLS, NavRoutes.QUICK_CONTROLS, "Quick control center"),
            Triple(ActionIds.OPEN_APP_LAUNCHER, NavRoutes.APPS, "App launcher"),
            Triple(ActionIds.OPEN_GENERAL_SETTINGS, NavRoutes.SETTINGS, "Settings"),
            Triple(ActionIds.OPEN_ABOUT, NavRoutes.ABOUT, "About AstraTouch"),
            Triple(ActionIds.OPEN_PRIVACY_CENTER, NavRoutes.PRIVACY, "Privacy center"),
        ).forEach { (id, route, title) ->
            register(
                AstraAction(id, title, "Open $title.", "ic_arrow_right", ActionCategory.ASTRA, isAstraInternal = true),
                AstraRouteHandler(context, route),
                ::always,
            )
        }
        register(
            AstraAction(ActionIds.HIDE_BUBBLE, "Hide bubble", "Temporarily hide the floating bubble.", "ic_eye_off", ActionCategory.ASTRA),
            OverlayCommandHandler(context, OverlayCommand.HIDE, overlay),
            { if (capabilities.hasOverlayPermission()) CapabilityState.AVAILABLE else CapabilityState.NEEDS_PERMISSION },
        )
        register(
            AstraAction(ActionIds.SHOW_BUBBLE, "Show bubble", "Show the floating bubble again.", "ic_eye", ActionCategory.ASTRA),
            OverlayCommandHandler(context, OverlayCommand.SHOW, overlay),
            { if (capabilities.hasOverlayPermission()) CapabilityState.AVAILABLE else CapabilityState.NEEDS_PERMISSION },
        )
        register(
            AstraAction(ActionIds.STOP_OVERLAY, "Stop floating controls", "Stop the overlay service entirely.", "ic_stop", ActionCategory.ASTRA),
            OverlayCommandHandler(context, OverlayCommand.STOP, overlay),
            { if (capabilities.hasOverlayPermission()) CapabilityState.AVAILABLE else CapabilityState.NEEDS_PERMISSION },
        )
    }

    private fun apiToAndroidName(api: Int): String = when {
        api <= 29 -> "Android 10"
        api == 30 -> "Android 11"
        api == 31 -> "Android 12"
        api == 32 -> "Android 12L"
        api == 33 -> "Android 13"
        api == 34 -> "Android 14"
        api == 35 -> "Android 15"
        else -> "Android 16"
    }

    companion object {
        const val EXECUTION_TIMEOUT_MS = 12_000L
    }
}

data class ActionExecution(
    val actionId: String,
    val result: ActionResult,
    val at: Long,
)

/** Contract for persisting a captured screenshot; implemented in the data layer. */
interface ScreenshotSaver {
    suspend fun save(bitmap: android.graphics.Bitmap): ActionResult
}
