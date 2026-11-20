package com.astratouch.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import com.astratouch.app.MainActivity
import com.astratouch.app.R
import com.astratouch.app.accessibility.AstraAccessibilityService
import com.astratouch.app.data.prefs.PreferencesStore
import com.astratouch.app.data.repository.ProfileRepository
import com.astratouch.app.data.repository.RecentActionRepository
import com.astratouch.app.domain.model.AppPreferences
import com.astratouch.app.domain.model.BubbleConfig
import com.astratouch.app.domain.model.GestureMapping
import com.astratouch.app.domain.model.GestureType
import com.astratouch.app.domain.model.Profile
import com.astratouch.app.domain.model.RadialConfig
import com.astratouch.app.domain.model.RadialMenuItem
import com.astratouch.app.domain.model.ThemeConfig
import com.astratouch.app.domain.model.ThemePreset
import com.astratouch.app.service.action.ActionRegistry
import com.astratouch.app.service.action.ActionResult
import com.astratouch.app.service.overlay.MenuEvent
import com.astratouch.app.service.overlay.MutableOverlayStateHolder
import com.astratouch.app.service.overlay.OverlayController
import com.astratouch.app.service.overlay.OverlayGeometry
import com.astratouch.app.service.overlay.OverlayState
import com.astratouch.app.ui.overlay.AstraOverlayHost
import com.astratouch.app.ui.theme.AstraTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Hosts the floating bubble and the radial command menu.
 *
 * Implementation notes (Part 5, Part 17):
 *  * A single `WindowManager` overlay is created once and reused; the bubble is
 *    drawn by Compose inside a `ComposeView`. No per-frame layout inflation.
 *  * Nothing polls the screen. Position is written to the database only on drag
 *    end, and configuration is read from Flows so edits in the app UI appear
 *    immediately.
 *  * The service stops itself if the overlay permission is revoked, rather than
 *    leaking a dead window.
 */
@AndroidEntryPoint
class FloatingOverlayService : androidx.lifecycle.LifecycleService(), OverlayController {

    @Inject lateinit var preferencesStore: PreferencesStore
    @Inject lateinit var profileRepository: ProfileRepository
    @Inject lateinit var actionRegistry: ActionRegistry
    @Inject lateinit var recentActionRepository: RecentActionRepository

    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private val stateHolder = MutableOverlayStateHolder()

    private val bubbleConfigState = MutableStateFlow(BubbleConfig())
    private val radialConfigState = MutableStateFlow(RadialConfig())
    private val radialItemsState = MutableStateFlow<List<RadialMenuItem>>(emptyList())
    private val gesturesState = MutableStateFlow<List<GestureMapping>>(emptyList())
    private val themeState = MutableStateFlow(ThemeConfig.fromPreset(ThemePreset.MIDNIGHT_OBSIDIAN))

    override val state: StateFlow<OverlayState> = stateHolder.state
    override val menuEvents = stateHolder.menuEvents

