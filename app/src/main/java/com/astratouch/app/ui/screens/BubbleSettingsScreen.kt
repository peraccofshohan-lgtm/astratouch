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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astratouch.app.data.repository.ProfileRepository
import com.astratouch.app.domain.model.AnimationStyle
import com.astratouch.app.domain.model.BubbleConfig
import com.astratouch.app.domain.model.BubbleShape
import com.astratouch.app.domain.model.DockSide
import com.astratouch.app.domain.model.SnapMode
import com.astratouch.app.service.overlay.OverlayController
import com.astratouch.app.ui.components.AstraCard
import com.astratouch.app.ui.components.AstraScaffold
import com.astratouch.app.ui.components.SectionHeader
import com.astratouch.app.ui.components.SettingRow
import com.astratouch.app.ui.overlay.IconRegistry
import com.astratouch.app.ui.overlay.bubbleShapeOf
import com.astratouch.app.ui.theme.LocalAstraColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BubbleSettingsViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
) : ViewModel() {
    private val _config = MutableStateFlow(BubbleConfig())
    val config: StateFlow<BubbleConfig> = _config.asStateFlow()

    init {
        viewModelScope.launch {
            val profile = profileRepository.getActiveProfile() ?: return@launch
            profileRepository.getBubbleConfig(profile.bubbleConfigId)?.let { _config.value = it }
        }
    }

    fun update(block: (BubbleConfig) -> BubbleConfig) {
        val next = block(_config.value)
        _config.value = next
        viewModelScope.launch {
            profileRepository.saveBubbleConfig(next)
            OverlayController.instance?.refreshConfiguration()
        }
    }

    fun reset() = update { BubbleConfig(id = it.id, profileId = it.profileId) }
}

