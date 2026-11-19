package com.astratouch.app.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astratouch.app.BuildConfig
import com.astratouch.app.data.backup.BackupManager
import com.astratouch.app.data.backup.ExportResult
import com.astratouch.app.data.backup.ImportResult
import com.astratouch.app.data.prefs.PreferencesStore
import com.astratouch.app.data.repository.ProfileRepository
import com.astratouch.app.domain.model.AccessId
import com.astratouch.app.domain.model.PermissionSnapshot
import com.astratouch.app.permission.PermissionManager
import com.astratouch.app.service.FloatingOverlayService
import com.astratouch.app.service.action.ActionRegistry
import com.astratouch.app.service.overlay.OverlayController
import com.astratouch.app.ui.components.AstraCard
import com.astratouch.app.ui.components.AstraPrimaryButton
import com.astratouch.app.ui.components.AstraScaffold
import com.astratouch.app.ui.components.AstraSecondaryButton
import com.astratouch.app.ui.components.SectionHeader
import com.astratouch.app.ui.components.SettingRow
import com.astratouch.app.ui.components.StatusPill
import com.astratouch.app.ui.theme.LocalAstraColors
import com.astratouch.app.util.NavRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

// -------------------------------------------------------- Permission center

@HiltViewModel
class PermissionCenterViewModel @Inject constructor(
    private val permissionManager: PermissionManager,
) : ViewModel() {
    private val _snapshot = MutableStateFlow(permissionManager.current())
    val snapshot: StateFlow<PermissionSnapshot> = _snapshot.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun refresh() { _snapshot.value = permissionManager.refresh() }

    fun request(id: AccessId) {
        if (!permissionManager.requestAccess(id)) {
            _message.value = "Android did not open that screen. Open Settings and search for AstraTouch."
        }
    }

    fun dismiss() { _message.value = null }
}

