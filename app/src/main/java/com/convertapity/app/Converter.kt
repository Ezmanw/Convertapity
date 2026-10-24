package com.convertapity.app

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

sealed interface Result {
    data class Success(val name: String) : Result
    data class Failure(val message: String) : Result
    data object Cancelled : Result
}

class Converter(private val context: Context) {

    fun displayName(uri: Uri): String =
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && i >= 0) c.getString(i) else null
        } ?: "input"

    private fun durationMs(uri: Uri): Long = try {
        MediaMetadataRetriever().run {
            setDataSource(context, uri)
            val d = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            release()
            d
        }
    } catch (_: Exception) { 0L }

    fun cancel() = FFmpegKit.cancel()

    suspend fun convert(
        input: Uri,
        format: OutputFormat,
        options: Options,
        onProgress: (Float?) -> Unit,
    ): Result {
        val total = durationMs(input)
        val baseName = displayName(input).substringBeforeLast('.')
        val outName = "$baseName.${format.ext}"
        val tmp = File(context.cacheDir, "out_${System.currentTimeMillis()}.${format.ext}")
        val inPath = FFmpegKitConfig.getSafParameterForRead(context, input)
        val args = CommandBuilder.build(inPath, tmp.absolutePath, format, options)

        val session: FFmpegSession = suspendCoroutine { cont ->
            FFmpegKit.executeWithArgumentsAsync(
                args.toTypedArray(),
                { cont.resume(it) },
                null,
                { stats ->
                    onProgress(if (total > 0) (stats.time / total.toFloat()).coerceIn(0f, 1f) else null)
                },
            )
        }

        return when {
            ReturnCode.isSuccess(session.returnCode) -> {
                try {
                    saveToDownloads(tmp, outName, format.mime)
                    Result.Success(outName)
                } catch (e: Exception) {
                    Result.Failure("Could not save file: ${e.message}")
                } finally { tmp.delete() }
            }
            ReturnCode.isCancel(session.returnCode) -> { tmp.delete(); Result.Cancelled }
            else -> {
                tmp.delete()
                val log = session.allLogsAsString.orEmpty().lines().filter { it.isNotBlank() }.takeLast(4)
                Result.Failure(log.joinToString("\n").ifEmpty { "ffmpeg failed (code ${session.returnCode})" })
            }
        }
    }

    private fun saveToDownloads(file: File, name: String, mime: String) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Convertapity")
        }
        val resolver = context.contentResolver
        val dest = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore insert failed")
        resolver.openOutputStream(dest)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
    }
}
