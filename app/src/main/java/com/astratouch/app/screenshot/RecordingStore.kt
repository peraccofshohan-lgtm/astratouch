package com.astratouch.app.screenshot

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the MP4 files written by `ScreenCaptureService`.
 *
 * Recording files live in `filesDir/recordings`, which is declared in
 * `res/xml/file_paths.xml`, so they can be shared through a granted content URI.
 *
 * Why this exists: v1.0.0 created recordings but offered no way to find, play,
 * share or delete them — they accumulated invisibly in app storage. That was a
 * genuine feature hole, not a documentation gap.
 */
@Singleton
class RecordingStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val directory: File
        get() = File(context.filesDir, "recordings").apply { if (!exists()) mkdirs() }

    /** Recording files, newest first. */
    fun list(): List<RecordingFile> =
        directory.listFiles()
            ?.filter { it.isFile && it.extension.equals("mp4", true) }
            ?.sortedByDescending { it.lastModified() }
            ?.map { RecordingFile(it, it.lastModified(), it.length()) }
            ?: emptyList()

    fun delete(file: File): Boolean = file.delete()

    fun shareUri(file: File): android.net.Uri =
        androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
}

data class RecordingFile(
    val file: File,
    val modifiedAt: Long,
    val sizeBytes: Long,
) {
    val name: String get() = file.name

    val readableSize: String
        get() = when {
            sizeBytes > 1024 * 1024 -> String.format(Locale.US, "%.1f MB", sizeBytes / 1024f / 1024f)
            sizeBytes > 1024 -> String.format(Locale.US, "%.0f KB", sizeBytes / 1024f)
            else -> "$sizeBytes B"
        }
}
