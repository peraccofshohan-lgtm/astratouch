package com.astratouch.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astratouch.app.data.repository.ProfileRepository
import com.astratouch.app.data.repository.RecentActionRepository
import com.astratouch.app.domain.model.ActionCategory
import com.astratouch.app.domain.model.RadialConfig
import com.astratouch.app.domain.model.RadialLayout
import com.astratouch.app.domain.model.RadialMenuItem
import com.astratouch.app.service.action.ActionIcons
import com.astratouch.app.service.action.ActionRegistry
import com.astratouch.app.service.action.AstraAction
import com.astratouch.app.service.action.CapabilityState
import com.astratouch.app.service.overlay.OverlayController
import com.astratouch.app.ui.components.AstraCard
import com.astratouch.app.ui.components.AstraScaffold
import com.astratouch.app.ui.components.EmptyState
import com.astratouch.app.ui.components.SectionHeader
import com.astratouch.app.ui.components.SettingRow
import com.astratouch.app.ui.overlay.IconRegistry
import com.astratouch.app.ui.overlay.RadialLayoutMath
import com.astratouch.app.ui.theme.LocalAstraColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RadialEditorViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val actionRegistry: ActionRegistry,
    private val recentActionRepository: RecentActionRepository,
) : ViewModel() {
    private val _config = MutableStateFlow(RadialConfig())
    val config: StateFlow<RadialConfig> = _config.asStateFlow()

    private val _items = MutableStateFlow<List<RadialMenuItem>>(emptyList())
    val items: StateFlow<List<RadialMenuItem>> = _items.asStateFlow()

    private val _recents = MutableStateFlow<List<String>>(emptyList())
    val recents: StateFlow<List<String>> = _recents.asStateFlow()

    private val _page = MutableStateFlow(0)
    val page: StateFlow<Int> = _page.asStateFlow()

    var radialId: String = "radial-default"

    init {
        viewModelScope.launch {
            val profile = profileRepository.getActiveProfile() ?: return@launch
            radialId = profile.radialConfigId
            profileRepository.getRadialConfig(profile.radialConfigId)?.let { _config.value = it }
            profileRepository.observeRadialItems(profile.radialConfigId).collect { _items.value = it }
        }
        viewModelScope.launch {
            recentActionRepository.observeRecent().collect { list -> _recents.value = list.map { it.actionId } }
        }
    }

    fun setPage(p: Int) { _page.value = p.coerceAtLeast(0) }

    fun update(block: (RadialConfig) -> RadialConfig) {
        val next = block(_config.value)
        _config.value = next
        viewModelScope.launch {
            profileRepository.saveRadialConfig(next)
            OverlayController.instance?.refreshConfiguration()
        }
    }

    fun addAction(actionId: String) {
        val current = _items.value
        val pages = config.value.layout
        val page = _page.value
        val used = current.filter { it.page == page }.map { it.slot }.toSet()
        val capacity = RadialLayout.fromName(pages.name).capacity
        val slot = (0 until capacity).firstOrNull { it !in used } ?: return
        val next = current + RadialMenuItem(
            id = "$radialId-$page-$slot-${System.currentTimeMillis()}",
            radialConfigId = radialId,
            page = page,
            slot = slot,
            actionId = actionId,
            isFavorite = false,
        )
        persist(next)
    }

    fun removeItem(item: RadialMenuItem) = persist(_items.value.filterNot { it.id == item.id })

    fun toggleFavorite(item: RadialMenuItem) = persist(
        _items.value.map { if (it.id == item.id) it.copy(isFavorite = !it.isFavorite) else it },
    )

    fun moveItem(item: RadialMenuItem, direction: Int) {
        val pageItems = _items.value.filter { it.page == item.page }.sortedBy { it.slot }
        val index = pageItems.indexOfFirst { it.id == item.id }
        val target = index + direction
        if (index < 0 || target < 0 || target >= pageItems.size) return
        val a = pageItems[index]
        val b = pageItems[target]
        val next = _items.value.map {
            when (it.id) {
                a.id -> it.copy(slot = b.slot)
                b.id -> it.copy(slot = a.slot)
                else -> it
            }
        }
        persist(next)
    }

    fun rename(item: RadialMenuItem, label: String) = persist(
        _items.value.map { if (it.id == item.id) it.copy(label = label) else it },
    )

    fun setItemIcon(item: RadialMenuItem, iconKey: String) = persist(
        _items.value.map { if (it.id == item.id) it.copy(iconKey = iconKey) else it },
    )

    fun fillFavorites() {
        val already = _items.value.map { it.actionId }.toSet()
        actionRegistry.all()
            .filter { it.category != ActionCategory.ASTRA && it.id !in already }
            .take(8 - _items.value.count { it.page == _page.value })
            .forEach { addAction(it.id) }
    }

    fun resetLayout() {
        val primary = listOf(
            "nav.home", "nav.back", "nav.recents", "nav.notifications",
            "device.volume_panel", "capture.screenshot", "astra.clipboard_manager", "astra.settings",
        )
        val next = primary.mapIndexed { index, actionId ->
            RadialMenuItem(
                id = "$radialId-0-$index-reset",
                radialConfigId = radialId,
                page = 0,
                slot = index,
                actionId = actionId,
            )
        }
        persist(next)
    }

    private fun persist(items: List<RadialMenuItem>) {
        _items.value = items
        viewModelScope.launch {
            profileRepository.saveRadialItems(radialId, items)
            OverlayController.instance?.refreshConfiguration()
        }
    }

    val allActions: List<AstraAction> get() = actionRegistry.all()

    fun capabilityOf(id: String): CapabilityState = actionRegistry.capabilityOf(id)
}

