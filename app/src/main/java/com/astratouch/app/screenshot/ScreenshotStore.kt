package com.astratouch.app.screenshot

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.astratouch.app.service.action.ActionResult
import com.astratouch.app.service.action.ScreenshotSaver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists screenshots captured by the action engine into app-private storage.
 *
 * Files live in `filesDir/captures` (declared in the FileProvider paths XML), so
 * they can be shared through a granted content URI without exposing any other
 * app data. Nothing is uploaded anywhere.
 */
@Singleton
class ScreenshotStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : ScreenshotSaver {

    private val _captures = MutableStateFlow<List<CaptureFile>>(emptyList())
    val captures: StateFlow<List<CaptureFile>> = _captures.asStateFlow()

    private val directory: File
        get() = File(context.filesDir, "captures").apply { if (!exists()) mkdirs() }

    init {
        refresh()
    }

    fun refresh(): List<CaptureFile> {
        val list = directory.listFiles()
            ?.filter { it.extension.equals("png", true) || it.extension.equals("jpg", true) }
            ?.sortedByDescending { it.lastModified() }
            ?.map { CaptureFile(it, it.lastModified()) }
            ?: emptyList()
        _captures.value = list
        return list
    }

    override suspend fun save(bitmap: Bitmap): ActionResult = withContext(Dispatchers.IO) {
        try {
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val file = File(directory, "AstraTouch-$stamp.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
            }
            bitmap.recycle()
            refresh()
            ActionResult.Success
        } catch (e: Exception) {
            Log.e(TAG, "Screenshot save failed", e)
            ActionResult.Failure(
                com.astratouch.app.service.action.FailureReason.INTERNAL,
                "The screenshot was captured but could not be saved: ${e.message ?: "storage error"}",
            )
        }
    }

    /** Saves an edited bitmap (crop/annotate) as a new capture. */
    suspend fun saveEdited(bitmap: Bitmap, suffix: String = "edited"): File? =
        withContext(Dispatchers.IO) {
            try {
                val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                val file = File(directory, "AstraTouch-$stamp-$suffix.png")
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                refresh()
                file
            } catch (e: Exception) {
                Log.e(TAG, "Edited save failed", e)
                null
            }
        }

    fun delete(file: File): Boolean {
        val ok = file.delete()
        refresh()
        return ok
    }

    fun shareUri(file: File): android.net.Uri = androidx.core.content.FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )

    companion object {
        private const val TAG = "AstraScreenshot"
    }
}

data class CaptureFile(
    val file: File,
    val modifiedAt: Long,
) {
    val name: String get() = file.name
    val sizeBytes: Long get() = file.length()
    val readableSize: String
        get() = when {
            sizeBytes > 1024 * 1024 -> String.format(Locale.US, "%.1f MB", sizeBytes / 1024f / 1024f)
            sizeBytes > 1024 -> String.format(Locale.US, "%.0f KB", sizeBytes / 1024f)
            else -> "$sizeBytes B"
        }
}
