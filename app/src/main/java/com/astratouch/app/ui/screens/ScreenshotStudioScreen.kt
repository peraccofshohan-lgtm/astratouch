package com.astratouch.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astratouch.app.screenshot.CaptureFile
import com.astratouch.app.screenshot.ScreenshotStore
import com.astratouch.app.service.action.ActionRegistry
import com.astratouch.app.ui.components.AstraCard
import com.astratouch.app.ui.components.AstraScaffold
import com.astratouch.app.ui.components.EmptyState
import com.astratouch.app.ui.components.SectionHeader
import com.astratouch.app.ui.theme.LocalAstraColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ScreenshotStudioViewModel @Inject constructor(
    private val store: ScreenshotStore,
    private val recordingStore: com.astratouch.app.screenshot.RecordingStore,
    private val actionRegistry: ActionRegistry,
) : ViewModel() {

    private val _captures = MutableStateFlow<List<CaptureFile>>(store.refresh())
    val captures: StateFlow<List<CaptureFile>> = _captures.asStateFlow()

    private val _selected = MutableStateFlow<CaptureFile?>(null)
    val selected: StateFlow<CaptureFile?> = _selected.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _recordings = MutableStateFlow(recordingStore.list())
    val recordings: StateFlow<List<com.astratouch.app.screenshot.RecordingFile>> =
        _recordings.asStateFlow()

    fun refresh() {
        _captures.value = store.refresh()
        _recordings.value = recordingStore.list()
    }

    fun deleteRecording(recording: com.astratouch.app.screenshot.RecordingFile) {
        val ok = recordingStore.delete(recording.file)
        _message.value = if (ok) "Deleted ${recording.name}." else "Could not delete ${recording.name}."
        refresh()
    }

    fun recordingUri(recording: com.astratouch.app.screenshot.RecordingFile): android.net.Uri =
        recordingStore.shareUri(recording.file)

    fun select(file: CaptureFile?) { _selected.value = file }

    fun captureNow() {
        viewModelScope.launch {
            _busy.value = true
            val result = actionRegistry.execute(com.astratouch.app.domain.model.ActionIds.SCREENSHOT)
            _message.value = when (result) {
                is com.astratouch.app.service.action.ActionResult.Success -> "Screenshot captured."
                is com.astratouch.app.service.action.ActionResult.Partial -> result.explanation
                is com.astratouch.app.service.action.ActionResult.NeedsPermission ->
                    "The AstraTouch accessibility service must be enabled to take screenshots."
                is com.astratouch.app.service.action.ActionResult.Unsupported -> result.explanation
                is com.astratouch.app.service.action.ActionResult.Failure -> result.explanation
                com.astratouch.app.service.action.ActionResult.Cancelled -> "Cancelled."
            }
            refresh()
            _busy.value = false
        }
    }

    fun delete(file: CaptureFile) {
        store.delete(file.file)
        if (_selected.value?.file == file.file) _selected.value = null
        refresh()
    }

    fun saveEdited(bitmap: Bitmap, suffix: String) {
        viewModelScope.launch {
            val file = store.saveEdited(bitmap, suffix)
            _message.value = if (file != null) "Saved ${file.name}." else "Could not save the edited image."
            refresh()
        }
    }

    fun shareUri(file: File): android.net.Uri = store.shareUri(file)

    fun dismiss() { _message.value = null }
}

