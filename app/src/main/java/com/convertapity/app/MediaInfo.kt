package com.convertapity.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import java.util.Locale

data class MediaInfo(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val mime: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val hasVideo: Boolean,
    val hasAudio: Boolean,
) {
    val isImage get() = mime.startsWith("image/")
    val isVideo get() = !isImage && hasVideo && durationMs > 0
    val aspect: Float get() = if (width > 0 && height > 0) width / height.toFloat() else 16 / 9f
    val baseName get() = name.substringBeforeLast('.')
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> String.format(Locale.US, "%.1f GB", bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", bytes / (1L shl 20).toDouble())
    bytes >= 1L shl 10 -> String.format(Locale.US, "%.0f KB", bytes / (1L shl 10).toDouble())
    else -> "$bytes B"
}

/** 83_456 -> "1:23.4"; shows hours only when needed. */
fun formatTime(ms: Long, tenths: Boolean = true): String {
    val totalS = ms / 1000
    val h = totalS / 3600
    val m = (totalS % 3600) / 60
    val s = totalS % 60
    val base = if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
    return if (tenths) "$base.${(ms % 1000) / 100}" else base
}

/** Reads metadata and preview frames. MediaMetadataRetriever isn't thread-safe, so everything runs on one thread. */
@OptIn(ExperimentalCoroutinesApi::class)
class MediaProbe(private val context: Context) {
    private val io = Dispatchers.IO.limitedParallelism(1)
    private var retriever: MediaMetadataRetriever? = null

    suspend fun open(uri: Uri): MediaInfo = withContext(io) {
        retriever?.release()
        retriever = null
        var name = "file"
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) ?: name }
                c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { size = c.getLong(it) }
            }
        }
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"

        if (mime.startsWith("image/")) {
            val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, opts) }
            return@withContext MediaInfo(uri, name, size, mime, 0, opts.outWidth, opts.outHeight, hasVideo = false, hasAudio = false)
        }

        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            retriever = r
            fun key(k: Int) = r.extractMetadata(k)
            val rotation = key(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            var w = key(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            var h = key(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            if (rotation == 90 || rotation == 270) { val t = w; w = h; h = t }
            MediaInfo(
                uri, name, size, mime,
                durationMs = key(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0,
                width = w, height = h,
                hasVideo = key(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes",
                hasAudio = key(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes",
            )
        } catch (e: Exception) {
            r.release()
            MediaInfo(uri, name, size, mime, 0, 0, 0, hasVideo = false, hasAudio = mime.startsWith("audio/"))
        }
    }

    /** A frame of the currently open video, scaled to fit [maxPx]. */
    suspend fun frameAt(ms: Long, maxPx: Int): Bitmap? = withContext(io) {
        val r = retriever ?: return@withContext null
        try {
            r.getScaledFrameAtTime(ms * 1000, MediaMetadataRetriever.OPTION_CLOSEST, maxPx, maxPx)
        } catch (_: Exception) { null }
    }

    /** Quick thumbnail for the file card: a video frame or the image itself. */
    suspend fun thumbnail(info: MediaInfo, maxPx: Int): Bitmap? = withContext(io) {
        try {
            when {
                info.isImage -> ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, info.uri)) { d, i, _ ->
                    val scale = maxPx / maxOf(i.size.width, i.size.height).toFloat()
                    if (scale < 1f) d.setTargetSize((i.size.width * scale).toInt().coerceAtLeast(1), (i.size.height * scale).toInt().coerceAtLeast(1))
                }
                info.hasVideo -> retriever?.getScaledFrameAtTime(minOf(1_000_000L, info.durationMs * 500), MediaMetadataRetriever.OPTION_CLOSEST_SYNC, maxPx, maxPx)
                else -> retriever?.embeddedPicture?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }
            }
        } catch (_: Exception) { null }
    }

    fun close() { retriever?.release(); retriever = null }
}
