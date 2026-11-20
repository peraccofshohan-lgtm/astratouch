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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astratouch.app.data.repository.AppsRepository
import com.astratouch.app.data.repository.ProfileRepository
import com.astratouch.app.domain.model.LauncherApp
import com.astratouch.app.domain.model.Profile
import com.astratouch.app.domain.model.ProfileType
import com.astratouch.app.domain.model.ThemeConfig
import com.astratouch.app.domain.model.ThemePreset
import com.astratouch.app.service.overlay.OverlayController
import com.astratouch.app.ui.components.AstraCard
import com.astratouch.app.ui.components.AstraScaffold
import com.astratouch.app.ui.components.EmptyState
import com.astratouch.app.ui.components.LoadingState
import com.astratouch.app.ui.components.SectionHeader
import com.astratouch.app.ui.components.SettingRow
import com.astratouch.app.ui.components.StatusPill
import com.astratouch.app.ui.theme.LocalAstraColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

// ------------------------------------------------------------ App launcher

@HiltViewModel
class AppLauncherViewModel @Inject constructor(
    private val appsRepository: AppsRepository,
) : ViewModel() {
    private val _apps = MutableStateFlow<List<LauncherApp>>(emptyList())
    val apps: StateFlow<List<LauncherApp>> = _apps.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _onlyFavorites = MutableStateFlow(false)
    val onlyFavorites: StateFlow<Boolean> = _onlyFavorites.asStateFlow()

    private val _groupFilter = MutableStateFlow<String?>(null)
    val groupFilter: StateFlow<String?> = _groupFilter.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init { reload() }

    fun reload() {
        viewModelScope.launch {
            _loading.value = true
            _apps.value = appsRepository.discoverApps()
            _loading.value = false
        }
    }

    fun setQuery(q: String) { _query.value = q }
    fun toggleFavoritesOnly() { _onlyFavorites.value = !_onlyFavorites.value }
    fun setGroup(g: String?) { _groupFilter.value = g }

    fun launch(app: LauncherApp) {
        viewModelScope.launch {
            if (!appsRepository.launch(app.packageName)) {
                _message.value = "Could not launch ${app.label}. It may have been uninstalled."
            }
        }
    }

    fun toggleFavorite(app: LauncherApp) {
        viewModelScope.launch {
            appsRepository.toggleFavorite(app.packageName, !app.isFavorite)
            reload()
        }
    }

    fun setGroup(app: LauncherApp, group: String) {
        viewModelScope.launch {
            appsRepository.setGroup(app.packageName, group)
            reload()
        }
    }

    fun openDetails(app: LauncherApp) {
        viewModelScope.launch { appsRepository.openAppDetails(app.packageName) }
    }

    fun dismiss() { _message.value = null }

    fun filtered(): List<LauncherApp> = _apps.value.filter { app ->
        (!_onlyFavorites.value || app.isFavorite) &&
            (_groupFilter.value == null || app.group == _groupFilter.value) &&
            (_query.value.isBlank() || app.label.contains(_query.value, true) ||
                app.packageName.contains(_query.value, true))
    }

    fun groups(): List<String> = _apps.value.map { it.group }.filter { it.isNotBlank() }.distinct().sorted()
}

