package com.astratouch.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astratouch.app.data.repository.ProfileRepository
import com.astratouch.app.domain.model.ActionCategory
import com.astratouch.app.domain.model.GestureMapping
import com.astratouch.app.domain.model.GestureType
import com.astratouch.app.gesture.GestureEngine
import com.astratouch.app.service.action.ActionIcons
import com.astratouch.app.service.action.ActionRegistry
import com.astratouch.app.service.action.AstraAction
import com.astratouch.app.service.action.CapabilityState
import com.astratouch.app.service.overlay.OverlayController
import com.astratouch.app.ui.components.AstraCard
import com.astratouch.app.ui.components.AstraScaffold
import com.astratouch.app.ui.components.AstraSecondaryButton
import com.astratouch.app.ui.components.EmptyState
import com.astratouch.app.ui.components.SectionHeader
import com.astratouch.app.ui.components.SettingRow
import com.astratouch.app.ui.components.StatusPill
import com.astratouch.app.ui.overlay.IconRegistry
import com.astratouch.app.ui.theme.LocalAstraColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GestureViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val actionRegistry: ActionRegistry,
) : ViewModel() {
    private val _mappings = MutableStateFlow<List<GestureMapping>>(emptyList())
    val mappings: StateFlow<List<GestureMapping>> = _mappings.asStateFlow()

    private val _testResult = MutableStateFlow<String?>(null)
    val testResult: StateFlow<String?> = _testResult.asStateFlow()

    private var profileId: String = "default"

    init {
        viewModelScope.launch {
            val profile = profileRepository.getActiveProfile() ?: return@launch
            profileId = profile.id
            profileRepository.observeGestures(profile.id).collect { _mappings.value = it }
        }
    }

    fun bind(gesture: GestureType, actionId: String) {
        val existing = _mappings.value.firstOrNull { it.gesture == gesture }
        val mapping = GestureMapping(
            id = existing?.id ?: "$profileId-${gesture.name}",
            profileId = profileId,
            gesture = gesture,
            actionId = actionId,
            swipeThresholdPx = existing?.swipeThresholdPx ?: 48,
            sensitivity = existing?.sensitivity ?: 1f,
            hapticsEnabled = existing?.hapticsEnabled ?: true,
        )
        _mappings.value = _mappings.value.filterNot { it.gesture == gesture } + mapping
        viewModelScope.launch {
            profileRepository.saveGesture(mapping)
            OverlayController.instance?.refreshConfiguration()
        }
    }

    fun setSensitivity(gesture: GestureType, value: Float) {
        val existing = _mappings.value.firstOrNull { it.gesture == gesture } ?: return
        val next = existing.copy(sensitivity = value)
        _mappings.value = _mappings.value.map { if (it.gesture == gesture) next else it }
        viewModelScope.launch { profileRepository.saveGesture(next) }
    }

    fun setHaptics(gesture: GestureType, enabled: Boolean) {
        val existing = _mappings.value.firstOrNull { it.gesture == gesture } ?: return
        val next = existing.copy(hapticsEnabled = enabled)
        _mappings.value = _mappings.value.map { if (it.gesture == gesture) next else it }
        viewModelScope.launch { profileRepository.saveGesture(next) }
    }

    fun resetDefaults() {
        viewModelScope.launch {
            profileRepository.resetGestures(profileId)
            OverlayController.instance?.refreshConfiguration()
        }
    }

    fun recordTest(gesture: GestureType) {
        val mapping = _mappings.value.firstOrNull { it.gesture == gesture }
        _testResult.value = if (mapping == null) {
            "${gesture.label}: no action bound"
        } else {
            "${gesture.label} → ${actionRegistry.byId(mapping.actionId)?.name ?: mapping.actionId}"
        }
    }

    fun clearTest() { _testResult.value = null }

    val allActions: List<AstraAction> get() = actionRegistry.all()
    fun capabilityOf(id: String): CapabilityState = actionRegistry.capabilityOf(id)
}