@Composable
fun PermissionCenterScreen(
    onBack: () -> Unit,
    viewModel: PermissionCenterViewModel = hiltViewModel(),
) {
    val astra = LocalAstraColors.current
    val snapshot by viewModel.snapshot.collectAsState()
    val message by viewModel.message.collectAsState()

    LaunchedEffect(Unit) { viewModel.refresh() }

    AstraScaffold(title = "Permission center", subtitle = "Every access, why it exists, and what happens without it", onBack = onBack) { modifier ->
        Column(
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            AstraCard {
                Column {
                    Text(
                        "${snapshot.requiredGrantedCount} of ${snapshot.requiredTotal} required access granted",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "AstraTouch never asks for a permission it does not use. Each card lists the exact " +
                            "features that break when the access is missing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = astra.onBackground.copy(alpha = 0.68f),
                    )
                }
            }
            if (message != null) {
                Spacer(Modifier.height(8.dp))
                Text(message!!, style = MaterialTheme.typography.labelMedium, color = astra.warning)
            }
            Spacer(Modifier.height(10.dp))

            snapshot.statuses.forEach { status ->
                val d = status.descriptor
                AstraCard(Modifier.padding(bottom = 10.dp)) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(d.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            StatusPill(
                                text = if (status.state.isGranted) "Granted" else if (d.optional) "Optional" else "Required",
                                positive = status.state.isGranted,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(d.purpose, style = MaterialTheme.typography.bodySmall, color = astra.onBackground.copy(alpha = 0.72f))
                        Spacer(Modifier.height(6.dp))
                        Text("Enables: " + d.dependentFeatures.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = astra.secondary)
                        Spacer(Modifier.height(4.dp))
                        Text("Without it: " + d.fallback, style = MaterialTheme.typography.labelSmall, color = astra.onBackground.copy(alpha = 0.55f))
                        Spacer(Modifier.height(10.dp))
                        if (!status.state.isGranted) {
                            AstraSecondaryButton(
                                text = "Open ${d.title} settings",
                                onClick = { viewModel.request(d.id) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ----------------------------------------------------------- Privacy center

@HiltViewModel
class PrivacyViewModel @Inject constructor(
    private val backupManager: BackupManager,
    private val preferencesStore: PreferencesStore,
    private val profileRepository: ProfileRepository,
) : ViewModel() {
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _pendingExport = MutableStateFlow<File?>(null)
    val pendingExport: StateFlow<File?> = _pendingExport.asStateFlow()

    fun export(): File? {
        viewModelScope.launch {
            _busy.value = true
            val file = backupManager.exportToCache(BuildConfig.ASTRA_VERSION_NAME, BuildConfig.ASTRA_VERSION_CODE)
            _pendingExport.value = file
            _message.value = if (file != null) {
                "Backup ready (${file.length() / 1024} KB). Use Share to save it somewhere safe."
            } else {
                "Export failed."
            }
            _busy.value = false
        }
        return null
    }

    fun import(uri: android.net.Uri) {
        viewModelScope.launch {
            _busy.value = true
            when (val result = backupManager.importFrom(uri)) {
                is ImportResult.Success -> {
                    _message.value = "Imported ${result.importedProfiles} profiles, ${result.importedMacros} macros, " +
                        "${result.importedThemes} themes."
                    profileRepository.ensureSeeded()
                }
                is ImportResult.Failure -> _message.value = result.reason
            }
            _busy.value = false
        }
    }

    fun wipe(context: android.content.Context, onDone: () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            FloatingOverlayService.stop(context)
            backupManager.wipeAll()
            _message.value = "All AstraTouch data was deleted from this device."
            _busy.value = false
            onDone()
        }
    }

    fun dismiss() { _message.value = null }

    fun clearPending() { _pendingExport.value = null }
}

@Composable
fun PrivacyCenterScreen(
    onBack: () -> Unit,
    viewModel: PrivacyViewModel = hiltViewModel(),
) {
    val astra = LocalAstraColors.current
    val context = LocalContext.current
    val message by viewModel.message.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val pendingExport by viewModel.pendingExport.collectAsState()
    var confirmWipe by remember { mutableStateOf(false) }
    var restartRequired by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            // Import-less export path: write the generated JSON to the chosen URI.
            val file = pendingExport
            if (file != null) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        file.inputStream().use { it.copyTo(out) }
                    }
                }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.import(uri)
    }

    AstraScaffold(title = "Privacy center", subtitle = "No network, no account, no tracking", onBack = onBack) { modifier ->
        Column(
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(astra.surface, astra.primary.copy(alpha = 0.12f))))
                    .padding(18.dp),
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = astra.success)
                        Spacer(Modifier.width(10.dp))
                        Text("Offline by construction", style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "The app manifest declares no INTERNET permission and no analytics or advertising SDK. " +
                            "Every profile, macro, note, screenshot and clipboard entry stays in AstraTouch's " +
                            "private app storage on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = astra.onBackground.copy(alpha = 0.75f),
                    )
                }
            }
            if (message != null) {
                Spacer(Modifier.height(8.dp))
                Text(message!!, style = MaterialTheme.typography.labelMedium, color = astra.primary)
            }
            if (busy) {
                Spacer(Modifier.height(8.dp))
                Text("Working…", style = MaterialTheme.typography.labelMedium, color = astra.secondary)
            }

            SectionHeader("Your data", "Plain JSON — portable, inspectable, yours")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AstraSecondaryButton(
                    text = "Export backup",
                    onClick = { viewModel.export() },
                    modifier = Modifier.weight(1f),
                )
                AstraSecondaryButton(
                    text = "Import backup",
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    modifier = Modifier.weight(1f),
                )
            }
            if (pendingExport != null) {
                Spacer(Modifier.height(8.dp))
                AstraCard(onClick = { exportLauncher.launch("astratouch-backup.json") }) {
                    Text(
                        "Tap to save ${pendingExport!!.name} (${pendingExport!!.length() / 1024} KB) with the system file picker.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            SectionHeader("Danger zone")
            AstraCard(onClick = { confirmWipe = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = astra.danger)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Delete all local data", style = MaterialTheme.typography.titleSmall, color = astra.danger)
                        Text(
                            "Profiles, macros, notes, clipboard history, screenshots and recordings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = astra.onBackground.copy(alpha = 0.65f),
                        )
                    }
                }
            }

            SectionHeader("Notes on clipboard privacy")
            AstraCard {
                Text(
                    "AstraTouch only reads the clipboard when you tap Capture, and it refuses to store anything " +
                        "that looks like a password, one-time code, private key or payment card. On Android 13+ " +
                        "the system's own sensitive-content flag is honoured first. Clipboard history is never " +
                        "part of an exported backup.",
                    style = MaterialTheme.typography.bodySmall,
                    color = astra.onBackground.copy(alpha = 0.72f),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Delete everything?") },
            text = {
                Text(
                    "This permanently deletes every AstraTouch profile, macro, note, clipboard entry, " +
                        "screenshot and recording on this device. It cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmWipe = false
                    viewModel.wipe(context) { restartRequired = true }
                }) { Text("Delete all data", color = LocalAstraColors.current.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Cancel") } },
        )
    }

    if (restartRequired) {
        AlertDialog(
            onDismissRequest = { restartRequired = false; onBack() },
            title = { Text("Data deleted") },
            text = { Text("AstraTouch will rebuild its default profiles. Reopen the app to start fresh.") },
            confirmButton = {
                TextButton(onClick = { restartRequired = false; onBack() }) { Text("OK") }
            },
        )
    }
}

// --------------------------------------------------------------- Lab

@HiltViewModel
class ExperimentalLabViewModel @Inject constructor(
    private val preferencesStore: PreferencesStore,
) : ViewModel() {
    val prefs = preferencesStore.preferences
        .stateInSafe(viewModelScope)

    fun setGestures(value: Boolean) = viewModelScope.launch { preferencesStore.setExperimentalGestures(value) }
    fun setEdgePanel(value: Boolean) = viewModelScope.launch { preferencesStore.setExperimentalEdgePanel(value) }
    fun setOcr(value: Boolean) = viewModelScope.launch { preferencesStore.setExperimentalOcr(value) }
}

internal fun kotlinx.coroutines.flow.Flow<com.astratouch.app.domain.model.AppPreferences>.stateInSafe(
    scope: kotlinx.coroutines.CoroutineScope,
): kotlinx.coroutines.flow.StateFlow<com.astratouch.app.domain.model.AppPreferences> =
    this.stateIn(
        scope,
        kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000),
        com.astratouch.app.domain.model.AppPreferences(),
    )

@Composable
fun ExperimentalLabScreen(
    onBack: () -> Unit,
    viewModel: ExperimentalLabViewModel = hiltViewModel(),
) {
    val astra = LocalAstraColors.current
    val prefs by viewModel.prefs.collectAsState()

    AstraScaffold(title = "Experimental lab", subtitle = "Features that are real but still maturing", onBack = onBack) { modifier ->
        Column(
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            AstraCard {
                Text(
                    "Everything here works today, but the behaviour may change between releases. " +
                        "Nothing in this lab sends data off the device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = astra.onBackground.copy(alpha = 0.72f),
                )
            }
            Spacer(Modifier.height(10.dp))
            AstraCard {
                Column {
                    SettingRow(
                        title = "Extended gesture set",
                        description = "Enables diagonal swipe classification hints on the gesture test pad.",
                        checked = prefs.experimentalGestures,
                        onCheckedChange = viewModel::setGestures,
                    )
                    SettingRow(
                        title = "Edge panel",
                        description = "Swipe from the docked edge to reveal quick shortcuts.",
                        checked = prefs.experimentalEdgePanel,
                        onCheckedChange = viewModel::setEdgePanel,
                    )
                    SettingRow(
                        title = "On-screen text recognition",
                        description = "Reserved. AstraTouch does not ship an OCR engine yet, so this stays off and " +
                            "is labelled honestly rather than pretending to work.",
                        checked = false,
                        enabled = false,
                        onCheckedChange = { viewModel.setOcr(it) },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            AstraCard {
                Column {
                    Text("Diagnostics", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    DiagnosticRow("Overlay service", if (OverlayController.isRunning()) "running" else "stopped")
                    DiagnosticRow("Accessibility", if (com.astratouch.app.accessibility.AstraAccessibilityService.instance != null) "connected" else "not connected")
                    DiagnosticRow("Foreground app", com.astratouch.app.accessibility.AstraAccessibilityService.foregroundPackage.value ?: "unknown")
                    DiagnosticRow("SDK", BuildConfig.ASTRA_TARGET_SDK.toString())
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    val astra = LocalAstraColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelMedium, color = astra.primary)
    }
}

// ------------------------------------------------------------ Settings

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesStore: PreferencesStore,
    private val actionRegistry: ActionRegistry,
) : ViewModel() {
    val prefs = preferencesStore.preferences
        .stateInSafe(viewModelScope)

    fun setHaptics(v: Boolean) = viewModelScope.launch { preferencesStore.setHapticsEnabled(v) }
    fun setReduceMotion(v: Boolean) = viewModelScope.launch { preferencesStore.setReduceMotion(v) }
    fun setShowRecents(v: Boolean) = viewModelScope.launch { preferencesStore.setShowRecentsInRadial(v) }
    fun setRetention(days: Int) = viewModelScope.launch { preferencesStore.setClipboardRetentionDays(days) }

    val actionCount: Int get() = actionRegistry.all().size
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val astra = LocalAstraColors.current
    val prefs by viewModel.prefs.collectAsState()

    AstraScaffold(title = "Settings", subtitle = "Behaviour, data and about", onBack = onBack) { modifier ->
        Column(
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SectionHeader("Feel")
            AstraCard {
                Column {
                    SettingRow(
                        title = "Haptic feedback",
                        description = "Vibrate on bubble taps and menu selections.",
                        checked = prefs.hapticsEnabled,
                        onCheckedChange = viewModel::setHaptics,
                    )
                    SettingRow(
                        title = "Reduce motion",
                        description = "Shortens AstraTouch's own animations. Android's accessibility setting is respected too.",
                        checked = prefs.reduceMotion,
                        onCheckedChange = viewModel::setReduceMotion,
                    )
                }
            }
            SectionHeader("Menu")
            AstraCard {
                Column {
                    SettingRow(
                        title = "Show recent actions",
                        description = "Adds a recently-used row to the radial editor.",
                        checked = prefs.showRecentsInRadial,
                        onCheckedChange = viewModel::setShowRecents,
                    )
                }
            }
            SectionHeader("Clipboard")
            AstraCard {
                Column {
                    Text(
                        "Retention: ${prefs.clipboardRetentionDays} day(s)",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1, 7, 30).forEach { days ->
                            FilterChipLite("$days d", prefs.clipboardRetentionDays == days) {
                                viewModel.setRetention(days)
                            }
                        }
                    }
                }
            }
            SectionHeader("Navigate")
            listOf(
                "Permission center" to NavRoutes.PERMISSIONS,
                "Privacy center" to NavRoutes.PRIVACY,
                "Experimental lab" to NavRoutes.LAB,
                "Action library" to NavRoutes.ACTIONS,
                "Profiles" to NavRoutes.PROFILES,
                "Theme studio" to NavRoutes.THEMES,
                "About AstraTouch" to NavRoutes.ABOUT,
            ).forEach { (label, route) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onNavigate(route) }
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text("›", style = MaterialTheme.typography.titleMedium, color = astra.primary)
                }
            }
            Spacer(Modifier.height(12.dp))
            AstraCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = astra.success, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "No INTERNET permission · ${viewModel.actionCount} registered actions",
                        style = MaterialTheme.typography.labelSmall,
                        color = astra.onBackground.copy(alpha = 0.7f),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---------------------------------------------------------------- About

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val astra = LocalAstraColors.current
    val context = LocalContext.current
    val packageInfo = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
    }
    AstraScaffold(title = "About AstraTouch", subtitle = "Your Phone. Your Control.", onBack = onBack) { modifier ->
        Column(
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Brush.linearGradient(listOf(astra.primary.copy(alpha = 0.2f), astra.surface)))
                    .padding(22.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Box(
                        Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(astra.primary, astra.secondary))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            com.astratouch.app.ui.overlay.IconRegistry.get("ic_star"),
                            contentDescription = null,
                            tint = androidx.compose.ui.graphics.Color.White,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("AstraTouch", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Version ${BuildConfig.ASTRA_VERSION_NAME} (${BuildConfig.ASTRA_VERSION_CODE})",
                        style = MaterialTheme.typography.bodySmall,
                        color = astra.primary,
                    )
                }
            }
            SectionHeader("Build information")
            AstraCard {
                Column {
                    InfoRow("Version", BuildConfig.ASTRA_VERSION_NAME)
                    InfoRow("Version code", BuildConfig.ASTRA_VERSION_CODE.toString())
                    InfoRow("Application ID", BuildConfig.ASTRA_APPLICATION_ID)
                    InfoRow("Package", packageInfo?.packageName ?: context.packageName)
                    InfoRow("Min / target SDK", "${BuildConfig.ASTRA_MIN_SDK} / ${BuildConfig.ASTRA_TARGET_SDK}")
                    InfoRow("Build type", if (BuildConfig.DEBUG) "debug" else "release")
                    InfoRow("License", "MIT (see LICENSE)")
                }
            }
            SectionHeader("Privacy")
            AstraCard {
                Text(
                    "Offline by design: no internet permission, no account, no ads, no analytics, no telemetry. " +
                        "All configuration lives in AstraTouch's private storage on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = astra.onBackground.copy(alpha = 0.75f),
                )
            }
            SectionHeader("Open source")
            AstraCard {
                Column {
                    Text("Built with Kotlin, Jetpack Compose, Room, DataStore, Hilt and Kotlin Serialization.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Source, release notes and the signed APK are published on GitHub Releases.",
                        style = MaterialTheme.typography.bodySmall,
                        color = astra.onBackground.copy(alpha = 0.6f),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val astra = LocalAstraColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.labelMedium,
            color = astra.primary,
            textAlign = TextAlign.End,
        )
    }
}