@Composable
fun RadialEditorScreen(
    onBack: () -> Unit,
    viewModel: RadialEditorViewModel = hiltViewModel(),
) {
    val astra = LocalAstraColors.current
    val config by viewModel.config.collectAsState()
    val items by viewModel.items.collectAsState()
    val page by viewModel.page.collectAsState()
    val recents by viewModel.recents.collectAsState()
    var showPicker by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<RadialMenuItem?>(null) }

    val pageItems = items.filter { it.page == page }.sortedBy { it.slot }
    val positions = remember(config.layout, config.radiusDp, pageItems.map { it.id }) {
        RadialLayoutMath.positions(
            layout = config.layout,
            count = pageItems.size,
            radiusDp = config.radiusDp,
            itemSizeDp = config.itemSizeDp,
        )
    }
    val panelSize = config.radiusDp * 2 + config.itemSizeDp

    AstraScaffold(title = "Radial menu", subtitle = "Eight layouts, fully customisable", onBack = onBack) { modifier ->
        Column(
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // Preview
            Box(
                Modifier
                    .fillMaxWidth()
                    .height((panelSize + 80).dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Brush.radialGradient(listOf(astra.surface, astra.background))),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(panelSize.dp), contentAlignment = Alignment.Center) {
                    pageItems.forEachIndexed { index, item ->
                        val pos = positions.getOrNull(index) ?: return@forEachIndexed
                        Box(
                            Modifier
                                .size(config.itemSizeDp.dp)
                                .padding(2.dp)
                                .offsetDp(pos.first, pos.second)
                                .clip(CircleShape)
                                .background(astra.surface)
                                .border(1.dp, astra.primary.copy(alpha = 0.35f), CircleShape)
                                .clickable { editing = item },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                IconRegistry.get(item.iconKey.ifBlank { ActionIcons.iconFor(item.actionId) }),
                                contentDescription = item.label.ifBlank { item.actionId },
                                tint = astra.primary,
                                modifier = Modifier.size((config.itemSizeDp * 0.45f).dp),
                            )
                        }
                    }
                    if (pageItems.isEmpty()) {
                        Text(
                            "Add actions to build your menu",
                            style = MaterialTheme.typography.bodySmall,
                            color = astra.onBackground.copy(alpha = 0.6f),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Tap any item to rename, re-icon, favourite or remove it.",
                style = MaterialTheme.typography.labelSmall,
                color = astra.onBackground.copy(alpha = 0.55f),
            )

            SectionHeader("Layout")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(RadialLayout.entries.size) { index ->
                    val layout = RadialLayout.entries[index]
                    val selected = config.layout == layout
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) astra.primary.copy(alpha = 0.2f) else astra.surface)
                                .clickable {
                                    viewModel.update { it.copy(layout = layout) }
                                    viewModel.setPage(0)
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Text(
                                layout.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) astra.primary else astra.onBackground.copy(alpha = 0.75f),
                            )
                        }
                    }
                }
            }

            if (config.layout.supportsPaging) {
                Spacer(Modifier.height(12.dp))
                Text("Page ${page + 1}", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { viewModel.setPage(page - 1) }, enabled = page > 0) { Text("Previous") }
                    TextButton(onClick = { viewModel.setPage(page + 1) }) { Text("Next page") }
                }
            }

            Spacer(Modifier.height(8.dp))
            SliderRowSimple("Radius", config.radiusDp.toFloat(), 56f..140f, "${config.radiusDp} dp") {
                viewModel.update { c -> c.copy(radiusDp = it.toInt()) }
            }
            SliderRowSimple("Item size", config.itemSizeDp.toFloat(), 36f..72f, "${config.itemSizeDp} dp") {
                viewModel.update { c -> c.copy(itemSizeDp = it.toInt()) }
            }
            SliderRowSimple("Background opacity", config.backgroundOpacity, 0.4f..1f, "${(config.backgroundOpacity * 100).toInt()}%") {
                viewModel.update { c -> c.copy(backgroundOpacity = it) }
            }
            SliderRowSimple("Blur radius", config.blurRadiusDp.toFloat(), 0f..40f, "${config.blurRadiusDp} dp") {
                viewModel.update { c -> c.copy(blurRadiusDp = it.toInt()) }
            }
            SettingRow(
                title = "Show labels",
                description = "Draw a text label under each icon.",
                checked = config.showLabels,
                onCheckedChange = { v -> viewModel.update { c -> c.copy(showLabels = v) } },
            )
            SettingRow(
                title = "Haptics",
                description = "Vibrate when a menu item is tapped.",
                checked = config.hapticsEnabled,
                onCheckedChange = { v -> viewModel.update { c -> c.copy(hapticsEnabled = v) } },
            )

            SectionHeader("Menu content", "${pageItems.size} of ${config.layout.capacity} slots used on this page")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                androidx.compose.material3.Button(
                    onClick = { showPicker = true },
                    modifier = Modifier.weight(1f),
                ) { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Add action") }
                androidx.compose.material3.OutlinedButton(
                    onClick = { viewModel.fillFavorites() },
                    modifier = Modifier.weight(1f),
                ) { Text("Auto-fill") }
            }
            Spacer(Modifier.height(8.dp))

            if (pageItems.isEmpty()) {
                AstraCard {
                    Text(
                        "This page is empty. Add actions from the library, or tap Auto-fill to start from favourites.",
                        style = MaterialTheme.typography.bodySmall,
                        color = astra.onBackground.copy(alpha = 0.7f),
                    )
                }
            } else {
                pageItems.forEach { item ->
                    AstraCard(Modifier.padding(bottom = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(astra.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    IconRegistry.get(item.iconKey.ifBlank { ActionIcons.iconFor(item.actionId) }),
                                    contentDescription = null,
                                    tint = astra.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.label.ifBlank { actionName(viewModel, item.actionId) },
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    item.actionId,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = astra.onBackground.copy(alpha = 0.5f),
                                )
                            }
                            if (item.isFavorite) {
                                Icon(
                                    com.astratouch.app.ui.overlay.IconRegistry.get("ic_star"),
                                    contentDescription = "Favourite",
                                    tint = astra.warning,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            TextButton(onClick = { viewModel.moveItem(item, -1) }) { Text("↑") }
                            TextButton(onClick = { viewModel.moveItem(item, 1) }) { Text("↓") }
                            androidx.compose.material3.IconButton(onClick = { viewModel.removeItem(item) }) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            if (recents.isNotEmpty()) {
                SectionHeader("Recently used", "Quick re-add")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(recents.size) { i ->
                        val id = recents[i]
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(astra.surface)
                                .clickable { viewModel.addAction(id) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) { Text(actionName(viewModel, id), style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.OutlinedButton(
                onClick = { viewModel.resetLayout() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Reset menu to defaults") }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showPicker) {
        ActionPickerDialog(
            onDismiss = { showPicker = false },
            onPick = { id ->
                viewModel.addAction(id)
                showPicker = false
            },
            capabilityOf = viewModel::capabilityOf,
            actions = viewModel.allActions,
        )
    }

    editing?.let { item ->
        ItemEditDialog(
            item = item,
            title = actionName(viewModel, item.actionId),
            onDismiss = { editing = null },
            onLabel = { label -> viewModel.rename(item, label); editing = null },
            onIcon = { key -> viewModel.setItemIcon(item, key); editing = null },
            onToggleFavorite = { viewModel.toggleFavorite(item); editing = null },
            onRemove = { viewModel.removeItem(item); editing = null },
        )
    }
}

private fun actionName(viewModel: RadialEditorViewModel, id: String): String =
    viewModel.allActions.firstOrNull { it.id == id }?.name ?: id

private fun Modifier.offsetDp(x: Float, y: Float): Modifier =
    this.then(androidx.compose.foundation.layout.offset(x = x.dp, y = y.dp))

@Composable
private fun SliderRowSimple(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onChange: (Float) -> Unit,
) {
    val astra = LocalAstraColors.current
    Column(Modifier.padding(vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(valueLabel, style = MaterialTheme.typography.labelMedium, color = astra.primary)
        }
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range)
    }
}

@Composable
fun ActionPickerDialog(
    actions: List<AstraAction>,
    capabilityOf: (String) -> CapabilityState,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<ActionCategory?>(null) }
    val filtered = actions.filter { action ->
        (category == null || action.category == category) &&
            (query.isBlank() || action.name.contains(query, true) || action.description.contains(query, true))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add action") },
        text = {
            Column(Modifier.height(420.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search actions") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("action_search"),
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        FilterChipLite("All", category == null) { category = null }
                    }
                    items(ActionCategory.entries.size) { i ->
                        val c = ActionCategory.entries[i]
                        FilterChipLite(c.label, category == c) { category = c }
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (filtered.isEmpty()) {
                    EmptyState("No actions match", "Try a different search or category.")
                } else {
                    LazyColumn(Modifier.fillMaxWidth()) {
                        items(filtered.size) { i ->
                            val action = filtered[i]
                            val cap = capabilityOf(action.id)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = cap != CapabilityState.UNSUPPORTED_OS) { onPick(action.id) }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    IconRegistry.get(action.iconKey),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(action.name, style = MaterialTheme.typography.titleSmall)
                                    if (cap != CapabilityState.AVAILABLE) {
                                        Text(
                                            cap.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (cap == CapabilityState.UNSUPPORTED_OS) {
                                                LocalAstraColors.current.danger
                                            } else {
                                                LocalAstraColors.current.warning
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
fun FilterChipLite(label: String, selected: Boolean, onClick: () -> Unit) {
    val astra = LocalAstraColors.current
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) astra.primary.copy(alpha = 0.2f) else astra.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) astra.primary else astra.onBackground.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun ItemEditDialog(
    item: RadialMenuItem,
    title: String,
    onDismiss: () -> Unit,
    onLabel: (String) -> Unit,
    onIcon: (String) -> Unit,
    onToggleFavorite: () -> Unit,
    onRemove: () -> Unit,
) {
    var label by remember { mutableStateOf(item.label) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Custom label (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Text("Icon", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(IconRegistry.keys.size) { i ->
                        val key = IconRegistry.keys[i]
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(LocalAstraColors.current.surface)
                                .clickable { onIcon(key) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(IconRegistry.get(key), contentDescription = key, modifier = Modifier.size(16.dp)) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onLabel(label) }) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onToggleFavorite) { Text(if (item.isFavorite) "Unfavourite" else "Favourite") }
                TextButton(onClick = onRemove) { Text("Remove") }
            }
        },
    )
}