@Composable
fun GestureManagerScreen(
    onBack: () -> Unit,
    viewModel: GestureViewModel = hiltViewModel(),
) {
    val astra = LocalAstraColors.current
    val mappings by viewModel.mappings.collectAsState()
    val testResult by viewModel.testResult.collectAsState()
    var binding by remember { mutableStateOf<GestureType?>(null) }

    AstraScaffold(title = "Gestures", subtitle = "Tap, long-press and swipe actions", onBack = onBack) { modifier ->
        Column(
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            AstraCard {
                Column {
                    Text("Live test pad", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Drag or tap inside this pad exactly like the floating bubble. The recognised gesture " +
                            "and its binding appear below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = astra.onBackground.copy(alpha = 0.65f),
                    )
                    Spacer(Modifier.height(12.dp))
                    GestureTestPad(
                        engine = GestureEngine(
                            mappingsProvider = { mappings },
                            clock = { System.nanoTime() / 1_000_000L },
                        ),
                        onGesture = { g -> viewModel.recordTest(g) },
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        testResult ?: "No gesture recognised yet.",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (testResult == null) astra.onBackground.copy(alpha = 0.5f) else astra.primary,
                        modifier = Modifier.testTag("gesture_result"),
                    )
                }
            }

            SectionHeader("Bindings", "Tap a gesture to choose the action it runs")
            GestureType.entries.forEach { gesture ->
                val mapping = mappings.firstOrNull { it.gesture == gesture }
                val capability = mapping?.let { viewModel.capabilityOf(it.actionId) }
                AstraCard(Modifier.padding(bottom = 8.dp), onClick = { binding = gesture }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            IconRegistry.get(
                                mapping?.let { ActionIcons.iconFor(it.actionId) } ?: "ic_gesture",
                            ),
                            contentDescription = null,
                            tint = astra.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(gesture.label, style = MaterialTheme.typography.titleSmall)
                            Text(
                                mapping?.let { m ->
                                    viewModel.allActions.firstOrNull { it.id == m.actionId }?.name ?: m.actionId
                                } ?: "Not bound",
                                style = MaterialTheme.typography.bodySmall,
                                color = astra.onBackground.copy(alpha = 0.65f),
                            )
                        }
                        if (capability != null && capability != CapabilityState.AVAILABLE) {
                            StatusPill(capability.label, positive = false)
                        }
                    }
                }
            }

            SectionHeader("Tuning")
            GestureType.entries.filter { it.isSwipe }.forEach { gesture ->
                val mapping = mappings.firstOrNull { it.gesture == gesture } ?: return@forEach
                Column(Modifier.padding(vertical = 4.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(gesture.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(
                            "Sensitivity %.1fx (${mapping.effectiveThresholdPx()} dp)".format(mapping.sensitivity),
                            style = MaterialTheme.typography.labelSmall,
                            color = astra.primary,
                        )
                    }
                    Slider(
                        value = mapping.sensitivity,
                        onValueChange = { viewModel.setSensitivity(gesture, it) },
                        valueRange = 0.2f..2f,
                    )
                    SettingRow(
                        title = "Haptics for ${gesture.label.lowercase()}",
                        description = "Vibrate when this gesture is recognised.",
                        checked = mapping.hapticsEnabled,
                        onCheckedChange = { viewModel.setHaptics(gesture, it) },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            AstraCard {
                Column {
                    Text("What cannot be intercepted", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "AstraTouch only recognises gestures that start on its own bubble or its own screens. " +
                            "Android does not let an ordinary app capture swipes that begin inside another app, " +
                            "so system-wide gestures still belong to your launcher and Android itself. " +
                            "AstraTouch deliberately does not add a full-screen invisible overlay to fake that, " +
                            "because it would break app scrolling and is a known malware pattern.",
                        style = MaterialTheme.typography.bodySmall,
                        color = astra.onBackground.copy(alpha = 0.7f),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            AstraSecondaryButton(
                text = "Reset gestures to defaults",
                onClick = { viewModel.resetDefaults() },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Filled.Refresh,
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    binding?.let { gesture ->
        val current = mappings.firstOrNull { it.gesture == gesture }
        AlertDialog(
            onDismissRequest = { binding = null },
            title = { Text("Bind ${gesture.label}") },
            text = {
                ActionPickerDialog(
                    actions = viewModel.allActions,
                    capabilityOf = viewModel::capabilityOf,
                    onDismiss = { binding = null },
                    onPick = { actionId ->
                        viewModel.bind(gesture, actionId)
                        binding = null
                    },
                )
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { binding = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun GestureTestPad(
    engine: GestureEngine,
    onGesture: (GestureType) -> Unit,
) {
    val astra = LocalAstraColors.current
    var hint by remember { mutableStateOf("Touch here") }
    Box(
        Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(astra.background.copy(alpha = 0.6f))
            .border(1.dp, astra.primary.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset -> engine.onDown(offset.x, offset.y); hint = "Dragging…" },
                    onDrag = { change, amount ->
                        change.consume()
                        engine.onMove(change.position.x, change.position.y)?.let(onGesture)
                    },
                    onDragEnd = {
                        engine.onUp(0f, 0f, density)?.let(onGesture)
                        hint = "Touch here"
                    },
                    onDragCancel = { engine.onCancel(); hint = "Touch here" },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        engine.onDown(it.x, it.y)
                        engine.onUp(it.x, it.y, density)?.let(onGesture)
                    },
                    onLongPress = {
                        engine.onDown(it.x, it.y)
                        engine.onMove(it.x, it.y, longPressTimeoutMs = 0)?.let(onGesture)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(astra.primary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(hint, style = MaterialTheme.typography.labelSmall, color = astra.primary)
        }
    }
}

private val density: Float = 2.75f

// ------------------------------------------------------------------ Actions

@HiltViewModel
class ActionLibraryViewModel @Inject constructor(
    private val actionRegistry: ActionRegistry,
    private val profileRepository: ProfileRepository,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _category = MutableStateFlow<ActionCategory?>(null)
    val category: StateFlow<ActionCategory?> = _category.asStateFlow()

    private val _capabilities = MutableStateFlow<Map<String, CapabilityState>>(emptyMap())
    val capabilities: StateFlow<Map<String, CapabilityState>> = _capabilities.asStateFlow()

    private val _testMessage = MutableStateFlow<String?>(null)
    val testMessage: StateFlow<String?> = _testMessage.asStateFlow()

    fun refresh() { _capabilities.value = actionRegistry.capabilitySnapshot() }

    fun setQuery(q: String) { _query.value = q }

    fun setCategory(c: ActionCategory?) { _category.value = c }

    fun actions(): List<AstraAction> =
        actionRegistry.search(_query.value).filter { _category.value == null || it.category == _category.value }

    fun runTest(actionId: String) {
        viewModelScope.launch {
            val result = actionRegistry.execute(actionId)
            _testMessage.value = when (result) {
                is com.astratouch.app.service.action.ActionResult.Success -> "Ran successfully."
                is com.astratouch.app.service.action.ActionResult.Partial -> result.explanation
                is com.astratouch.app.service.action.ActionResult.NeedsPermission -> result.explanation
                is com.astratouch.app.service.action.ActionResult.Unsupported -> result.explanation
                is com.astratouch.app.service.action.ActionResult.Failure -> result.explanation
                com.astratouch.app.service.action.ActionResult.Cancelled -> "Cancelled."
            }
            refresh()
        }
    }

    fun dismissTest() { _testMessage.value = null }

    fun addToMenu(actionId: String, add: (String) -> Unit) = add(actionId)
}

@Composable
fun ActionLibraryScreen(
    onBack: () -> Unit,
    viewModel: ActionLibraryViewModel = hiltViewModel(),
) {
    val astra = LocalAstraColors.current
    val query by viewModel.query.collectAsState()
    val category by viewModel.category.collectAsState()
    val capabilities by viewModel.capabilities.collectAsState()
    val message by viewModel.testMessage.collectAsState()
    var refreshTick by remember { mutableStateOf(0) }

    LaunchedEffect(refreshTick) { viewModel.refresh() }
    val actions = remember(query, category, capabilities) { viewModel.actions() }

    AstraScaffold(title = "Action library", subtitle = "${actions.size} actions available", onBack = onBack) { modifier ->
        Column(
            modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            androidx.compose.material3.OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                label = { Text("Search actions") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("action_library_search"),
            )
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { FilterChipLite("All", category == null) { viewModel.setCategory(null) } }
                items(ActionCategory.entries.size) { i ->
                    val c = ActionCategory.entries[i]
                    FilterChipLite(c.label, category == c) { viewModel.setCategory(c) }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (message != null) {
                AstraCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(message!!, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewModel.dismissTest() }) { Text("OK") }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (actions.isEmpty()) {
                EmptyState(
                    title = "No matching actions",
                    message = "Try another search term or clear the category filter.",
                )
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(actions.size) { i ->
                        val action = actions[i]
                        val capability = capabilities[action.id] ?: CapabilityState.AVAILABLE
                        AstraCard {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(astra.primary.copy(alpha = 0.14f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            IconRegistry.get(action.iconKey),
                                            contentDescription = null,
                                            tint = astra.primary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(action.name, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            action.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = astra.onBackground.copy(alpha = 0.65f),
                                        )
                                    }
                                    StatusPill(
                                        text = capability.label,
                                        positive = capability == CapabilityState.AVAILABLE,
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        action.category.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = astra.secondary,
                                    )
                                    if (action.requiredAccess.isNotEmpty()) {
                                        Text(
                                            "Needs: " + action.requiredAccess.joinToString { it.label },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = astra.onBackground.copy(alpha = 0.5f),
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    androidx.compose.material3.OutlinedButton(
                                        onClick = { viewModel.runTest(action.id) },
                                        enabled = capability == CapabilityState.AVAILABLE,
                                    ) { Text("Run now", style = MaterialTheme.typography.labelMedium) }
                                    TextButton(onClick = { viewModel.runTest(action.id) }) {
                                        Text("Test", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------- Quick controls

@HiltViewModel
class QuickControlsViewModel @Inject constructor(
    private val actionRegistry: ActionRegistry,
) : ViewModel() {
    private val _capabilities = MutableStateFlow<Map<String, CapabilityState>>(emptyMap())
    val capabilities: StateFlow<Map<String, CapabilityState>> = _capabilities.asStateFlow()

    private val _feedback = MutableStateFlow<String?>(null)
    val feedback: StateFlow<String?> = _feedback.asStateFlow()

    fun refresh() { _capabilities.value = actionRegistry.capabilitySnapshot() }

    fun run(id: String) {
        viewModelScope.launch {
            val result = actionRegistry.execute(id)
            _feedback.value = when (result) {
                is com.astratouch.app.service.action.ActionResult.Success -> null
                is com.astratouch.app.service.action.ActionResult.Partial -> result.explanation
                is com.astratouch.app.service.action.ActionResult.NeedsPermission -> result.explanation
                is com.astratouch.app.service.action.ActionResult.Unsupported -> result.explanation
                is com.astratouch.app.service.action.ActionResult.Failure -> result.explanation
                com.astratouch.app.service.action.ActionResult.Cancelled -> null
            }
            refresh()
        }
    }

    fun dismiss() { _feedback.value = null }

    fun actionsFor(ids: List<String>): List<AstraAction> = ids.mapNotNull { actionRegistry.byId(it) }
}

@Composable
fun QuickControlCenterScreen(
    onBack: () -> Unit,
    viewModel: QuickControlsViewModel = hiltViewModel(),
) {
    val astra = LocalAstraColors.current
    val caps by viewModel.capabilities.collectAsState()
    val feedback by viewModel.feedback.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }

    val groups = listOf(
        "Navigation" to listOf(
            "nav.home", "nav.back", "nav.recents", "nav.notifications", "nav.quick_settings", "nav.lock_screen",
        ),
        "Device" to listOf(
            "device.volume_up", "device.volume_down", "device.volume_panel",
            "device.brightness_up", "device.brightness_down", "device.flashlight",
            "device.rotation_toggle", "device.dnd_panel", "device.internet_panel",
        ),
        "Capture" to listOf("capture.screenshot", "capture.screen_record", "capture.open_last"),
        "Quick launch" to listOf(
            "productivity.calculator", "productivity.notes", "productivity.timer", "productivity.clipboard",
        ),
    )

    AstraScaffold(title = "Quick control center", subtitle = "Every action, one tap away", onBack = onBack) { modifier ->
        Column(
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            if (feedback != null) {
                AstraCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            feedback!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = astra.warning,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { viewModel.dismiss() }) { Text("OK") }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            groups.forEach { (title, ids) ->
                SectionHeader(title)
                ids.chunked(3).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { id ->
                            val action = viewModel.actionsFor(listOf(id)).firstOrNull() ?: return@forEach
                            val capability = caps[id] ?: CapabilityState.AVAILABLE
                            Box(Modifier.weight(1f)) {
                                QuickTile(
                                    label = action.name,
                                    iconKey = action.iconKey,
                                    enabled = capability == CapabilityState.AVAILABLE,
                                    onClick = { viewModel.run(id) },
                                )
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun QuickTile(label: String, iconKey: String, enabled: Boolean, onClick: () -> Unit) {
    val astra = LocalAstraColors.current
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(astra.surface)
            .border(1.dp, astra.outline.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            IconRegistry.get(iconKey),
            contentDescription = label,
            tint = if (enabled) astra.primary else astra.onBackground.copy(alpha = 0.3f),
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) astra.onBackground else astra.onBackground.copy(alpha = 0.35f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 2,
        )
    }
}