    private var currentProfile: Profile? = null
    private var lastBounds = OverlayGeometry.ScreenBounds(0, 0)

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        OverlayController.instance = this
        promoteToForeground()
        stateHolder.update { it.copy(isRunning = true, activeProfileId = currentProfileId) }
        observeProfile()
        observeForegroundApp()
        showOrRefreshBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> stopService()
            ACTION_TOGGLE -> toggleMenu()
            ACTION_SHOW -> showBubble()
            ACTION_HIDE -> hideBubble()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        OverlayController.instance = null
        removeBubble()
        stateHolder.update { OverlayState.STOPPED }
        super.onDestroy()
    }

    // ------------------------------------------------------------- controller

    override fun showBubble() = mainThread {
        stateHolder.update { it.copy(bubbleVisible = true, temporaryHidden = false) }
        composeView?.visibility = android.view.View.VISIBLE
    }

    override fun hideBubble() = mainThread {
        stateHolder.update { it.copy(bubbleVisible = false, menuVisible = false) }
        // Temporary hide keeps the window alive but invisible, so "quick show"
        // is instant and does not need to recreate a WindowManager view.
        composeView?.visibility = android.view.View.GONE
    }

    override fun toggleBubble() {
        if (state.value.bubbleVisible) hideBubble() else showBubble()
    }

    override fun openMenu() {
        if (!state.value.bubbleVisible) showBubble()
        if (!Settings.canDrawOverlays(this)) return
        stateHolder.update { it.copy(menuVisible = true) }
        stateHolder.emit(MenuEvent.Opened)
        applyMenuWindowMode(true)
    }

    override fun closeMenu() {
        stateHolder.update { it.copy(menuVisible = false) }
        stateHolder.emit(MenuEvent.Closed)
        applyMenuWindowMode(false)
    }

    override fun toggleMenu() {
        if (state.value.menuVisible) closeMenu() else openMenu()
    }

    override fun stopService() {
        stopSelf()
    }

    override fun refreshConfiguration() {
        observeProfile()
    }

    // -------------------------------------------------------------- overlay UI

    private fun showOrRefreshBubble() {
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Overlay permission missing; stopping service")
            stopSelf()
            return
        }
        val view = composeView ?: createComposeView().also { composeView = it }
        if (layoutParams == null) {
            layoutParams = createLayoutParams()
            runCatching { windowManager.addView(view, layoutParams) }
                .onFailure {
                    Log.e(TAG, "Could not attach overlay window", it)
                    stopSelf()
                    return
                }
        }
        stateHolder.update {
            it.copy(
                bubbleVisible = true,
                dockSide = bubbleConfigState.value.dockSide.name,
                positionX = bubbleConfigState.value.positionX,
                positionY = bubbleConfigState.value.positionY,
            )
        }
        layoutBubble(bubbleConfigState.value)
    }

    private fun createComposeView(): ComposeView = ComposeView(this).apply {
        setContent {
            val prefs by preferencesStore.preferences.collectAsState(initial = AppPreferences())
            val theme by themeState.collectAsState()
            val bubble by bubbleConfigState.collectAsState()
            val radial by radialConfigState.collectAsState()
            val items by radialItemsState.collectAsState()
            val overlayState by state.collectAsState()
            val gestures by gesturesState.collectAsState()

            AstraTheme(theme = theme, reducedMotion = prefs.reduceMotion) {
                AstraOverlayHost(
                    state = overlayState,
                    bubble = bubble,
                    radial = radial,
                    items = items,
                    gestures = gestures,
                    hapticsEnabled = prefs.hapticsEnabled,
                    onDrag = { dx, dy -> moveBubbleBy(dx, dy) },
                    onDragEnd = { saveBubblePosition() },
                    onGesture = { gesture -> handleGesture(gesture) },
                    onActionTap = { actionId, target -> executeAction(actionId, target) },
                    onCloseMenu = { closeMenu() },
                )
            }
        }
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            // NOT_FOCUSABLE keeps the underlying app interactive; the menu uses a
            // second window mode when expanded to capture dismiss taps.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 400
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                fitInsetsTypes = 0
            }
        }
    }

    private fun applyMenuWindowMode(menuOpen: Boolean) {
        val view = composeView ?: return
        val params = layoutParams ?: return
        // Keep NOT_FOCUSABLE (so typing in other apps keeps working) but the
        // menu needs a wider window; Compose handles the size itself because
        // both dimensions are WRAP_CONTENT.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            runCatching { windowManager.updateViewLayout(view, params) }
        }
        stateHolder.update { it.copy(menuVisible = menuOpen) }
    }

    private fun layoutBubble(config: BubbleConfig) {
        val view = composeView ?: return
        val params = layoutParams ?: return
        val density = resources.displayMetrics.density
        val sizePx = (config.sizeDp * density).toInt().coerceAtLeast((36 * density).toInt())
        val marginPx = (config.edgeMarginDp * density).toInt()
        val bounds = OverlayGeometry.screenBounds(this).also { lastBounds = it }

        val (x, y) = if (config.hasStoredPosition && config.positionX > 0) {
            OverlayGeometry.snapAndClamp(
                config.positionX, config.positionY, sizePx, bounds, marginPx,
                config.snapMode, config.dockSide,
            )
        } else {
            // First run: tuck the bubble against the right edge, mid-height.
            val startX = bounds.width - sizePx - marginPx
            val startY = (bounds.height * 0.62f).toInt()
            OverlayGeometry.snapAndClamp(startX, startY, sizePx, bounds, marginPx, config.snapMode, config.dockSide)
        }
        params.x = x
        params.y = y
        runCatching { windowManager.updateViewLayout(view, params) }
        stateHolder.update {
            it.copy(
                positionX = x,
                positionY = y,
                dockSide = OverlayGeometry.dockSideFor(x, sizePx, bounds).name,
            )
        }
    }

    /** Manual drag: clamps immediately so the bubble can never leave the screen. */
    private fun moveBubbleBy(dx: Float, dy: Float) {
        val params = layoutParams ?: return
        val view = composeView ?: return
        val density = resources.displayMetrics.density
        val config = bubbleConfigState.value
        val sizePx = (config.sizeDp * density).toInt().coerceAtLeast((36 * density).toInt())
        val marginPx = (config.edgeMarginDp * density).toInt()
        val bounds = OverlayGeometry.screenBounds(this)
        val (x, y) = OverlayGeometry.snapAndClamp(
            params.x + dx.toInt(),
            params.y + dy.toInt(),
            sizePx,
            bounds,
            marginPx,
            com.astratouch.app.domain.model.SnapMode.NONE,
            null,
        )
        params.x = x
        params.y = y
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun saveBubblePosition() {
        val params = layoutParams ?: return
        val density = resources.displayMetrics.density
        val config = bubbleConfigState.value
        val sizePx = (config.sizeDp * density).toInt()
        val marginPx = (config.edgeMarginDp * density).toInt()
        val bounds = OverlayGeometry.screenBounds(this)
        val (x, y) = OverlayGeometry.snapAndClamp(
            params.x, params.y, sizePx, bounds, marginPx, config.snapMode, config.dockSide,
        )
        params.x = x
        params.y = y
        composeView?.let { runCatching { windowManager.updateViewLayout(it, params) } }
        val side = when {
            config.snapMode == com.astratouch.app.domain.model.SnapMode.EDGE ->
                OverlayGeometry.dockSideFor(x, sizePx, bounds).name
            else -> config.dockSide.name
        }
        stateHolder.update { it.copy(positionX = x, positionY = y, dockSide = side) }
        lifecycleScope.launch(ioDispatcher) {
            runCatching { profileRepository.saveBubblePosition(config.id, x, y, side) }
        }
    }

    // ------------------------------------------------------------------ events

    private fun handleGesture(gesture: GestureType) {
        when (gesture) {
            GestureType.SINGLE_TAP -> {
                val mapping = gesturesState.value.firstOrNull { it.gesture == GestureType.SINGLE_TAP }
                if (mapping?.actionId == com.astratouch.app.domain.model.ActionIds.OPEN_RADIAL_MENU) {
                    toggleMenu()
                } else if (mapping != null) {
                    lifecycleScope.launch { executeAction(mapping.actionId, null) }
                } else {
                    toggleMenu()
                }
            }
            GestureType.DOUBLE_TAP -> {
                val mapping = gesturesState.value.firstOrNull { it.gesture == GestureType.DOUBLE_TAP }
                if (mapping != null) lifecycleScope.launch { executeAction(mapping.actionId, null) }
                else toggleMenu()
            }
            else -> {
                val mapping = gesturesState.value.firstOrNull { it.gesture == gesture }
                if (mapping != null) lifecycleScope.launch { executeAction(mapping.actionId, null) }
            }
        }
    }

    private fun executeAction(actionId: String, target: String?) {
        lifecycleScope.launch {
            stateHolder.emit(MenuEvent.ActionExecuted(actionId))
            val result = actionRegistry.execute(actionId, target)
            when (result) {
                is ActionResult.Success -> Unit
                is ActionResult.Partial -> showToast(result.explanation)
                is ActionResult.NeedsPermission -> showToast(result.explanation)
                is ActionResult.Unsupported -> showToast(result.explanation)
                is ActionResult.Failure -> showToast(result.explanation)
                ActionResult.Cancelled -> Unit
            }
            closeMenu()
        }
    }

    private fun showToast(message: String) {
        try {
            android.os.Handler(mainLooper).post {
                android.widget.Toast.makeText(applicationContext, message, android.widget.Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not show overlay feedback", e)
        }
    }

    // -------------------------------------------------------------- data flows

    private fun observeProfile() {
        lifecycleScope.launch {
            profileRepository.observeActiveProfile().collect { profile ->
                val resolved = profile ?: return@collect
                currentProfile = resolved
                stateHolder.update { it.copy(activeProfileId = resolved.id) }
                profileRepository.getBubbleConfig(resolved.bubbleConfigId)?.let { bubble ->
                    bubbleConfigState.value = bubble
                    if (Settings.canDrawOverlays(this@FloatingOverlayService)) layoutBubble(bubble)
                }
                profileRepository.getRadialConfig(resolved.radialConfigId)?.let { radialConfigState.value = it }
                profileRepository.observeRadialItems(resolved.radialConfigId).collect { items ->
                    radialItemsState.value = items
                }
            }
        }
        lifecycleScope.launch {
            profileRepository.observeActiveProfile().collect { profile ->
                val id = profile?.id ?: com.astratouch.app.domain.model.ProfileType.DEFAULT.id
                profileRepository.observeGestures(id).collect { gesturesState.value = it }
            }
        }
        lifecycleScope.launch {
            preferencesStore.preferences.collect { prefs ->
                val theme = profileRepository.getTheme("theme-${prefs.activeProfileId}")
                    ?: profileRepository.getTheme("theme-midnight_obsidian")
                if (theme != null) themeState.value = theme
            }
        }
    }

    /** Auto-switching profiles based on the foreground package (no polling). */
    private fun observeForegroundApp() {
        lifecycleScope.launch {
            AstraAccessibilityService.foregroundPackage.collect { pkg ->
                stateHolder.update { it.copy(foregroundPackage = pkg) }
                val target = pkg?.let { p ->
                    profileRepository.observeProfiles().first().firstOrNull { it.autoSwitchPackage == p }
                }
                if (target != null && target.id != currentProfileId) {
                    runCatching { profileRepository.setActiveProfile(target.id) }
                }
            }
        }
    }

    private val currentProfileId: String
        get() = currentProfile?.id ?: com.astratouch.app.domain.model.ProfileType.DEFAULT.id

    // ------------------------------------------------------------- foreground

    private fun promoteToForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.overlay_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.overlay_channel_description)
                setShowBadge(false)
                enableVibration(false)
            }
            manager?.createNotificationChannel(channel)
        }
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val hideIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, FloatingOverlayService::class.java).setAction(ACTION_HIDE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, FloatingOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_astra)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, getString(R.string.overlay_notification_hide), hideIntent)
            .addAction(0, getString(R.string.overlay_notification_stop), stopIntent)
            .build()
    }

    private fun removeBubble() {
        composeView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        composeView = null
        layoutParams = null
    }

    private inline fun mainThread(crossinline block: () -> Unit) {
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.Main) { block() }
    }

    companion object {
        private const val TAG = "AstraOverlay"
        const val CHANNEL_ID = "astratouch_overlay"
        const val NOTIFICATION_ID = 4101
        const val ACTION_STOP = "com.astratouch.app.action.STOP_OVERLAY"
        const val ACTION_TOGGLE = "com.astratouch.app.action.TOGGLE_MENU"
        const val ACTION_SHOW = "com.astratouch.app.action.SHOW_BUBBLE"
        const val ACTION_HIDE = "com.astratouch.app.action.HIDE_BUBBLE"

        private val ioDispatcher = kotlinx.coroutines.Dispatchers.IO

        fun start(context: Context) {
            val intent = Intent(context, FloatingOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingOverlayService::class.java))
        }
    }
}
