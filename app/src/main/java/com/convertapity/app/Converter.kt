package com.convertapity.app

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

sealed interface Result {
    /** [uri] is the saved file (null for a folder of frames). */
    data class Success(val message: String, val uri: Uri?, val mime: String?) : Result
    data class Failure(val message: String) : Result
    data object Cancelled : Result
}

class Converter(private val context: Context, private val ffmpeg: FfmpegManager) {

    @Volatile private var process: Process? = null
    @Volatile private var cancelled = false

    private var stagedFor: Uri? = null
    private var staged: File? = null

    fun cancel() { cancelled = true; process?.destroy() }

    /** ffmpeg is a separate process and can't read content:// URIs, so copy the input to cache (once per file). */
    private fun stage(media: MediaInfo): File {
        staged?.takeIf { stagedFor == media.uri && it.exists() }?.let { return it }
        staged?.delete()
        val ext = media.name.substringAfterLast('.', "bin")
        val f = File(context.cacheDir, "input.$ext")
        context.contentResolver.openInputStream(media.uri)!!.use { i -> f.outputStream().use { i.copyTo(it) } }
        staged = f; stagedFor = media.uri
        return f
    }

    fun release() { staged?.delete(); staged = null; stagedFor = null }

    private data class Run(val code: Int, val log: List<String>)

    private fun run(args: List<String>, totalMs: Long, onProgress: (Float?) -> Unit): Run {
        val p = ffmpeg.process(listOf("-nostdin", "-hide_banner", "-progress", "pipe:1", "-nostats") + args).start()
        process = p
        val log = ArrayDeque<String>()
        p.inputStream.bufferedReader().forEachLine { line ->
            when {
                line.startsWith("out_time_us=") -> {
                    val us = line.substringAfter('=').toLongOrNull()
                    if (us != null && totalMs > 0) onProgress((us / 1000f / totalMs).coerceIn(0f, 1f))
                }
                line.contains('=') && line.substringBefore('=').all { it.isLetterOrDigit() || it == '_' } -> Unit
                line.isNotBlank() -> { log.addLast(line); if (log.size > 4) log.removeFirst() }
            }
        }
        return Run(p.waitFor(), log.toList()).also { process = null }
    }

    private fun copyTo(file: File, dest: Uri) {
        context.contentResolver.openOutputStream(dest, "wt")!!.use { o -> file.inputStream().use { it.copyTo(o) } }
    }

    private suspend fun guarded(block: suspend () -> Result): Result = withContext(Dispatchers.IO) {
        cancelled = false
        try {
            block()
        } catch (e: Exception) {
            if (cancelled) Result.Cancelled else Result.Failure(e.message ?: e.toString())
        } finally {
            process = null
        }
    }

    private fun failure(r: Run) =
        if (cancelled) Result.Cancelled else Result.Failure(r.log.joinToString("\n").ifEmpty { "ffmpeg exited with code ${r.code}" })

    suspend fun convert(media: MediaInfo, output: Uri, format: OutputFormat, options: Options, onProgress: (Float?) -> Unit) = guarded {
        val out = File(context.cacheDir, "out.${format.ext}")
        try {
            val r = run(CommandBuilder.build(stage(media).absolutePath, out.absolutePath, format, options), media.durationMs, onProgress)
            if (r.code != 0) return@guarded failure(r)
            copyTo(out, output)
            Result.Success("Saved as ${format.label}", output, format.mime)
        } finally { out.delete() }
    }

    suspend fun exportFrame(media: MediaInfo, output: Uri, f: FrameOptions) = guarded {
        val out = File(context.cacheDir, "frame.${f.format.ext}")
        try {
            val r = run(CommandBuilder.frame(stage(media).absolutePath, out.absolutePath, f.positionMs, f.format, f.halfSize), 0) {}
            if (r.code != 0 || !out.exists()) return@guarded failure(r)
            copyTo(out, output)
            Result.Success("Frame at ${formatTime(f.positionMs)} saved", output, f.format.mime)
        } finally { out.delete() }
    }

    /** Writes the frames into a new sub-folder of the user-picked [tree]. */
    suspend fun exportFrames(media: MediaInfo, tree: Uri, f: FrameOptions, onProgress: (Float?) -> Unit) = guarded {
        val dir = File(context.cacheDir, "frames").apply { deleteRecursively(); mkdirs() }
        try {
            val pattern = File(dir, "frame_%05d.${f.format.ext}").absolutePath
            val r = run(CommandBuilder.frames(stage(media).absolutePath, pattern, f.startMs, f.endMs, f.fps, f.format, f.halfSize), f.endMs - f.startMs) { p -> onProgress(p?.times(0.85f)) }
            if (r.code != 0) return@guarded failure(r)
            val files = dir.listFiles()?.sortedBy { it.name }.orEmpty()
            if (files.isEmpty()) return@guarded Result.Failure("No frames in that range")

            val resolver = context.contentResolver
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val folder = DocumentsContract.createDocument(resolver, parent, DocumentsContract.Document.MIME_TYPE_DIR, "${media.baseName} frames")
                ?: return@guarded Result.Failure("Couldn't create a folder there")
            files.forEachIndexed { i, file ->
                if (cancelled) return@guarded Result.Cancelled
                val doc = DocumentsContract.createDocument(resolver, folder, f.format.mime, file.name) ?: error("Couldn't create ${file.name}")
                copyTo(file, doc)
                onProgress(0.85f + 0.15f * (i + 1) / files.size)
            }
            Result.Success("${files.size} frames saved", null, null)
        } finally { dir.deleteRecursively() }
    }
}
