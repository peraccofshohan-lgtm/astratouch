package com.astratouch.app.domain.model

/** Permission identifiers AstraTouch tracks in the Permission Center (Part 14). */
enum class AccessId(val label: String) {
    OVERLAY("Display over other apps"),
    ACCESSIBILITY("Accessibility service"),
    WRITE_SETTINGS("Modify system settings"),
    NOTIFICATIONS("Notifications"),
    CAMERA("Camera"),
    BATTERY_OPTIMIZATION("Ignore battery optimisation"),
    ;

    companion object {
        fun fromName(name: String?): AccessId? = entries.firstOrNull { it.name == name }
    }
}

enum class AccessState(val label: String) {
    GRANTED("Granted"),
    DENIED("Not granted"),
    PERMANENTLY_DENIED("Blocked — open settings"),
    RESTRICTED("Restricted by system"),
    NOT_REQUIRED("Not required"),
    ;

    val isGranted: Boolean get() = this == GRANTED
    val isBlocked: Boolean get() = this == PERMANENTLY_DENIED || this == RESTRICTED
}

/**
 * Full descriptor for one special access: why it exists, which features depend
 * on it, how to reach the right settings screen, and what happens on denial.
 */
data class AccessDescriptor(
    val id: AccessId,
    val title: String,
    val purpose: String,
    val dependentFeatures: List<String>,
    val fallback: String,
    val optional: Boolean,
)

data class AccessStatus(
    val descriptor: AccessDescriptor,
    val state: AccessState,
    val lastCheckedAt: Long = 0L,
)

data class PermissionSnapshot(
    val statuses: List<AccessStatus>,
) {
    fun stateOf(id: AccessId): AccessState =
        statuses.firstOrNull { it.descriptor.id == id }?.state ?: AccessState.NOT_REQUIRED

    fun isGranted(id: AccessId): Boolean = stateOf(id).isGranted

    /** Requirements for the core floating feature set. */
    val overlayReady: Boolean get() = isGranted(AccessId.OVERLAY)
    val accessibilityReady: Boolean get() = isGranted(AccessId.ACCESSIBILITY)
    val canCaptureScreen: Boolean get() = accessibilityReady
    val canControlBrightness: Boolean get() = isGranted(AccessId.WRITE_SETTINGS)

    val requiredGrantedCount: Int
        get() = statuses.count { !it.descriptor.optional && it.state.isGranted }

    val requiredTotal: Int
        get() = statuses.count { !it.descriptor.optional }

    companion object {
        val EMPTY = PermissionSnapshot(emptyList())
    }
}

/** Canonical catalogue used by the UI, onboarding and the privacy audit. */
object AccessCatalogue {
    val all: List<AccessDescriptor> = listOf(
        AccessDescriptor(
            id = AccessId.OVERLAY,
            title = "Display over other apps",
            purpose = "AstraTouch draws its floating bubble and radial menu above other apps. Android only allows this with the \"Display over other apps\" special access.",
            dependentFeatures = listOf("Floating bubble", "Radial command center", "Edge handle and edge panel"),
            fallback = "Without it, AstraTouch still works as a normal app: you can configure everything and launch actions from the in-app Quick Control Center.",
            optional = false,
        ),
        AccessDescriptor(
            id = AccessId.ACCESSIBILITY,
            title = "AstraTouch accessibility service",
            purpose = "Android performs Home, Back, Recents, the notification shade, Quick Settings and screenshot capture on behalf of an accessibility service. AstraTouch uses it only for actions you trigger yourself.",
            dependentFeatures = listOf(
                "Home / Back / Recents",
                "Notifications and Quick Settings panels",
                "Screenshot capture and screen recording setup",
                "Lock screen",
            ),
            fallback = "Without it, only AstraTouch's own screens, app launching, opening system settings pages, volume keys and the flashlight remain available.",
            optional = false,
        ),
        AccessDescriptor(
            id = AccessId.WRITE_SETTINGS,
            title = "Modify system settings",
            purpose = "Brightness and auto-rotation are system settings. Android requires this special access to change them from an app.",
            dependentFeatures = listOf("Brightness up / down", "Auto-rotation toggle"),
            fallback = "Those two actions show a clear explanation and open the relevant settings page instead.",
            optional = true,
        ),
        AccessDescriptor(
            id = AccessId.NOTIFICATIONS,
            title = "Notifications",
            purpose = "The floating bubble must run as a foreground service, so Android shows an ongoing notification. Notifications also surface macro results and timer alerts.",
            dependentFeatures = listOf("Ongoing \"AstraTouch is active\" status", "Timer alerts", "Macro results"),
            fallback = "Everything still works on Android 12 and below; on Android 13+ the ongoing notification is silent but the bubble keeps running.",
            optional = true,
        ),
        AccessDescriptor(
            id = AccessId.CAMERA,
            title = "Camera",
            purpose = "The flashlight action toggles the camera torch through CameraManager. AstraTouch never opens a camera preview or captures a photo.",
            dependentFeatures = listOf("Flashlight action"),
            fallback = "The flashlight action reports that the torch is unavailable and suggests the system Quick Settings tile.",
            optional = true,
        ),
        AccessDescriptor(
            id = AccessId.BATTERY_OPTIMIZATION,
            title = "Battery optimisation exemption",
            purpose = "Some manufacturers stop background services aggressively. Excluding AstraTouch keeps the bubble alive after you leave an app.",
            dependentFeatures = listOf("Bubble persistence in the background", "Boot start-up", "Long macro runs"),
            fallback = "The bubble may disappear after a while on aggressive OEM builds; AstraTouch detects this and offers to restart.",
            optional = true,
        ),
    )

    fun byId(id: AccessId): AccessDescriptor = all.first { it.id == id }
}