@Composable
fun BubbleSettingsScreen(
    onBack: () -> Unit,
    viewModel: BubbleSettingsViewModel = hiltViewModel(),
) {
    val astra = LocalAstraColors.current
    val config by viewModel.config.collectAsState()

    AstraScaffold(title = "Floating button", subtitle = "Shape, size, colour and behaviour", onBack = onBack) { modifier ->
        Column(
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // Live preview
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(astra.surface, astra.background, astra.surface.copy(alpha = 0.7f)),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(config.sizeDp.dp)
                        .clip(bubbleShapeOf(config.shape, config.sizeDp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(config.colorStart.toInt()), Color(config.colorEnd.toInt())),
                            ),
                        )
                        .border(config.borderWidthDp.dp, Color.White.copy(alpha = 0.25f), bubbleShapeOf(config.shape, config.sizeDp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        IconRegistry.get(config.iconKey),
                        contentDescription = "Bubble preview",
                        tint = Color(config.iconTint.toInt()),
                        modifier = Modifier.size(config.iconSizeDp.dp),
                    )
                }
                Text(
                    "Live preview",
                    style = MaterialTheme.typography.labelSmall,
                    color = astra.onBackground.copy(alpha = 0.5f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp),
                )
            }

            SectionHeader("Shape", "Pick the outline of your bubble")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(BubbleShape.entries.size) { index ->
                    val shape = BubbleShape.entries[index]
                    val selected = config.shape == shape
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .size(48.dp)
                                .clip(bubbleShapeOf(shape, 48))
                                .background(if (selected) astra.primary else astra.surface)
                                .clickable { viewModel.update { it.copy(shape = shape) } },
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            shape.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) astra.primary else astra.onBackground.copy(alpha = 0.6f),
                        )
                    }
                }
            }

            SectionHeader("Size & opacity")
            SliderRow(
                label = "Size",
                value = config.sizeDp.toFloat(),
                range = 40f..96f,
                valueLabel = "${config.sizeDp} dp",
            ) { viewModel.update { c -> c.copy(sizeDp = it.toInt()) } }
            SliderRow(
                label = "Opacity",
                value = config.opacity,
                range = 0.3f..1f,
                valueLabel = "${(config.opacity * 100).toInt()}%",
            ) { viewModel.update { c -> c.copy(opacity = it) } }
            SliderRow(
                label = "Icon size",
                value = config.iconSizeDp.toFloat(),
                range = 14f..44f,
                valueLabel = "${config.iconSizeDp} dp",
            ) { viewModel.update { c -> c.copy(iconSizeDp = it.toInt()) } }
            SliderRow(
                label = "Edge margin",
                value = config.edgeMarginDp.toFloat(),
                range = 0f..32f,
                valueLabel = "${config.edgeMarginDp} dp",
            ) { viewModel.update { c -> c.copy(edgeMarginDp = it.toInt()) } }
            SliderRow(
                label = "Border width",
                value = config.borderWidthDp.toFloat(),
                range = 0f..6f,
                valueLabel = "${config.borderWidthDp} dp",
            ) { viewModel.update { c -> c.copy(borderWidthDp = it.toInt()) } }

            SectionHeader("Icon")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(IconRegistry.keys.size) { index ->
                    val key = IconRegistry.keys[index]
                    val selected = config.iconKey == key
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (selected) astra.primary.copy(alpha = 0.22f) else astra.surface)
                            .clickable { viewModel.update { c -> c.copy(iconKey = key) } },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(IconRegistry.get(key), contentDescription = key, tint = if (selected) astra.primary else astra.onBackground.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                    }
                }
            }

            SectionHeader("Colours")
            ColorChoices(selected = config.colorStart) { viewModel.update { c -> c.copy(colorStart = it) } }
            Spacer(Modifier.height(8.dp))
            ColorChoices(selected = config.colorEnd) { viewModel.update { c -> c.copy(colorEnd = it) } }

            SectionHeader("Behaviour")
            AstraCard {
                Column {
                    ChoiceRow(
                        label = "Snap",
                        options = SnapMode.entries.map { it.name to it.label },
                        selected = config.snapMode.name,
                    ) { name -> viewModel.update { c -> c.copy(snapMode = SnapMode.fromName(name)) } }
                    ChoiceRow(
                        label = "Dock side",
                        options = DockSide.entries.map { it.name to it.label },
                        selected = config.dockSide.name,
                    ) { name -> viewModel.update { c -> c.copy(dockSide = DockSide.fromName(name)) } }
                    ChoiceRow(
                        label = "Animation",
                        options = AnimationStyle.entries.map { it.name to it.label },
                        selected = config.animationStyle.name,
                    ) { name -> viewModel.update { c -> c.copy(animationStyle = AnimationStyle.fromName(name)) } }
                    SettingRow(
                        title = "Haptic feedback",
                        description = "A short vibration when the bubble is tapped or dragged.",
                        checked = config.hapticsEnabled,
                        onCheckedChange = { v -> viewModel.update { c -> c.copy(hapticsEnabled = v) } },
                    )
                    SettingRow(
                        title = "Auto-dim when menu is open",
                        description = "Fades the bubble so menu items stay readable.",
                        checked = config.autoDimAlpha < 0.9f,
                        onCheckedChange = { v -> viewModel.update { c -> c.copy(autoDimAlpha = if (v) 0.45f else 1f) } },
                    )
                    SettingRow(
                        title = "Show edge handle",
                        description = "A slim grab handle at the docked edge for quick access.",
                        checked = config.showEdgeHandle,
                        onCheckedChange = { v -> viewModel.update { c -> c.copy(showEdgeHandle = v) } },
                    )
                    SettingRow(
                        title = "Expandable edge panel",
                        description = "Swipe the docked edge to reveal extra shortcuts.",
                        checked = config.edgePanelEnabled,
                        onCheckedChange = { v -> viewModel.update { c -> c.copy(edgePanelEnabled = v) } },
                    )
                    SettingRow(
                        title = "Start with device",
                        description = "Restore the bubble after a reboot (requires the overlay permission).",
                        checked = config.startupOnBoot,
                        onCheckedChange = { v -> viewModel.update { c -> c.copy(startupOnBoot = v) } },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            androidx.compose.material3.OutlinedButton(
                onClick = { viewModel.reset() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Reset bubble to defaults") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
) {
    val astra = LocalAstraColors.current
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(valueLabel, style = MaterialTheme.typography.labelMedium, color = astra.primary)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.testTag("slider_$label"),
        )
    }
}

@Composable
private fun ColorChoices(selected: Long, onPick: (Long) -> Unit) {
    val palette = listOf(
        0xFF32E6E2, 0xFF7C5CFF, 0xFFFF4D9D, 0xFFFFB020, 0xFF34D399,
        0xFFF87171, 0xFF60A5FA, 0xFFE5E7EB, 0xFF111827, 0xFFFFFFFF,
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(palette.size) { index ->
            val color = palette[index]
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(color.toInt()))
                    .border(
                        width = if (selected == color) 3.dp else 1.dp,
                        color = if (selected == color) Color.White else Color.White.copy(alpha = 0.25f),
                        shape = CircleShape,
                    )
                    .clickable { onPick(color) },
            )
        }
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val astra = LocalAstraColors.current
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (key, text) ->
                val isSelected = key == selected
                Box(
                    Modifier
                        .weight(1f)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                        .background(if (isSelected) astra.primary.copy(alpha = 0.2f) else astra.background.copy(alpha = 0.5f))
                        .clickable { onSelect(key) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) astra.primary else astra.onBackground.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}
