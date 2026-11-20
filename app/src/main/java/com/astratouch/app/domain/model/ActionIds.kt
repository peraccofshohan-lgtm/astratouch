package com.astratouch.app.domain.model

/**
 * Stable identifiers for every action the System Action Engine knows about.
 *
 * Declared as constants (not an enum) so that persisted configurations keep
 * working when new actions are appended to the registry.
 */
object ActionIds {
    // Navigation
    const val HOME = "nav.home"
    const val BACK = "nav.back"
    const val RECENTS = "nav.recents"
    const val NOTIFICATIONS = "nav.notifications"
    const val QUICK_SETTINGS = "nav.quick_settings"
    const val LOCK_SCREEN = "nav.lock_screen"
    const val OPEN_RADIAL_MENU = "nav.radial_menu"

    // Device
    const val VOLUME_UP = "device.volume_up"
    const val VOLUME_DOWN = "device.volume_down"
    const val VOLUME_PANEL = "device.volume_panel"
    const val BRIGHTNESS_UP = "device.brightness_up"
    const val BRIGHTNESS_DOWN = "device.brightness_down"
    const val BRIGHTNESS_DIALOG = "device.brightness_dialog"
    const val FLASHLIGHT = "device.flashlight"
    const val ROTATION_TOGGLE = "device.rotation_toggle"
    const val SOUND_SETTINGS = "device.sound_settings"
    const val DISPLAY_SETTINGS = "device.display_settings"
    const val WIFI_SETTINGS = "device.wifi_settings"
    const val BLUETOOTH_SETTINGS = "device.bluetooth_settings"
    const val AIRPLANE_SETTINGS = "device.airplane_settings"
    const val BATTERY_SETTINGS = "device.battery_settings"
    const val SOUND_PANEL = "device.sound_panel"
    const val BLUETOOTH_PANEL = "device.bluetooth_panel"
    const val INTERNET_PANEL = "device.internet_panel"
    const val DND_PANEL = "device.dnd_panel"

    // Capture
    const val SCREENSHOT = "capture.screenshot"
    const val SCREEN_RECORD = "capture.screen_record"
    const val CAPTURE_LAST = "capture.open_last"
    const val CROP = "capture.crop"
    const val ANNOTATE = "capture.annotate"
    const val SHARE_LAST = "capture.share_last"

    // Apps
    const val OPEN_APP = "app.open"
    const val APP_DETAILS = "app.details"
    const val APP_SHORTCUT = "app.shortcut"
    const val APP_SETTINGS = "app.settings"
    const val APP_SELECTOR = "app.select"

    // Productivity
    const val OPEN_CALCULATOR = "productivity.calculator"
    const val OPEN_NOTES = "productivity.notes"
    const val OPEN_TIMER = "productivity.timer"
    const val OPEN_CLIPBOARD = "productivity.clipboard"

    // AstraTouch
    const val OPEN_SETTINGS = "astra.settings"
    const val OPEN_HOME = "astra.home"
    const val OPEN_PERMISSIONS = "astra.permissions"
    const val OPEN_THEME_STUDIO = "astra.theme"
    const val OPEN_PROFILES = "astra.profiles"
    const val OPEN_MACROS = "astra.macros"
    const val OPEN_GESTURES = "astra.gestures"
    const val OPEN_LAB = "astra.lab"
    const val OPEN_SCREENSHOTS = "astra.screenshots"
    const val OPEN_CLIPBOARD_MANAGER = "astra.clipboard_manager"
    const val OPEN_ACTION_LIBRARY = "astra.action_library"
    const val OPEN_QUICK_CONTROLS = "astra.quick_controls"
    const val OPEN_APP_LAUNCHER = "astra.app_launcher"
    const val OPEN_GENERAL_SETTINGS = "astra.general_settings"
    const val OPEN_ABOUT = "astra.about"
    const val OPEN_PRIVACY_CENTER = "astra.privacy_center"
    const val HIDE_BUBBLE = "astra.hide_bubble"
    const val SHOW_BUBBLE = "astra.show_bubble"
    const val STOP_OVERLAY = "astra.stop_overlay"
}

/** Categories used to group actions in the library UI and radial editor. */
enum class ActionCategory(val label: String) {
    NAVIGATION("Navigation"),
    DEVICE("Device"),
    CAPTURE("Capture"),
    APP("Applications"),
    PRODUCTIVITY("Productivity"),
    ASTRA("AstraTouch"),
    ;

    companion object {
        fun fromName(name: String?): ActionCategory =
            entries.firstOrNull { it.name == name } ?: NAVIGATION
    }
}
