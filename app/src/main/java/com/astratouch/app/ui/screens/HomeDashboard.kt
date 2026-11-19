package com.astratouch.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astratouch.app.BuildConfig
import com.astratouch.app.data.prefs.PreferencesStore
import com.astratouch.app.domain.model.AccessId
import com.astratouch.app.domain.model.PermissionSnapshot
import com.astratouch.app.domain.model.Profile
import com.astratouch.app.domain.model.ProfileType
import com.astratouch.app.permission.PermissionManager
import com.astratouch.app.service.FloatingOverlayService
import com.astratouch.app.service.overlay.OverlayController
import com.astratouch.app.ui.components.AstraCard
import com.astratouch.app.ui.components.AstraPrimaryButton
import com.astratouch.app.ui.components.AstraSecondaryButton
import com.astratouch.app.ui.components.AstraScaffold
import com.astratouch.app.ui.components.SectionHeader
import com.astratouch.app.ui.components.StatusPill
import com.astratouch.app.ui.theme.LocalAstraColors
import com.astratouch.app.util.NavRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val preferencesStore: PreferencesStore,
    private val permissionManager: PermissionManager,
    private val profileRepository: com.astratouch.app.data.repository.ProfileRepository,
    private val actionRegistry: com.astratouch.app.service.action.ActionRegistry,
) : ViewModel() {

    private val _permissions = MutableStateFlow(permissionManager.current())
    val permissions: StateFlow<PermissionSnapshot> = _permissions.asStateFlow()

    private val _overlayRunning = MutableStateFlow(OverlayController.isRunning())
    val overlayRunning: StateFlow<Boolean> = _overlayRunning.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val activeProfile: StateFlow<Profile?> = profileRepository.observeActiveProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val enabledProfiles: StateFlow<List<Profile>> = profileRepository.observeProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val prefs = preferencesStore.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.astratouch.app.domain.model.AppPreferences())

    val availableActionCount: Int get() = actionRegistry.availableActions().size
    val totalActionCount: Int get() = actionRegistry.all().size

    fun refresh() {
        _permissions.value = permissionManager.refresh()
        _overlayRunning.value = OverlayController.isRunning()
    }

    fun startOverlay(context: android.content.Context) {
        if (!permissionManager.isGranted(AccessId.OVERLAY)) {
            _message.value = "Grant \"Display over other apps\" first — open the Permission Center to do that."
            return
        }
        FloatingOverlayService.start(context)
        viewModelScope.launch {
            preferencesStore.setOverlayEnabled(true)
            kotlinx.coroutines.delay(400)
            refresh()
        }
    }

    fun stopOverlay(context: android.content.Context) {
        FloatingOverlayService.stop(context)
        viewModelScope.launch {
            preferencesStore.setOverlayEnabled(false)
            kotlinx.coroutines.delay(300)
            refresh()
        }
    }

    fun switchProfile(id: String) {
        viewModelScope.launch {
            profileRepository.setActiveProfile(id)
            preferencesStore.setActiveProfileId(id)
            OverlayController.instance?.refreshConfiguration()
        }
    }

    fun dismissMessage() { _message.value = null }
}