@Composable
fun AppLauncherScreen(
    onBack: () -> Unit,
    viewModel: AppLauncherViewModel = hiltViewModel(),
) {
    val astra = LocalAstraColors.current
    val apps by viewModel.apps.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val query by viewModel.query.collectAsState()
    val onlyFavorites by viewModel.onlyFavorites.collectAsState()
    val groupFilter by viewModel.groupFilter.collectAsState()
    val message by viewModel.message.collectAsState()
    var grouping by remember { mutableStateOf<LauncherApp?>(null) }
    var groupInput by remember { mutableStateOf("") }

    val filtered = remember(apps, query, onlyFavorites, groupFilter) { viewModel.filtered() }
    val groups = remember(apps) { viewModel.groups() }

    AstraScaffold(
        title = "App launcher",
        subtitle = "${apps.size} installed apps",
        onBack = onBack,
        actions = {
            TextButton(onClick = { viewModel.reload() }) { Text("Refresh") }
        },
    ) { modifier ->
        Column(
            modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                label = { Text("Search apps") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("app_search"),
            )
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    FilterChipLite(if (onlyFavorites) "★ Favourites" else "☆ Favourites", onlyFavorites) {
                        viewModel.toggleFavoritesOnly()
                    }
                }
                if (groups.isNotEmpty()) {
                    item { FilterChipLite("All groups", groupFilter == null) { viewModel.setGroup(null) } }
                    items(groups.size) { i ->
                        FilterChipLite(groups[i], groupFilter == groups[i]) { viewModel.setGroup(groups[i]) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (message != null) {
                AstraCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(message!!, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewModel.dismiss() }) { Text("OK") }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            when {
                loading -> LoadingState("Scanning installed apps…")
                apps.isEmpty() -> EmptyState(
                    title = "No launchable apps found",
                    message = "PackageManager returned no launcher activities. This is unusual — try Refresh.",
                )
                filtered.isEmpty() -> EmptyState(
                    title = "No apps match",
                    message = "Clear the search or the favourites filter to see all ${apps.size} apps.",
                )
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(filtered.size) { i ->
                        val app = filtered[i]
                        AstraCard(onClick = { viewModel.launch(app) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(astra.primary.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        app.label.take(1).uppercase(),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = astra.primary,
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(app.label, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                    Text(
                                        app.packageName + if (app.group.isNotBlank()) " · ${app.group}" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = astra.onBackground.copy(alpha = 0.5f),
                                        maxLines = 1,
                                    )
                                }
                                androidx.compose.material3.IconButton(onClick = { viewModel.toggleFavorite(app) }) {
                                    Icon(
                                        if (app.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                        contentDescription = "Favourite",
                                        tint = if (app.isFavorite) astra.warning else astra.onBackground.copy(alpha = 0.4f),
                                    )
                                }
                                TextButton(onClick = { grouping = app; groupInput = app.group }) { Text("Group") }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    grouping?.let { app ->
        AlertDialog(
            onDismissRequest = { grouping = null },
            title = { Text("Group ${app.label}") },
            text = {
                OutlinedTextField(
                    value = groupInput,
                    onValueChange = { groupInput = it },
                    label = { Text("Group name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setGroup(app, groupInput.trim())
                    grouping = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { grouping = null }) { Text("Cancel") } },
        )
    }
}

// --------------------------------------------------------- Profile manager

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val appsRepository: AppsRepository,
) : ViewModel() {
    private val _profiles = MutableStateFlow<List<Profile>>(emptyList())
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _availableApps = MutableStateFlow<List<LauncherApp>>(emptyList())
    val availableApps: StateFlow<List<LauncherApp>> = _availableApps.asStateFlow()

    init {
        viewModelScope.launch {
            profileRepository.observeProfiles().collect { _profiles.value = it }
        }
        viewModelScope.launch {
            _availableApps.value = appsRepository.discoverApps()
        }
    }

    fun activate(profile: Profile) {
        viewModelScope.launch {
            profileRepository.setActiveProfile(profile.id)
            OverlayController.instance?.refreshConfiguration()
            _message.value = "${profile.name} is now the active profile."
        }
    }

    fun createCustom(name: String) {
        viewModelScope.launch {
            if (name.isBlank()) {
                _message.value = "Give the profile a name."
                return@launch
            }
            val id = "custom-" + UUID.randomUUID().toString().take(6)
            val now = System.currentTimeMillis()
            val themeId = "theme-custom"
            val bubbleId = "bubble-$id"
            val radialId = "radial-$id"
            profileRepository.saveBubbleConfig(
                com.astratouch.app.domain.model.BubbleConfig(id = bubbleId, profileId = id),
            )
            profileRepository.saveRadialConfig(
                com.astratouch.app.domain.model.RadialConfig(id = radialId, profileId = id),
            )
            profileRepository.saveProfile(
                Profile(
                    id = id,
                    type = ProfileType.CUSTOM,
                    name = name,
                    description = "Your custom profile.",
                    themeId = themeId,
                    bubbleConfigId = bubbleId,
                    radialConfigId = radialId,
                    createdAt = now,
                    updatedAt = now,
                    sortOrder = _profiles.value.size,
                ),
            )
            _message.value = "Created \"$name\"."
        }
    }

    fun delete(profile: Profile) {
        viewModelScope.launch {
            if (profile.type != ProfileType.CUSTOM) {
                _message.value = "Built-in profiles cannot be deleted."
                return@launch
            }
            profileRepository.deleteProfile(profile.id)
            _message.value = "Deleted ${profile.name}."
        }
    }

    fun setAutoSwitch(profile: Profile, packageName: String?) {
        viewModelScope.launch {
            profileRepository.saveProfile(profile.copy(autoSwitchPackage = packageName?.ifBlank { null }))
            _message.value = if (packageName.isNullOrBlank()) {
                "Auto-switch cleared for ${profile.name}."
            } else {
                "${profile.name} activates automatically in ${appsRepository.appLabel(packageName)}."
            }
        }
    }

    fun rename(profile: Profile, name: String) {
        viewModelScope.launch {
            if (name.isBlank()) return@launch
            profileRepository.saveProfile(profile.copy(name = name, updatedAt = System.currentTimeMillis()))
        }
    }

    fun dismiss() { _message.value = null }

    fun apps(): List<LauncherApp> = _availableApps.value
}

@Composable
fun ProfileManagerScreen(
    onBack: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val astra = LocalAstraColors.current
    val profiles by viewModel.profiles.collectAsState()
    val message by viewModel.message.collectAsState()
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    AstraScaffold(
        title = "Profiles",
        subtitle = "Independent bubble, menu, gestures, macros and theme",
        onBack = onBack,
        actions = {
            androidx.compose.material3.IconButton(onClick = { creating = true }) {
                Icon(Icons.Filled.Add, contentDescription = "New profile")
            }
        },
    ) { modifier ->
        Column(
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            if (message != null) {
                AstraCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(message!!, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewModel.dismiss() }) { Text("OK") }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            profiles.forEach { profile ->
                AstraCard(Modifier.padding(bottom = 10.dp)) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                profile.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            if (profile.isActive) StatusPill("Active", positive = true)
                            if (!profile.type.isBuiltIn) {
                                TextButton(onClick = { viewModel.delete(profile) }) { Text("Delete") }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            profile.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = astra.onBackground.copy(alpha = 0.65f),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Auto-switch: " + (profile.autoSwitchPackage ?: "off"),
                            style = MaterialTheme.typography.labelSmall,
                            color = astra.secondary,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            androidx.compose.material3.Button(
                                onClick = { viewModel.activate(profile) },
                                enabled = !profile.isActive,
                            ) { Text(if (profile.isActive) "In use" else "Activate") }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (creating) {
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("New custom profile") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Profile name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.createCustom(newName.trim())
                    newName = ""
                    creating = false
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Cancel") } },
        )
    }
}

// ------------------------------------------------------------ Theme studio

@HiltViewModel
class ThemeStudioViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
) : ViewModel() {
    private val _themes = MutableStateFlow<List<ThemeConfig>>(emptyList())
    val themes: StateFlow<List<ThemeConfig>> = _themes.asStateFlow()

    private val _current = MutableStateFlow(ThemeConfig.fromPreset(ThemePreset.MIDNIGHT_OBSIDIAN))
    val current: StateFlow<ThemeConfig> = _current.asStateFlow()

    private val _activeThemeId = MutableStateFlow("theme-midnight_obsidian")
    val activeThemeId: StateFlow<String> = _activeThemeId.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            val profile = profileRepository.getActiveProfile()
            val id = profile?.themeId ?: "theme-midnight_obsidian"
            _activeThemeId.value = id
            profileRepository.getTheme(id)?.let { _current.value = it }
            profileRepository.observeThemes().collect { _themes.value = it }
        }
    }

    fun applyPreset(preset: ThemePreset) {
        val next = ThemeConfig.fromPreset(preset).copy(id = _activeThemeId.value)
        _current.value = next
        viewModelScope.launch {
            profileRepository.saveTheme(next)
            _message.value = "${preset.label} applied."
            OverlayController.instance?.refreshConfiguration()
        }
    }

    fun update(block: (ThemeConfig) -> ThemeConfig) {
        val next = block(_current.value)
        _current.value = next
        viewModelScope.launch {
            profileRepository.saveTheme(next)
            OverlayController.instance?.refreshConfiguration()
        }
    }

    fun dismiss() { _message.value = null }
}

@Composable
fun ThemeStudioScreen(
    onBack: () -> Unit,
    viewModel: ThemeStudioViewModel = hiltViewModel(),
) {
    val astra = LocalAstraColors.current
    val themes by viewModel.themes.collectAsState()
    val current by viewModel.current.collectAsState()
    val message by viewModel.message.collectAsState()

    AstraScaffold(title = "Theme studio", subtitle = "Five presets plus full custom control", onBack = onBack) { modifier ->
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
                    .height(150.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(current.background.toInt()),
                                Color(current.surface.toInt()),
                            ),
                        ),
                    )
                    .border(1.dp, Color(current.primary.toInt()).copy(alpha = 0.4f), RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(current.primary.toInt()), Color(current.secondary.toInt())),
                                ),
                            ),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        current.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color(current.onBackground.toInt()),
                    )
                    Text(
                        "Accent · Surface · Background",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(current.onBackground.toInt()).copy(alpha = 0.6f),
                    )
                }
            }
            if (message != null) {
                Spacer(Modifier.height(8.dp))
                Text(message!!, style = MaterialTheme.typography.labelMedium, color = astra.success)
            }

            SectionHeader("Presets")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(ThemePreset.entries.size) { i ->
                    val preset = ThemePreset.entries[i]
                    val selected = current.presetId == preset.id
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .size(width = 96.dp, height = 62.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(preset.background.toInt()), Color(preset.surface.toInt())),
                                    ),
                                )
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) Color(preset.primary.toInt()) else Color.White.copy(alpha = 0.18f),
                                    shape = RoundedCornerShape(14.dp),
                                )
                                .clickable { viewModel.applyPreset(preset) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(Color(preset.primary.toInt())),
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            preset.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) astra.primary else astra.onBackground.copy(alpha = 0.65f),
                        )
                    }
                }
            }

            SectionHeader("Custom colours", "Edits apply to the active profile immediately")
            ColorRow("Primary", current.primary) { c -> viewModel.update { it.copy(primary = c, presetId = ThemePreset.CUSTOM.id) } }
            ColorRow("Secondary", current.secondary) { c -> viewModel.update { it.copy(secondary = c, presetId = ThemePreset.CUSTOM.id) } }
            ColorRow("Tertiary", current.tertiary) { c -> viewModel.update { it.copy(tertiary = c, presetId = ThemePreset.CUSTOM.id) } }
            ColorRow("Background", current.background) { c -> viewModel.update { it.copy(background = c, presetId = ThemePreset.CUSTOM.id) } }
            ColorRow("Surface", current.surface) { c -> viewModel.update { it.copy(surface = c, presetId = ThemePreset.CUSTOM.id) } }

            SectionHeader("Shape & motion")
            SliderLabeled("Corner radius", current.cornerRadiusDp.toFloat(), 0f..36f, "${current.cornerRadiusDp} dp") {
                viewModel.update { t -> t.copy(cornerRadiusDp = it.toInt()) }
            }
            SliderLabeled("Text scale", current.typographyScale, 0.85f..1.3f, "%.2fx".format(current.typographyScale)) {
                viewModel.update { t -> t.copy(typographyScale = it) }
            }
            SliderLabeled("Animation speed", current.animationSpeed, 0.5f..2f, "%.1fx".format(current.animationSpeed)) {
                viewModel.update { t -> t.copy(animationSpeed = it) }
            }
            SliderLabeled("Haptic intensity", current.hapticIntensity, 0f..1f, "%.2f".format(current.hapticIntensity)) {
                viewModel.update { t -> t.copy(hapticIntensity = it) }
            }
            SettingRow(
                title = "Monospace typography",
                description = "Use a monospaced font for labels and numbers.",
                checked = current.useMonospace,
                onCheckedChange = { v -> viewModel.update { it.copy(useMonospace = v) } },
            )
            SettingRow(
                title = "Dark theme",
                description = "Controls the Material colour scheme direction.",
                checked = current.isDark,
                onCheckedChange = { v -> viewModel.update { it.copy(isDark = v) } },
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ColorRow(label: String, value: Long, onPick: (Long) -> Unit) {
    val astra = LocalAstraColors.current
    val palette = listOf(
        0xFF32E6E2, 0xFF7C5CFF, 0xFFFF4D9D, 0xFF00F0FF, 0xFFB388FF,
        0xFF0F6FE0, 0xFF34D399, 0xFFFBBF24, 0xFFF87171, 0xFF0B0D14,
    )
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(value.toInt())),
            )
        }
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(palette.size) { i ->
                val c = palette[i]
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(c.toInt()))
                        .border(
                            width = if (value == c) 3.dp else 1.dp,
                            color = if (value == c) astra.onBackground else Color.White.copy(alpha = 0.2f),
                            shape = CircleShape,
                        )
                        .clickable { onPick(c) },
                )
            }
        }
    }
}

@Composable
private fun SliderLabeled(
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
