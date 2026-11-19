package com.astratouch.app.service.action

import com.astratouch.app.domain.model.AccessId
import com.astratouch.app.domain.model.ActionCategory

/**
 * Capability state of a single action on the current device.
 *
 * Reported up-front so the UI can render accurate affordances instead of
 * failing after the user taps (H20/H21: no misleading controls).
 */
enum class CapabilityState(val label: String) {
    AVAILABLE("Available"),
    NEEDS_PERMISSION("Needs permission"),
    UNSUPPORTED_OS("Not supported on this Android version"),
    NEEDS_DEVICE_SUPPORT("Not supported by this device"),
    DISABLED("Disabled by configuration"),
    ;

    val isAvailable: Boolean get() = this == AVAILABLE
}

/** Result of attempting to execute an action. */
sealed interface ActionResult {
    data object Success : ActionResult

    /** Action ran but the user must still complete something (e.g. system panel). */
    data class Partial(val explanation: String) : ActionResult

    /** Action could not run; explanation is user-facing and never blank. */
    data class Failure(
        val reason: FailureReason,
        val explanation: String,
        val recoverable: Boolean = true,
    ) : ActionResult

    data class NeedsPermission(
        val access: AccessId,
        val explanation: String,
    ) : ActionResult

    data class Unsupported(val explanation: String) : ActionResult

    data object Cancelled : ActionResult
}

enum class FailureReason(val label: String) {
    PERMISSION_MISSING("Permission missing"),
    NOT_SUPPORTED("Not supported"),
    TIMEOUT("Timed out"),
    SERVICE_UNAVAILABLE("Service unavailable"),
    INVALID_TARGET("Invalid target"),
    USER_CANCELLED("Cancelled by user"),
    INTERNAL("Internal error"),
    ;

    val userMessage: String
        get() = when (this) {
            PERMISSION_MISSING -> "AstraTouch needs an extra permission for this action."
            NOT_SUPPORTED -> "Your device or Android version does not support this action."
            TIMEOUT -> "The action took too long to respond."
            SERVICE_UNAVAILABLE -> "The required AstraTouch service is not running."
            INVALID_TARGET -> "That target no longer exists on this device."
            USER_CANCELLED -> "Cancelled."
            INTERNAL -> "Something went wrong while running this action."
        }
}

/** Static description of an action; behaviour lives in [ActionHandler]. */
data class AstraAction(
    val id: String,
    val name: String,
    val description: String,
    val category: ActionCategory,
    val iconKey: String,
    val requiredAccess: Set<AccessId> = emptySet(),
    val minApi: Int = 29,
    val maxApiInclusive: Int? = null,
    val supportsTarget: Boolean = false,
    val searchKeywords: List<String> = emptyList(),
    val isAstraInternal: Boolean = false,
) {
    fun isApiSupported(currentApi: Int): Boolean =
        currentApi >= minApi && (maxApiInclusive == null || currentApi <= maxApiInclusive)
}

/** Executes an action. Implementations must be fast and never block the main thread. */
interface ActionHandler {
    /**
     * @param target optional target (package name, shortcut id, recorder payload…)
     * @return a non-null [ActionResult] describing exactly what happened.
     */
    suspend fun execute(target: String? = null): ActionResult
}

/** Catalogue entry: static metadata plus its handler and capability check. */
class RegisteredAction(
    val action: AstraAction,
    val handler: ActionHandler,
    val capabilityCheck: () -> CapabilityState,
)