@Composable
fun HomeDashboardScreen(
    onNavigate: (String) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val astra = LocalAstraColors.current
    val context = LocalContext.current
    val permissions by viewModel.permissions.collectAsState()
    val running by viewModel.overlayRunning.collectAsState()
    val profile by viewModel.activeProfile.collectAsState()
    val profiles by viewModel.enabledProfiles.collectAsState()
    val prefs by viewModel.prefs.collectAsState()
    val message by viewModel.message.collectAsState()

    LaunchedEffect(Unit) { viewModel.refresh() }

    AstraScaffold(
        title = "AstraTouch",
        subtitle = "Your Phone. Your Control.",
        actions = {
            androidx.compose.material3.IconButton(onClick = { onNavigate(NavRoutes.SETTINGS) }) {
                Icon(Icons.Filled.Tune, contentDescription = "Settings", tint = astra.onBackground)
            }
        },
    ) { modifier ->
        Column(
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // Status hero card
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                astra.primary.copy(alpha = 0.18f),
                                astra.secondary.copy(alpha = 0.12f),
                                astra.surface,
                            ),
                        ),
                    )
                    .padding(20.dp),
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(if (running) astra.success else astra.onBackground.copy(alpha = 0.3f)),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (running) "Bubble active" else "Bubble stopped",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.testTag("dashboard_status"),
                        )
                        Spacer(Modifier.weight(1f))
                        StatusPill(
                            text = if (permissions.overlayReady) "Overlay ready" else "Overlay needed",
                            positive = permissions.overlayReady,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (running) "Tap the bubble anywhere to open the command menu."
                        else "Start the bubble to control your phone from any screen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = astra.onBackground.copy(alpha = 0.72f),
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AstraPrimaryButton(
                            text = if (running) "Stop bubble" else "Start bubble",
                            onClick = {
                                if (running) viewModel.stopOverlay(context) else viewModel.startOverlay(context)
                            },
                            modifier = Modifier.weight(1f),
                        )
                        AstraSecondaryButton(
                            text = "Permissions",
                            onClick = { onNavigate(NavRoutes.PERMISSIONS) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            if (message != null) {
                Spacer(Modifier.height(10.dp))
                AstraCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            message!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = astra.warning,
                            modifier = Modifier.weight(1f),
                        )
                        androidx.compose.material3.TextButton(onClick = { viewModel.dismissMessage() }) {
                            Text("OK")
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            SectionHeader("Active profile", "Each profile carries its own bubble, menu, gestures and theme")
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                profiles.take(3).forEach { p ->
                    ProfileChip(
                        label = p.name,
                        selected = p.id == profile?.id,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.switchProfile(p.id) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            AstraSecondaryButton(
                text = "Manage profiles",
                onClick = { onNavigate(NavRoutes.PROFILES) },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))
            SectionHeader("Capability summary", "${viewModel.availableActionCount} of ${viewModel.totalActionCount} actions ready")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniStat(
                    label = "Overlay",
                    ok = permissions.overlayReady,
                    modifier = Modifier.weight(1f),
                )
                MiniStat(
                    label = "Accessibility",
                    ok = permissions.accessibilityReady,
                    modifier = Modifier.weight(1f),
                )
                MiniStat(
                    label = "Settings",
                    ok = permissions.canControlBrightness,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(16.dp))
            SectionHeader("Configure")
            val tiles = listOf(
                Triple("Floating bubble", Icons.Filled.Visibility, NavRoutes.BUBBLE),
                Triple("Radial menu", Icons.Filled.Bolt, NavRoutes.RADIAL),
                Triple("Gestures", Icons.Filled.Gesture, NavRoutes.GESTURES),
                Triple("Themes", Icons.Filled.Palette, NavRoutes.THEMES),
                Triple("Macros", Icons.Filled.Accessibility, NavRoutes.MACROS),
                Triple("Apps", Icons.Filled.Apps, NavRoutes.APPS),
            )
            tiles.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { (label, icon, route) ->
                        AstraCard(modifier = Modifier.weight(1f), onClick = { onNavigate(route) }) {
                            Column {
                                Icon(icon, contentDescription = null, tint = astra.primary, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.height(8.dp))
                                Text(label, style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(8.dp))
            SectionHeader("Tools")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AstraCard(modifier = Modifier.weight(1f), onClick = { onNavigate(NavRoutes.CLIPBOARD) }) {
                    Column {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = astra.secondary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Clipboard", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (prefs.clipboardCaptureEnabled) "History on" else "History off",
                            style = MaterialTheme.typography.bodySmall,
                            color = astra.onBackground.copy(alpha = 0.6f),
                        )
                    }
                }
                AstraCard(modifier = Modifier.weight(1f), onClick = { onNavigate(NavRoutes.SCREENSHOTS) }) {
                    Column {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = astra.tertiary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Screenshots", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Capture & annotate",
                            style = MaterialTheme.typography.bodySmall,
                            color = astra.onBackground.copy(alpha = 0.6f),
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            AstraCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CloudOff, contentDescription = null, tint = astra.success, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Fully offline", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "AstraTouch has no internet permission. Version ${BuildConfig.ASTRA_VERSION_NAME}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = astra.onBackground.copy(alpha = 0.65f),
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ProfileChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val astra = LocalAstraColors.current
    Box(
        modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            .background(if (selected) astra.primary.copy(alpha = 0.18f) else astra.surface)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) astra.primary else astra.onBackground.copy(alpha = 0.8f),
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun MiniStat(label: String, ok: Boolean, modifier: Modifier = Modifier) {
    val astra = LocalAstraColors.current
    Column(
        modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            .background(astra.surface.copy(alpha = 0.9f))
            .padding(12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = astra.onBackground.copy(alpha = 0.6f))
        Spacer(Modifier.height(4.dp))
        Text(
            if (ok) "Ready" else "Missing",
            style = MaterialTheme.typography.titleSmall,
            color = if (ok) astra.success else astra.warning,
        )
    }
}
