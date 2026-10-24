package com.convertapity.app

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Downloads an Android-native ffmpeg (Termux's build plus its shared libs, packaged by this repo's CI
 * onto the v1 release) on first use, and runs it with LD_LIBRARY_PATH pointing at the bundled libs.
 */
class FfmpegManager(context: Context) {
    private val dir = File(context.filesDir, "ffmpeg-dist")
    private val binary = File(dir, "bin/ffmpeg")
    private val libDir = File(dir, "lib")

    init {
        File(context.filesDir, "ffmpeg").delete() // leftover from the old glibc build
    }

    val supported: Boolean get() = Build.SUPPORTED_ABIS.contains("arm64-v8a")

    fun isInstalled(): Boolean = binary.canExecute() && File(dir, ".ok").exists()

    fun process(args: List<String>): ProcessBuilder =
        ProcessBuilder(listOf(binary.absolutePath) + args).apply {
            environment()["LD_LIBRARY_PATH"] = libDir.absolutePath
            redirectErrorStream(true)
        }

    /** Returns null on success, or an error message. */
    suspend fun download(onProgress: (Float?) -> Unit): String? = withContext(Dispatchers.IO) {
        try {
            dir.deleteRecursively()
            dir.mkdirs()
            val conn = (URL(DOWNLOAD_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000; readTimeout = 30_000; instanceFollowRedirects = true
            }
            if (conn.responseCode != 200) return@withContext "Download failed (HTTP ${conn.responseCode}). Has the v1 release been built yet?"
            val total = conn.contentLengthLong
            var done = 0L
            val counting = object : java.io.FilterInputStream(conn.inputStream) {
                override fun read(b: ByteArray, off: Int, len: Int): Int =
                    super.read(b, off, len).also { n ->
                        if (n > 0) { done += n; onProgress(if (total > 0) done / total.toFloat() else null) }
                    }
            }
            ZipInputStream(counting.buffered()).use { zip ->
                val root = dir.canonicalPath + File.separator
                while (true) {
                    val e = zip.nextEntry ?: break
                    val out = File(dir, e.name)
                    if (!out.canonicalPath.startsWith(root)) continue
                    if (e.isDirectory) { out.mkdirs(); continue }
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zip.copyTo(it) }
                }
            }
            if (!binary.exists()) return@withContext "Download was incomplete, try again"
            binary.setExecutable(true, true)

            // Sanity check: make sure it actually runs on this device
            val p = process(listOf("-hide_banner", "-version")).start()
            val out = p.inputStream.bufferedReader().readText()
            if (p.waitFor() != 0) {
                dir.deleteRecursively()
                return@withContext "ffmpeg won't run on this device:\n${out.take(300)}"
            }
            File(dir, ".ok").createNewFile()
            null
        } catch (e: Exception) {
            dir.deleteRecursively()
            "Download failed: ${e.message ?: e.toString()}"
        }
    }

    companion object {
        const val DOWNLOAD_URL = "https://github.com/ezmanw/convertapity/releases/download/v1/ffmpeg-arm64.zip"
    }
}
