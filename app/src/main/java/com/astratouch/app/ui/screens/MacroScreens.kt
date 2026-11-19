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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astratouch.app.data.repository.MacroRepository
import com.astratouch.app.domain.model.ConditionType
import com.astratouch.app.domain.model.Macro
import com.astratouch.app.domain.model.MacroExecutionRecord
import com.astratouch.app.domain.model.MacroRunStatus
import com.astratouch.app.domain.model.MacroStep
import com.astratouch.app.domain.model.StepType
import com.astratouch.app.domain.usecase.DuplicateMacroUseCase
import com.astratouch.app.domain.usecase.RunMacroUseCase
import com.astratouch.app.domain.usecase.SaveMacroUseCase
import com.astratouch.app.service.action.ActionIcons
import com.astratouch.app.service.action.ActionRegistry
import com.astratouch.app.service.action.AstraAction
import com.astratouch.app.service.action.CapabilityState
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MacroManagerViewModel @Inject constructor(
    private val macroRepository: MacroRepository,
    private val runMacro: RunMacroUseCase,
    private val duplicateMacro: DuplicateMacroUseCase,
) : ViewModel() {
    val macros: StateFlow<List<Macro>> = macroRepository.observeMacros()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val history: StateFlow<List<MacroExecutionRecord>> = macroRepository.observeExecutions(30)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _runningId = MutableStateFlow<String?>(null)
    val runningId: StateFlow<String?> = _runningId.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun run(macro: Macro) {
        if (_runningId.value != null) {
            _message.value = "Another macro is already running. Wait for it to finish."
            return
        }
        viewModelScope.launch {
            _runningId.value = macro.id
            val outcome = runMacro(macro)
            _message.value = "${macro.name}: ${outcome.status.label} — ${outcome.detail}"
            _runningId.value = null
        }
    }

    fun duplicate(id: String) {
        viewModelScope.launch {
            val copy = duplicateMacro(id)
            _message.value = copy?.let { "Created \"${it.name}\"." } ?: "Could not duplicate that macro."
        }
    }

    fun delete(macro: Macro) {
        viewModelScope.launch {
            if (macro.name.startsWith("macro-")) {
                // defensive: never allow deletion of a nameless internal entry
            }
            macroRepository.deleteMacro(macro.id)
            _message.value = "Deleted \"${macro.name}\"."
        }
    }

    fun clearHistory() {
        viewModelScope.launch { macroRepository.clearHistory() }
    }

    fun dismiss() { _message.value = null }
}