@Composable
fun ScreenshotStudioScreen(
    onBack: () -> Unit,
    viewModel: ScreenshotStudioViewModel = hiltViewModel(),
) {
    val astra = LocalAstraColors.current
    val context = LocalContext.current
    val captures by viewModel.captures.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val message by viewModel.message.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val recordings by viewModel.recordings.collectAsState()
    var showEditor by remember { mutableStateOf(false) }
    var recordingActive by remember { mutableStateOf(false) }

    val projectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            recordingActive = true
            com.astratouch.app.service.ScreenCaptureService.start(context, result.resultCode, result.data!!)
        } else {
            viewModel.dismiss()
        }
    }

    AstraScaffold(
        title = "Screenshot studio",
        subtitle = "Capture, crop, annotate and share",
        onBack = onBack,
        actions = {
            TextButton(onClick = { viewModel.captureNow() }, enabled = !busy) { Text("Capture") }
        },
    ) { modifier ->
        LazyColumn(
            modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                AstraCard {
                    Column {
                        Text("Capture", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Screenshots use Android's accessibility screenshot API (Android 11+). " +
                                "Secure screens — banking apps and DRM video — refuse capture by design.",
                            style = MaterialTheme.typography.bodySmall,
                            color = astra.onBackground.copy(alpha = 0.68f),
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            androidx.compose.material3.Button(
                                onClick = { viewModel.captureNow() },
                                enabled = !busy,
                                modifier = Modifier.weight(1f),
                            ) { Text(if (busy) "Capturing…" else "Screenshot now") }
                            androidx.compose.material3.OutlinedButton(
                                onClick = {
                                    val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                                        as MediaProjectionManager
                                    projectionLauncher.launch(manager.createScreenCaptureIntent())
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !recordingActive,
                            ) { Text(if (recordingActive) "Recording…" else "Record screen") }
                        }
                        if (recordingActive) {
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = {
                                com.astratouch.app.service.ScreenCaptureService.stop(context)
                                recordingActive = false
                            }) { Text("Stop recording") }
                        }
                    }
                }
            }
            if (message != null) {
                item {
                    Text(message!!, style = MaterialTheme.typography.labelMedium, color = astra.warning)
                }
            }
            if (captures.isEmpty()) {
                item {
                    EmptyState(
                        title = "No screenshots yet",
                        message = "Tap Screenshot now, or map the Screenshot action to a gesture in the Gesture Manager.",
                    )
                }
            } else {
                item { SectionHeader("Captures", "${captures.size} file(s) in app-private storage") }
                items(captures.size) { index ->
                    val capture = captures[index]
                    AstraCard {
                        Column {
                            val bitmap = remember(capture.name) {
                                runCatching { BitmapFactory.decodeFile(capture.file.absolutePath) }.getOrNull()
                            }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = capture.name,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Fit,
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(capture.name, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                    Text(
                                        capture.readableSize,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = astra.onBackground.copy(alpha = 0.5f),
                                    )
                                }
                                TextButton(onClick = {
                                    viewModel.select(capture)
                                    showEditor = true
                                }) { Text("Edit") }
                                TextButton(onClick = { share(context, viewModel.shareUri(capture.file)) }) {
                                    Icon(Icons.Filled.Share, contentDescription = "Share", modifier = Modifier.size(16.dp))
                                }
                                TextButton(onClick = { viewModel.delete(capture) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }

            item {
                SectionHeader(
                    "Recordings",
                    if (recordings.isEmpty()) "No recordings yet" else "${recordings.size} MP4 file(s) in app-private storage",
                )
            }
            if (recordings.isEmpty()) {
                item {
                    Text(
                        "Screen recordings are saved as MP4 in app-private storage. Press " +
                            "\"Record screen\" above, approve Android's capture dialog, then stop from here " +
                            "or from the ongoing notification.",
                        style = MaterialTheme.typography.bodySmall,
                        color = astra.onBackground.copy(alpha = 0.6f),
                    )
                }
            } else {
                items(recordings.size) { index ->
                    val recording = recordings[index]
                    AstraCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = astra.primary,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(recording.name, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                Text(
                                    recording.readableSize,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = astra.onBackground.copy(alpha = 0.5f),
                                )
                            }
                            TextButton(onClick = { shareRecording(context, viewModel, recording) }) {
                                Text("Play")
                            }
                            TextButton(onClick = { viewModel.deleteRecording(recording) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    val editing = selected
    if (showEditor && editing != null) {
        ImageEditorDialog(
            file = editing.file,
            onDismiss = { showEditor = false },
            onSave = { bitmap, suffix ->
                viewModel.saveEdited(bitmap, suffix)
                showEditor = false
            },
        )
    }
}

/**
 * Hands the MP4 to whatever handles `video/mp4`. Uses the FileProvider URI with a
 * read grant, so no other app gains access to AstraTouch's private storage.
 */
private fun shareRecording(
    context: Context,
    viewModel: ScreenshotStudioViewModel,
    recording: com.astratouch.app.screenshot.RecordingFile,
) {
    val uri = runCatching { viewModel.recordingUri(recording) }.getOrNull()
    if (uri == null) {
        android.widget.Toast.makeText(context, "Could not open that recording.", android.widget.Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "video/mp4")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, "Play recording")) }
        .onFailure {
            android.widget.Toast.makeText(context, "No app can play MP4 on this device.", android.widget.Toast.LENGTH_SHORT).show()
        }
}

private fun share(context: Context, uri: android.net.Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, "Share screenshot")) }
}

/**
 * Minimal but genuinely functional editor: drag to crop (top-left anchored
 * selection) or draw annotation strokes. Saving writes a new PNG.
 */
@Composable
private fun ImageEditorDialog(
    file: File,
    onDismiss: () -> Unit,
    onSave: (Bitmap, String) -> Unit,
) {
    val astra = LocalAstraColors.current
    val bitmap = remember(file.absolutePath) {
        runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }
    var mode by remember { mutableStateOf(0) } // 0 crop, 1 annotate
    var cropFraction by remember { mutableStateOf(1f) }
    var strokes by remember { mutableStateOf<List<List<Offset>>>(emptyList()) }
    val scope = rememberCoroutineScope()

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (mode == 0) "Crop" else "Annotate") },
        text = {
            Column {
                if (bitmap == null) {
                    Text("This image could not be decoded.")
                    return@Column
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChipLite("Crop", mode == 0) { mode = 0 }
                    FilterChipLite("Draw", mode == 1) { mode = 1 }
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black)
                        .pointerInput(mode) {
                            if (mode == 0) {
                                detectDragGestures(
                                    onDrag = { change, _ ->
                                        change.consume()
                                        cropFraction = (change.position.x / size.width).coerceIn(0.15f, 1f)
                                    },
                                )
                            } else {
                                var current = mutableListOf<Offset>()
                                detectDragGestures(
                                    onDragStart = { offset -> current = mutableListOf(offset) },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        current.add(change.position)
                                        strokes = strokes + listOf(current.toList())
                                    },
                                )
                            }
                        },
                ) {
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                        drawImage(
                            bitmap.asImageBitmap(),
                            dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()),
                        )
                        strokes.forEach { stroke ->
                            for (i in 0 until stroke.size - 1) {
                                drawLine(
                                    color = androidx.compose.ui.graphics.Color(0xFFFF3B6B),
                                    start = stroke[i],
                                    end = stroke[i + 1],
                                    strokeWidth = 6f,
                                )
                            }
                        }
                        if (mode == 0) {
                            drawRect(
                                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.12f),
                                topLeft = Offset(size.width * cropFraction, 0f),
                                size = androidx.compose.ui.geometry.Size(
                                    size.width * (1f - cropFraction),
                                    size.height,
                                ),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (mode == 0) "Drag horizontally to choose how much to keep."
                    else "Drag on the image to draw red annotations.",
                    style = MaterialTheme.typography.labelSmall,
                    color = astra.onBackground.copy(alpha = 0.6f),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val source = bitmap ?: return@TextButton
                scope.launch {
                    val result = when (mode) {
                        0 -> {
                            val width = (source.width * cropFraction).toInt().coerceAtLeast(16)
                            Bitmap.createBitmap(source, 0, 0, width, source.height)
                        }
                        else -> {
                            val mutable = source.copy(Bitmap.Config.ARGB_8888, true)
                            val canvas = android.graphics.Canvas(mutable)
                            val paint = android.graphics.Paint().apply {
                                color = android.graphics.Color.rgb(255, 59, 107)
                                strokeWidth = 8f
                                style = android.graphics.Paint.Style.STROKE
                                strokeCap = android.graphics.Paint.Cap.ROUND
                            }
                            val scaleX = source.width.toFloat() / 1000f
                            val scaleY = source.height.toFloat() / 1000f
                            strokes.forEach { stroke ->
                                val path = android.graphics.Path()
                                stroke.forEachIndexed { i, point ->
                                    val x = point.x * scaleX
                                    val y = point.y * scaleY
                                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                }
                                canvas.drawPath(path, paint)
                            }
                            mutable
                        }
                    }
                    onSave(result, if (mode == 0) "cropped" else "annotated")
                }
            }) { Text("Save copy") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
