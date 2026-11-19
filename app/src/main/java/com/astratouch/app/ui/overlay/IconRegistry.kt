package com.astratouch.app.ui.overlay

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecentActors
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Rotate90DegreesCcw
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Stable key -> icon map. Configuration rows store the *key*, never a drawable
 * id, so icons survive app updates and R8 resource shrinking.
 */
object IconRegistry {
    private val icons: Map<String, ImageVector> = mapOf(
        "ic_star" to Icons.Filled.Star,
        "ic_home" to Icons.Filled.Home,
        "ic_back" to Icons.Filled.ArrowBack,
        "ic_forward" to Icons.Filled.ArrowForward,
        "ic_arrow_left" to Icons.Filled.KeyboardArrowLeft,
        "ic_recents" to Icons.Filled.RecentActors,
        "ic_notifications" to Icons.Filled.Notifications,
        "ic_tune" to Icons.Filled.Tune,
        "ic_lock" to Icons.Filled.Lock,
        "ic_radial" to Icons.Filled.GridView,
        "ic_volume_up" to Icons.Filled.VolumeUp,
        "ic_volume_down" to Icons.Filled.VolumeDown,
        "ic_volume" to Icons.Filled.VolumeUp,
        "ic_brightness_up" to Icons.Filled.BrightnessHigh,
        "ic_brightness_down" to Icons.Filled.BrightnessLow,
        "ic_flashlight" to Icons.Filled.FlashlightOn,
        "ic_rotate" to Icons.Filled.ScreenRotation,
        "ic_rotate_ccw" to Icons.Filled.Rotate90DegreesCcw,
        "ic_wifi" to Icons.Filled.Wifi,
        "ic_bluetooth" to Icons.Filled.Bluetooth,
        "ic_plane" to Icons.Filled.Flight,
        "ic_battery" to Icons.Filled.BatteryChargingFull,
        "ic_moon" to Icons.Filled.DarkMode,
        "ic_camera" to Icons.Filled.CameraAlt,
        "ic_record" to Icons.Filled.PlayArrow,
        "ic_image" to Icons.Filled.Image,
        "ic_crop" to Icons.Filled.Crop,
        "ic_draw" to Icons.Filled.Edit,
        "ic_apps" to Icons.Filled.GridView,
        "ic_shortcut" to Icons.Filled.Bolt,
        "ic_search" to Icons.Filled.Search,
        "ic_calc" to Icons.Filled.Calculate,
        "ic_note" to Icons.Filled.Edit,
        "ic_timer" to Icons.Filled.Timer,
        "ic_clipboard" to Icons.Filled.ContentCopy,
        "ic_settings" to Icons.Filled.Settings,
        "ic_info" to Icons.Filled.Info,
        "ic_arrow_right" to Icons.Filled.ArrowForward,
        "ic_eye" to Icons.Filled.Visibility,
        "ic_eye_off" to Icons.Filled.VisibilityOff,
        "ic_stop" to Icons.Filled.Stop,
        "ic_close" to Icons.Filled.Close,
        "ic_add" to Icons.Filled.Add,
        "ic_delete" to Icons.Filled.Delete,
        "ic_save" to Icons.Filled.Save,
        "ic_share" to Icons.Filled.Share,
        "ic_refresh" to Icons.Filled.Refresh,
        "ic_menu" to Icons.Filled.Menu,
        "ic_expand_less" to Icons.Filled.ExpandLess,
        "ic_expand_more" to Icons.Filled.ExpandMore,
        "ic_pause" to Icons.Filled.Pause,
        "ic_play" to Icons.Filled.PlayArrow,
        "ic_folder" to Icons.Filled.Folder,
        "ic_storage" to Icons.Filled.Storage,
        "ic_vibration" to Icons.Filled.Vibration,
        "ic_work" to Icons.Filled.Work,
        "ic_book" to Icons.Filled.Book,
        "ic_game" to Icons.Filled.SportsEsports,
        "ic_media" to Icons.Filled.PlayArrow,
        "ic_bolt" to Icons.Filled.Bolt,
        "ic_gesture" to Icons.Filled.Gesture,
        "ic_split" to Icons.Filled.CallSplit,
        "ic_loop" to Icons.Filled.Loop,
    )

    fun get(key: String): ImageVector = icons[key] ?: Icons.Filled.Star

    /** Every key, exposed for icon pickers. */
    val keys: List<String> = icons.keys.toList()
}