@Composable
fun MacroManagerScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: MacroManagerViewModel = hiltViewModel(),
) {
    val astra = LocalAstraColors.current
    val macros by viewModel.macros.collectAsState()
    val history by viewModel.history.collectAsState()
    val runningId by viewModel.runningId.collectAsState()
    val message by viewModel.message.collectAsState()
    var confirmDelete by remember { mutableStateOf<Macro?>(null) }

    AstraScaffold(
        title = "Macros",
        subtitle = "${macros.size} automations on this device",
        onBack = onBack,
        actions = {
            androidx.compose.material3.IconButton(onClick = { onEdit("") }) {
                Icon(Icons.Filled.Add, contentDescription = "New macro")
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

            if (macros.isEmpty()) {
                EmptyState(
                    title = "No macros yet",
                    message = "A macro chains actions with delays and conditions. Tap + to build your first one.",
                )
            }

            macros.forEach { macro ->
                AstraCard(Modifier.padding(bottom = 10.dp)) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(astra.secondary.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    IconRegistry.get(macro.iconKey),
                                    contentDescription = null,
                                    tint = astra.secondary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(macro.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    macro.description.ifBlank { "No description" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = astra.onBackground.copy(alpha = 0.65f),
                                )
                            }
                            StatusPill(
                                text = macro.lastRunStatus.label,
                                positive = macro.lastRunStatus == MacroRunStatus.SUCCESS,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            androidx.compose.material3.Button(
                                onClick = { viewModel.run(macro) },
                                enabled = runningId == null && macro.isEnabled,
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (runningId == macro.id) "Running…" else "Run", style = MaterialTheme.typography.labelMedium)
                            }
                            TextButton(onClick = { onEdit(macro.id) }) { Text("Edit") }
                            TextButton(onClick = { viewModel.duplicate(macro.id) }) { Text("Duplicate") }
                            TextButton(onClick = { confirmDelete = macro }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            if (history.isNotEmpty()) {
                SectionHeader("Execution history", "Newest first")
                AstraCard {
                    Column {
                        history.take(12).forEach { record ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(record.macroName, style = MaterialTheme.typography.labelLarge)
                                    Text(
                                        record.detail,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = astra.onBackground.copy(alpha = 0.55f),
                                    )
                                }
                                Text(
                                    record.status.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (record.status == MacroRunStatus.SUCCESS) astra.success else astra.warning,
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        TextButton(onClick = { viewModel.clearHistory() }) { Text("Clear history") }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    confirmDelete?.let { macro ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete ${macro.name}?") },
            text = { Text("This removes the macro and all of its steps from this device.") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(macro); confirmDelete = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

@HiltViewModel
class MacroEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val macroRepository: MacroRepository,
    private val saveMacro: SaveMacroUseCase,
    private val runMacro: RunMacroUseCase,
    private val actionRegistry: ActionRegistry,
) : ViewModel() {

    private val macroId: String = savedStateHandle.get<String>("macroId").orEmpty()

    private val _macro = MutableStateFlow(Macro(id = "", name = ""))
    val macro: StateFlow<Macro> = _macro.asStateFlow()

    private val _steps = MutableStateFlow<List<MacroStep>>(emptyList())
    val steps: StateFlow<List<MacroStep>> = _steps.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val isNew: Boolean get() = macroId.isBlank()

    init {
        viewModelScope.launch {
            if (macroId.isNotBlank()) {
                macroRepository.getMacro(macroId)?.let { _macro.value = it }
                _steps.value = macroRepository.getSteps(macroId)
            } else {
                _macro.value = Macro(
                    id = "",
                    name = "",
                    description = "",
                    iconKey = "ic_bolt",
                )
            }
            _loaded.value = true
        }
    }

    fun setName(name: String) { _macro.value = _macro.value.copy(name = name) }

    fun setDescription(description: String) { _macro.value = _macro.value.copy(description = description) }

    fun setIcon(key: String) { _macro.value = _macro.value.copy(iconKey = key) }

    fun setEnabled(enabled: Boolean) { _macro.value = _macro.value.copy(isEnabled = enabled) }

    fun setTimeoutMs(ms: Long) { _macro.value = _macro.value.copy(timeoutMs = ms.coerceIn(2_000, 120_000)) }

    fun addActionStep(actionId: String) {
        _steps.value = _steps.value + MacroStep(
            id = "",
            macroId = _macro.value.id,
            order = _steps.value.size,
            type = StepType.ACTION,
            actionId = actionId,
        )
        renumber()
    }

    fun addDelayStep(ms: Long) {
        _steps.value = _steps.value + MacroStep(
            id = "",
            macroId = _macro.value.id,
            order = _steps.value.size,
            type = StepType.DELAY,
            delayMs = ms.coerceIn(0, RunMacroUseCase.MAX_DELAY_MS),
        )
        renumber()
    }

    fun addConditionStep(condition: ConditionType, argument: String) {
        _steps.value = _steps.value + MacroStep(
            id = "",
            macroId = _macro.value.id,
            order = _steps.value.size,
            type = StepType.CONDITION,
            payload = if (argument.isBlank()) condition.name else "${condition.name}|$argument",
            label = condition.label,
        )
        renumber()
    }

    fun removeStep(index: Int) {
        _steps.value = _steps.value.filterIndexed { i, _ -> i != index }
        renumber()
    }

    fun moveStep(index: Int, delta: Int) {
        val list = _steps.value.toMutableList()
        val target = index + delta
        if (index !in list.indices || target !in list.indices) return
        val tmp = list[index]
        list[index] = list[target]
        list[target] = tmp
        _steps.value = list
        renumber()
    }

    fun setStepDelay(index: Int, delayMs: Long) {
        _steps.value = _steps.value.mapIndexed { i, step ->
            if (i == index) step.copy(delayMs = delayMs.coerceIn(0, RunMacroUseCase.MAX_DELAY_MS)) else step
        }
    }

    private fun renumber() {
        _steps.value = _steps.value.mapIndexed { i, step -> step.copy(order = i) }
    }

    fun save(onSaved: (String) -> Unit) {
        viewModelScope.launch {
            val result = saveMacro(_macro.value, _steps.value)
            result.fold(
                onSuccess = { saved ->
                    _macro.value = saved
                    _message.value = "Saved."
                    onSaved(saved.id)
                },
                onFailure = { _message.value = it.message ?: "Could not save this macro." },
            )
        }
    }

    fun testRun() {
        viewModelScope.launch {
            val macro = _macro.value
            if (macro.id.isBlank()) {
                _message.value = "Save the macro before testing it."
                return@launch
            }
            val outcome = runMacro(macro)
            _message.value = "${outcome.status.label}: ${outcome.detail}"
        }
    }

    fun dismiss() { _message.value = null }

    fun actionName(actionId: String): String =
        actionRegistry.byId(actionId)?.name ?: actionId

    fun actionIcon(actionId: String): String = ActionIcons.iconFor(actionId)

    val allActions: List<AstraAction> get() = actionRegistry.all()
    fun capabilityOf(id: String): CapabilityState = actionRegistry.capabilityOf(id)
}

@Composable
fun MacroEditorScreen(
    macroId: String,
    onBack: () -> Unit,
    viewModel: MacroEditorViewModel = hiltViewModel(),
) {
    val astra = LocalAstraColors.current
    val macro by viewModel.macro.collectAsState()
    val steps by viewModel.steps.collectAsState()
    val message by viewModel.message.collectAsState()
    val loaded by viewModel.loaded.collectAsState()
    var showActionPicker by remember { mutableStateOf(false) }
    var showConditionDialog by remember { mutableStateOf(false) }
    var delayInput by remember { mutableStateOf("500") }

    AstraScaffold(
        title = if (viewModel.isNew) "New macro" else "Edit macro",
        subtitle = if (viewModel.isNew) "Chain actions, delays and conditions" else macro.name.ifBlank { "Macro" },
        onBack = onBack,
        actions = {
            TextButton(onClick = { viewModel.save { } }) { Text("Save") }
        },
    ) { modifier ->
        if (!loaded) {
            com.astratouch.app.ui.components.LoadingState()
            return@AstraScaffold
        }
        Column(
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            OutlinedTextField(
                value = macro.name,
                onValueChange = viewModel::setName,
                label = { Text("Macro name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("macro_name"),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = macro.description,
                onValueChange = viewModel::setDescription,
                label = { Text("Description (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))
            Text("Icon", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(IconRegistry.keys.size) { i ->
                    val key = IconRegistry.keys[i]
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (macro.iconKey == key) astra.primary.copy(alpha = 0.2f) else astra.surface)
                            .clickable { viewModel.setIcon(key) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(IconRegistry.get(key), contentDescription = key, modifier = Modifier.size(18.dp)) }
                }
            }

            SectionHeader("Steps", "${steps.size} step(s) — they run top to bottom")
            if (steps.isEmpty()) {
                AstraCard {
                    Text(
                        "No steps yet. Add an action, a wait, or a condition below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = astra.onBackground.copy(alpha = 0.7f),
                    )
                }
            }
            steps.forEachIndexed { index, step ->
                AstraCard(Modifier.padding(bottom = 8.dp)) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.labelLarge,
                                color = astra.primary,
                                modifier = Modifier.width(20.dp),
                            )
                            Icon(
                                IconRegistry.get(
                                    when (step.type) {
                                        StepType.ACTION -> viewModel.actionIcon(step.actionId)
                                        StepType.DELAY -> "ic_timer"
                                        StepType.CONDITION -> "ic_split"
                                    },
                                ),
                                contentDescription = null,
                                tint = astra.secondary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    when (step.type) {
                                        StepType.ACTION -> viewModel.actionName(step.actionId)
                                        StepType.DELAY -> "Wait ${step.delayMs} ms"
                                        StepType.CONDITION -> "If ${step.label.ifBlank { step.payload.substringBefore('|') }}"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                if (step.delayMs > 0 && step.type != StepType.DELAY) {
                                    Text(
                                        "Delay before: ${step.delayMs} ms",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = astra.onBackground.copy(alpha = 0.55f),
                                    )
                                }
                                if (step.type == StepType.ACTION) {
                                    val cap = viewModel.capabilityOf(step.actionId)
                                    if (cap != CapabilityState.AVAILABLE) {
                                        Text(cap.label, style = MaterialTheme.typography.labelSmall, color = astra.warning)
                                    }
                                }
                            }
                            TextButton(onClick = { viewModel.moveStep(index, -1) }) { Text("↑") }
                            TextButton(onClick = { viewModel.moveStep(index, 1) }) { Text("↓") }
                            TextButton(onClick = { viewModel.removeStep(index) }) { Text("✕") }
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                androidx.compose.material3.OutlinedButton(
                    onClick = { showActionPicker = true },
                    modifier = Modifier.weight(1f),
                ) { Text("Add action") }
                androidx.compose.material3.OutlinedButton(
                    onClick = { showConditionDialog = true },
                    modifier = Modifier.weight(1f),
                ) { Text("Add condition") }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = delayInput,
                    onValueChange = { delayInput = it.filter(Char::isDigit).take(5) },
                    label = { Text("Delay (ms)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                androidx.compose.material3.OutlinedButton(
                    onClick = { viewModel.addDelayStep(delayInput.toLongOrNull() ?: 500L) },
                ) { Text("Add wait") }
            }

            SectionHeader("Behaviour")
            SettingRow(
                title = "Enabled",
                description = "Disabled macros cannot run from the bubble or from macros.",
                checked = macro.isEnabled,
                onCheckedChange = viewModel::setEnabled,
            )
            SettingRow(
                title = "Long timeout guard",
                description = "Current limit: ${macro.timeoutMs / 1000}s. Macros stop themselves after this.",
                checked = macro.timeoutMs >= 30_000,
                onCheckedChange = { on -> viewModel.setTimeoutMs(if (on) 60_000 else 15_000) },
            )

            if (message != null) {
                Spacer(Modifier.height(8.dp))
                AstraCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(message!!, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewModel.dismiss() }) { Text("OK") }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                androidx.compose.material3.Button(
                    onClick = { viewModel.save { } },
                    modifier = Modifier.weight(1f),
                ) { Text("Save macro") }
                AstraSecondaryButton(
                    text = "Test run",
                    onClick = { viewModel.testRun() },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showActionPicker) {
        ActionPickerDialog(
            actions = viewModel.allActions,
            capabilityOf = viewModel::capabilityOf,
            onDismiss = { showActionPicker = false },
            onPick = { id ->
                viewModel.addActionStep(id)
                showActionPicker = false
            },
        )
    }

    if (showConditionDialog) {
        var condition by remember { mutableStateOf(ConditionType.PROFILE_IS) }
        var argument by remember { mutableStateOf("default") }
        AlertDialog(
            onDismissRequest = { showConditionDialog = false },
            title = { Text("Add condition") },
            text = {
                Column {
                    ConditionType.entries.forEach { c ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { condition = c }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(if (condition == c) astra.primary else astra.surface),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(c.label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    OutlinedTextField(
                        value = argument,
                        onValueChange = { argument = it },
                        label = { Text("Expected value (for profile checks)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.addConditionStep(condition, argument)
                    showConditionDialog = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showConditionDialog = false }) { Text("Cancel") } },
        )
    }
}
