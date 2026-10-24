package com.convertapity.app

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

sealed interface Result {
    data class Success(val name: String) : Result
    data class Failure(val message: String) : Result
    data object Cancelled : Result
}

class Converter(private val context: Context, private val ffmpeg: FfmpegManager) {

    @Volatile private var process: Process? = null
    @Volatile private var cancelled = false

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

    fun cancel() { cancelled = true; process?.destroy() }

    suspend fun convert(
        input: Uri,
        output: Uri,
        format: OutputFormat,
        options: Options,
        onProgress: (Float?) -> Unit,
    ): Result = withContext(Dispatchers.IO) {
        cancelled = false
        val total = durationMs(input)
        val stamp = System.currentTimeMillis()
        val ext = displayName(input).substringAfterLast('.', "bin")
        val inFile = File(context.cacheDir, "in_$stamp.$ext")
        val outFile = File(context.cacheDir, "out_$stamp.${format.ext}")
        try {
            // ffmpeg is a separate process and can't read content:// URIs, so stage the input
            context.contentResolver.openInputStream(input)!!.use { i -> inFile.outputStream().use { i.copyTo(it) } }

            val args = listOf(ffmpeg.file.absolutePath, "-nostdin", "-progress", "pipe:1", "-nostats") +
                CommandBuilder.build(inFile.absolutePath, outFile.absolutePath, format, options)
            val p = ProcessBuilder(args).redirectErrorStream(true).start()
            process = p

            val log = ArrayDeque<String>()
            p.inputStream.bufferedReader().forEachLine { line ->
                when {
                    line.startsWith("out_time_us=") || line.startsWith("out_time_ms=") -> {
                        val us = line.substringAfter('=').toLongOrNull()
                        if (us != null && total > 0) onProgress((us / 1000f / total).coerceIn(0f, 1f))
                    }
                    line.contains('=') && line.substringBefore('=').all { it.isLetterOrDigit() || it == '_' } -> Unit
                    line.isNotBlank() -> { log.addLast(line); if (log.size > 4) log.removeFirst() }
                }
            }
            val code = p.waitFor()
            process = null

            when {
                cancelled -> Result.Cancelled
                code != 0 -> Result.Failure(log.joinToString("\n").ifEmpty { "ffmpeg exited with code $code" })
                else -> {
                    context.contentResolver.openOutputStream(output, "wt")!!.use { o -> outFile.inputStream().use { it.copyTo(o) } }
                    Result.Success(displayName(output))
                }
            }
        } catch (e: Exception) {
            if (cancelled) Result.Cancelled else Result.Failure(e.message ?: e.toString())
        } finally {
            process = null
            inFile.delete(); outFile.delete()
        }
    }
}
