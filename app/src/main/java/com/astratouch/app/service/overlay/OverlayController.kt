package com.astratouch.app.service.overlay

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide handle on the running overlay so that non-service code (actions,
 * the app UI, macro engine) can drive the bubble without binding a service.
 *
 * Registered by [com.astratouch.app.service.FloatingOverlayService] on create and
 * cleared on destroy, which makes "is the bubble alive?" a truthful question.
 */
interface OverlayController {
    val state: StateFlow<OverlayState>

    /** Menu open/close requests coming from the bubble itself. */
    val menuEvents: SharedFlow<MenuEvent>

    fun showBubble()
    fun hideBubble()
    fun toggleBubble()
    fun openMenu()
    fun closeMenu()
    fun toggleMenu()
    fun stopService()
    fun refreshConfiguration()

    companion object {
        @Volatile
        var instance: OverlayController? = null

        fun isRunning(): Boolean = instance?.state?.value?.isRunning == true
    }
}

data class OverlayState(
    val isRunning: Boolean = false,
    val bubbleVisible: Boolean = false,
    val menuVisible: Boolean = false,
    val temporaryHidden: Boolean = false,
    val dockSide: String = "RIGHT",
    val positionX: Int = -1,
    val positionY: Int = -1,
    val foregroundPackage: String? = null,
    val activeProfileId: String = "default",
) {
    companion object {
        val STOPPED = OverlayState()
    }
}

sealed interface MenuEvent {
    data object Opened : MenuEvent
    data object Closed : MenuEvent
    data class ActionExecuted(val actionId: String) : MenuEvent
}

/** Mutable implementation shared between the service and its composables. */
class MutableOverlayStateHolder {
    private val mutableState = MutableStateFlow(OverlayState.STOPPED)
    val state: StateFlow<OverlayState> = mutableState.asStateFlow()

    private val mutableMenuEvents = MutableSharedFlow<MenuEvent>(extraBufferCapacity = 8)
    val menuEvents: SharedFlow<MenuEvent> = mutableMenuEvents.asSharedFlow()

    fun update(block: (OverlayState) -> OverlayState) {
        mutableState.value = block(mutableState.value)
    }

    fun emit(event: MenuEvent) {
        mutableMenuEvents.tryEmit(event)
    }
}
