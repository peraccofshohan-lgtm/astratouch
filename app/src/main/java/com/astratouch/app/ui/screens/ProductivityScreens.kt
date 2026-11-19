package com.astratouch.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astratouch.app.data.prefs.PreferencesStore
import com.astratouch.app.data.repository.ClipboardRepository
import com.astratouch.app.data.repository.NotesRepository
import com.astratouch.app.data.repository.TimerRepository
import com.astratouch.app.domain.model.AstraTimer
import com.astratouch.app.domain.model.ClipboardRecord
import com.astratouch.app.domain.model.Note
import com.astratouch.app.util.SensitiveContentFilter
import com.astratouch.app.ui.components.AstraCard
import com.astratouch.app.ui.components.AstraPrimaryButton
import com.astratouch.app.ui.components.AstraScaffold
import com.astratouch.app.ui.components.AstraSecondaryButton
import com.astratouch.app.ui.components.EmptyState
import com.astratouch.app.ui.components.SectionHeader
import com.astratouch.app.ui.components.SettingRow
import com.astratouch.app.ui.theme.LocalAstraColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

// -------------------------------------------------------------- Clipboard

@HiltViewModel
class ClipboardViewModel @Inject constructor(
    private val clipboardRepository: ClipboardRepository,
    private val preferencesStore: PreferencesStore,
) : ViewModel() {
    val records: StateFlow<List<ClipboardRecord>> = clipboardRepository.observeRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val prefs = preferencesStore.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.astratouch.app.domain.model.AppPreferences())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun setQuery(q: String) { _query.value = q }

    fun filtered(): List<ClipboardRecord> = records.value.filter {
        _query.value.isBlank() || it.content.contains(_query.value, true)
    }

    fun toggleCapture(enabled: Boolean) {
        viewModelScope.launch {
            preferencesStore.setClipboardCaptureEnabled(enabled)
            _message.value = if (enabled) {
                "Clipboard history is on. Passwords, OTPs and card numbers are still excluded."
            } else {
                "Clipboard history is off. Existing entries stay until you clear them."
            }
        }
    }

    fun captureCurrent(context: Context) {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clip = manager.primaryClip
        if (clip == null || clip.itemCount == 0) {
            _message.value = "The clipboard is empty."
            return
        }
        val text = clip.getItemAt(0).coerceToText(context)?.toString().orEmpty()
        if (text.isBlank()) {
            _message.value = "Clipboard content is not text, so it cannot be saved."
            return
        }
        val sensitiveFlag = clip.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true
        if (sensitiveFlag || SensitiveContentFilter.isSensitive(text)) {
            _message.value = SensitiveContentFilter.describeRejection(text)
            return
        }
        viewModelScope.launch {
            clipboardRepository.add(
                ClipboardRecord(
                    content = text,
                    createdAt = System.currentTimeMillis(),
                    charCount = text.length,
                ),
            )
            _message.value = "Saved ${text.length} characters to your local history."
        }
    }

    fun copy(context: Context, record: ClipboardRecord) {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        manager.setPrimaryClip(ClipData.newPlainText("AstraTouch", record.content))
        _message.value = "Copied to the system clipboard."
    }

    fun togglePin(record: ClipboardRecord) {
        viewModelScope.launch { clipboardRepository.setPinned(record.id, !record.isPinned) }
    }

    fun delete(record: ClipboardRecord) {
        viewModelScope.launch { clipboardRepository.delete(record.id) }
    }

    fun clearUnpinned() {
        viewModelScope.launch {
            clipboardRepository.clearUnpinned()
            _message.value = "Cleared every entry that was not pinned."
        }
    }

    fun dismiss() { _message.value = null }
}

@Composable
fun ClipboardScreen(
    onBack: () -> Unit,
    viewModel: ClipboardViewModel = hiltViewModel(),
) {
    val astra = LocalAstraColors.current
    val context = LocalContext.current
    val records by viewModel.records.collectAsState()
    val prefs by viewModel.prefs.collectAsState()
    val query by viewModel.query.collectAsState()
    val message by viewModel.message.collectAsState()
    val filtered = remember(records, query) { viewModel.filtered() }

    AstraScaffold(
        title = "Clipboard",
        subtitle = "Local history — never leaves this device",
        onBack = onBack,
        actions = {
            TextButton(onClick = { viewModel.clearUnpinned() }) { Text("Clear") }
        },
    ) { modifier ->
        Column(
            modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            AstraCard {
                Column {
                    SettingRow(
                        title = "Save clipboard history",
                        description = "AstraTouch only reads the clipboard when you tap Capture.",
                        checked = prefs.clipboardCaptureEnabled,
                        onCheckedChange = viewModel::toggleCapture,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AstraPrimaryButton(
                            text = "Capture clipboard now",
                            onClick = { viewModel.captureCurrent(context) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            if (message != null) {
                Spacer(Modifier.height(8.dp))
                Text(message!!, style = MaterialTheme.typography.labelMedium, color = astra.warning)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                label = { Text("Search history") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("clipboard_search"),
            )
            Spacer(Modifier.height(10.dp))

            if (filtered.isEmpty()) {
                EmptyState(
                    title = if (records.isEmpty()) "No clipboard entries" else "No matches",
                    message = if (records.isEmpty()) {
                        "Copy something, then tap Capture. Passwords, one-time codes and card numbers are never stored."
                    } else {
                        "Try another search term."
                    },
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filtered) { record ->
                        AstraCard {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())
                                            .format(Date(record.createdAt)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = astra.onBackground.copy(alpha = 0.55f),
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        "${record.charCount} chars",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = astra.onBackground.copy(alpha = 0.45f),
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    record.content.take(240) + if (record.content.length > 240) "…" else "",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(onClick = { viewModel.copy(context, record) }) { Text("Copy") }
                                    TextButton(onClick = { viewModel.togglePin(record) }) {
                                        Icon(
                                            Icons.Filled.PushPin,
                                            contentDescription = if (record.isPinned) "Unpin" else "Pin",
                                            modifier = Modifier.size(16.dp),
                                            tint = if (record.isPinned) astra.warning else astra.onBackground.copy(alpha = 0.5f),
                                        )
                                    }
                                    TextButton(onClick = { viewModel.delete(record) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Notes

@HiltViewModel
class NotesViewModel @Inject constructor(
    private val notesRepository: NotesRepository,
) : ViewModel() {
    val notes: StateFlow<List<Note>> = notesRepository.observeNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(note: Note, onSaved: () -> Unit) {
        viewModelScope.launch {
            notesRepository.upsert(note)
            onSaved()
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { notesRepository.delete(id) }
    }
}

@Composable
fun NotesScreen(
    onBack: () -> Unit,
    viewModel: NotesViewModel = hiltViewModel(),
) {
    val astra = LocalAstraColors.current
    val notes by viewModel.notes.collectAsState()
    var editing by remember { mutableStateOf<Note?>(null) }
    var creating by remember { mutableStateOf(false) }

    AstraScaffold(
        title = "Notes",
        subtitle = "${notes.size} note(s) stored locally",
        onBack = onBack,
        actions = {
            androidx.compose.material3.IconButton(onClick = { creating = true }) {
                Icon(Icons.Filled.Add, contentDescription = "New note")
            }
        },
    ) { modifier ->
        if (notes.isEmpty()) {
            EmptyState(
                title = "No notes yet",
                message = "Notes are stored in AstraTouch's local database with no sync and no account.",
                actionLabel = "Write the first note",
                onAction = { creating = true },
                modifier = modifier,
            )
        } else {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(notes) { note ->
                    AstraCard(Modifier.clickable { editing = note }) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    note.displayTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                )
                                if (note.isPinned) {
                                    Icon(
                                        Icons.Filled.PushPin,
                                        contentDescription = "Pinned",
                                        tint = astra.warning,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                            if (note.body.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    note.body.take(160),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = astra.onBackground.copy(alpha = 0.7f),
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(note.updatedAt)),
                                style = MaterialTheme.typography.labelSmall,
                                color = astra.onBackground.copy(alpha = 0.45f),
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    if (creating || editing != null) {
        var title by remember(editing, creating) { mutableStateOf(editing?.title ?: "") }
        var body by remember(editing, creating) { mutableStateOf(editing?.body ?: "") }
        var pinned by remember(editing, creating) { mutableStateOf(editing?.isPinned ?: false) }
        AlertDialog(
            onDismissRequest = { creating = false; editing = null },
            title = { Text(if (editing == null) "New note" else "Edit note") },
            text = {
                Column {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        label = { Text("Note") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SettingRow(
                        title = "Pin to top",
                        description = "Pinned notes stay at the top of the list.",
                        checked = pinned,
                        onCheckedChange = { pinned = it },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (title.isBlank() && body.isBlank()) {
                        creating = false
                        editing = null
                        return@TextButton
                    }
                    val existing = editing
                    viewModel.save(
                        existing?.copy(title = title, body = body, isPinned = pinned)
                            ?: Note(title = title, body = body, isPinned = pinned),
                    ) {
                        creating = false
                        editing = null
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    if (editing != null) {
                        TextButton(onClick = {
                            editing?.let { viewModel.delete(it.id) }
                            editing = null
                        }) { Text("Delete") }
                    }
                    TextButton(onClick = { creating = false; editing = null }) { Text("Cancel") }
                }
            },
        )
    }
}

// ------------------------------------------------------------- Calculator

/** Safe expression evaluator: no script engine, no reflection, no network. */
object ExpressionEvaluator {

    fun evaluate(input: String): Result<Double> = runCatching {
        val tokens = tokenize(input)
        val parser = Parser(tokens)
        val value = parser.parseExpression()
        require(parser.isAtEnd()) { "Unexpected trailing input" }
        require(!value.isNaN() && !value.isInfinite()) { "Result is not a finite number" }
        value
    }

    private fun tokenize(input: String): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        val normalized = input.replace("×", "*").replace("÷", "/").replace("−", "-").replace(" ", "")
        while (i < normalized.length) {
            val c = normalized[i]
            when {
                c.isDigit() || c == '.' -> {
                    val start = i
                    while (i < normalized.length && (normalized[i].isDigit() || normalized[i] == '.')) i++
                    out += normalized.substring(start, i)
                }
                c in "+-*/^()%" -> {
                    out += c.toString()
                    i++
                }
                else -> throw IllegalArgumentException("Unsupported character '$c'")
            }
        }
        return out
    }

    private class Parser(private val tokens: List<String>) {
        private var pos = 0

        fun isAtEnd(): Boolean = pos >= tokens.size

        fun parseExpression(): Double {
            var value = parseTerm()
            while (!isAtEnd() && (peek() == "+" || peek() == "-")) {
                val op = next()
                val rhs = parseTerm()
                value = if (op == "+") value + rhs else value - rhs
            }
            return value
        }

        private fun parseTerm(): Double {
            var value = parsePower()
            while (!isAtEnd() && (peek() == "*" || peek() == "/" || peek() == "%")) {
                val op = next()
                val rhs = parsePower()
                value = when (op) {
                    "*" -> value * rhs
                    "/" -> {
                        require(rhs != 0.0) { "Cannot divide by zero" }
                        value / rhs
                    }
                    else -> value % rhs
                }
            }
            return value
        }

        private fun parsePower(): Double {
            val base = parseUnary()
            if (!isAtEnd() && peek() == "^") {
                next()
                return Math.pow(base, parsePower())
            }
            return base
        }

        private fun parseUnary(): Double {
            if (!isAtEnd() && peek() == "-") {
                next()
                return -parseUnary()
            }
            return parsePrimary()
        }

        private fun parsePrimary(): Double {
            if (isAtEnd()) throw IllegalArgumentException("Expression ended unexpectedly")
            val token = next()
            if (token == "(") {
                val value = parseExpression()
                require(!isAtEnd() && next() == ")") { "Missing closing parenthesis" }
                return value
            }
            return token.toDoubleOrNull() ?: throw IllegalArgumentException("'$token' is not a number")
        }

        private fun peek(): String = tokens[pos]
        private fun next(): String = tokens[pos++]
    }
}

@HiltViewModel
class CalculatorViewModel @Inject constructor() : ViewModel() {
    private val _expression = MutableStateFlow("")
    val expression: StateFlow<String> = _expression.asStateFlow()

    private val _result = MutableStateFlow<String?>(null)
    val result: StateFlow<String?> = _result.asStateFlow()

    private val _history = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val history: StateFlow<List<Pair<String, String>>> = _history.asStateFlow()

    fun input(chunk: String) {
        _expression.value = (_expression.value + chunk).take(64)
        _result.value = null
    }

    fun clear() {
        _expression.value = ""
        _result.value = null
    }

    fun backspace() {
        _expression.value = _expression.value.dropLast(1)
    }

    fun evaluate() {
        val expr = _expression.value
        if (expr.isBlank()) return
        ExpressionEvaluator.evaluate(expr).fold(
            onSuccess = { value ->
                val formatted = format(value)
                _result.value = formatted
                _history.value = (listOf(expr to formatted) + _history.value).take(12)
                // Keep the worked expression visible; the value lives in `result`.
                // Replacing the display with the answer alone made it impossible to
                // see what had been calculated, and made the UI harder to test.
            },
            onFailure = { _result.value = "Error: ${it.message ?: "invalid expression"}" },
        )
    }

    private fun format(value: Double): String =
        if (value == value.toLong().toDouble() && kotlin.math.abs(value) < 1e15) {
            value.toLong().toString()
        } else {
            "%.6f".format(value).trimEnd('0').trimEnd('.')
        }
}

@Composable
fun CalculatorScreen(
    onBack: () -> Unit,
    viewModel: CalculatorViewModel = hiltViewModel(),
) {
    val astra = LocalAstraColors.current
    val expression by viewModel.expression.collectAsState()
    val result by viewModel.result.collectAsState()
    val history by viewModel.history.collectAsState()

    AstraScaffold(title = "Calculator", subtitle = "Offline, no ads, no history upload", onBack = onBack) { modifier ->
        Column(
            modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(astra.surface)
                    .padding(16.dp),
            ) {
                Column {
                    Text(
                        expression.ifBlank { "0" },
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 2,
                        modifier = Modifier.testTag("calc_expression"),
                    )
                    if (result != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            result!!,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (result!!.startsWith("Error")) astra.danger else astra.primary,
                            modifier = Modifier.testTag("calc_result"),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            val keys = listOf(
                listOf("7", "8", "9", "÷"),
                listOf("4", "5", "6", "×"),
                listOf("1", "2", "3", "−"),
                listOf("(", "0", ")", "+"),
                listOf(".", "%", "^", "⌫"),
            )
            keys.forEach { row ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { key ->
                        CalcKey(
                            label = key,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                when (key) {
                                    "⌫" -> viewModel.backspace()
                                    else -> viewModel.input(key)
                                }
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CalcKey("C", Modifier.weight(1f)) { viewModel.clear() }
                CalcKey("=", Modifier.weight(2f), accent = true) { viewModel.evaluate() }
            }
            if (history.isNotEmpty()) {
                SectionHeader("Recent calculations")
                LazyColumn(Modifier.fillMaxWidth()) {
                    items(history.size) { i ->
                        val (expr, res) = history[i]
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.input(res) }
                                .padding(vertical = 6.dp),
                        ) {
                            Text(expr, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Text(res, style = MaterialTheme.typography.bodySmall, color = astra.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalcKey(label: String, modifier: Modifier = Modifier, accent: Boolean = false, onClick: () -> Unit) {
    val astra = LocalAstraColors.current
    Box(
        modifier
            .height(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (accent) astra.primary.copy(alpha = 0.22f) else astra.surface)
            .clickable(onClick = onClick)
            // Keys are also reachable by text ("2", "+"), but the display and the
            // recent-calculations list contain the same glyphs. The tag makes the
            // keypad unambiguous for UI tests and for TalkBack ordering.
            .testTag("calc_key_$label"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = if (accent) astra.primary else astra.onBackground,
        )
    }
}

// ------------------------------------------------------------------ Timer

@HiltViewModel
class TimerViewModel @Inject constructor(
    private val timerRepository: TimerRepository,
) : ViewModel() {
    private val _timers = MutableStateFlow<List<AstraTimer>>(emptyList())
    val timers: StateFlow<List<AstraTimer>> = _timers.asStateFlow()

    private val _now = MutableStateFlow(System.currentTimeMillis())
    val now: StateFlow<Long> = _now.asStateFlow()

    init {
        viewModelScope.launch {
            timerRepository.observeTimers().collect { _timers.value = it }
        }
        viewModelScope.launch {
            // Ticks once per second only while this screen is alive, and only for
            // the countdown display. No background alarm polling.
            while (true) {
                _now.value = System.currentTimeMillis()
                delay(1_000)
            }
        }
    }

    fun start(seconds: Int, label: String) {
        if (seconds <= 0) return
        viewModelScope.launch {
            timerRepository.upsert(
                AstraTimer(
                    label = label.ifBlank { "$seconds second timer" },
                    durationMs = seconds * 1000L,
                    remainingMs = seconds * 1000L,
                    isRunning = true,
                    endsAtEpochMs = System.currentTimeMillis() + seconds * 1000L,
                ),
            )
        }
    }

    fun stop(timer: AstraTimer) {
        viewModelScope.launch { timerRepository.upsert(timer.copy(isRunning = false, remainingMs = 0)) }
    }

    fun delete(timer: AstraTimer) {
        viewModelScope.launch { timerRepository.delete(timer.id) }
    }

    fun remainingMs(timer: AstraTimer): Long =
        if (timer.isRunning) (timer.endsAtEpochMs - _now.value).coerceAtLeast(0) else 0L
}

@Composable
fun TimerScreen(
    onBack: () -> Unit,
    viewModel: TimerViewModel = hiltViewModel(),
) {
    val astra = LocalAstraColors.current
    val timers by viewModel.timers.collectAsState()
    val now by viewModel.now.collectAsState()
    var secondsInput by remember { mutableStateOf("60") }
    var labelInput by remember { mutableStateOf("") }

    AstraScaffold(title = "Timer", subtitle = "Simple countdown timers, stored locally", onBack = onBack) { modifier ->
        Column(
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            AstraCard {
                Column {
                    OutlinedTextField(
                        value = secondsInput,
                        onValueChange = { secondsInput = it.filter(Char::isDigit).take(6) },
                        label = { Text("Duration (seconds)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = labelInput,
                        onValueChange = { labelInput = it },
                        label = { Text("Label (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(30, 60, 300, 600).forEach { preset ->
                            TextButton(onClick = { secondsInput = preset.toString() }) {
                                Text("${preset}s", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    AstraPrimaryButton(
                        text = "Start timer",
                        onClick = {
                            viewModel.start(secondsInput.toIntOrNull() ?: 0, labelInput.trim())
                            labelInput = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = (secondsInput.toIntOrNull() ?: 0) > 0,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            if (timers.isEmpty()) {
                AstraCard {
                    Text(
                        "No timers yet. AstraTouch does not need an alarm permission: timers count while the " +
                            "timer screen or the floating bubble is running.",
                        style = MaterialTheme.typography.bodySmall,
                        color = astra.onBackground.copy(alpha = 0.7f),
                    )
                }
            }
            timers.forEach { timer ->
                val remaining = if (timer.isRunning) (timer.endsAtEpochMs - now).coerceAtLeast(0) else 0L
                AstraCard(Modifier.padding(bottom = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(timer.label, style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (timer.isRunning) formatDuration(remaining)
                                else "Finished / stopped",
                                style = MaterialTheme.typography.headlineSmall,
                                color = if (remaining > 0) astra.primary else astra.onBackground.copy(alpha = 0.5f),
                            )
                        }
                        if (timer.isRunning) {
                            TextButton(onClick = { viewModel.stop(timer) }) { Text("Stop") }
                        }
                        TextButton(onClick = { viewModel.delete(timer) }) { Text("Delete") }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
