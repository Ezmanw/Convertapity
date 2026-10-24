package com.convertapity.app

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Downloads a static arm64 ffmpeg binary (published by this repo's CI on the v1 release) on first use. */
class FfmpegManager(context: Context) {
    val file = File(context.filesDir, "ffmpeg")

    val supported: Boolean get() = Build.SUPPORTED_ABIS.contains("arm64-v8a")

    fun isInstalled(): Boolean = file.exists() && file.length() > 0 && file.canExecute()

    fun delete() { file.delete() }

    /** Returns null on success, or an error message. */
    suspend fun download(onProgress: (Float?) -> Unit): String? = withContext(Dispatchers.IO) {
        val part = File(file.parentFile, "ffmpeg.part")
        try {
            val conn = (URL(DOWNLOAD_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000; readTimeout = 30_000; instanceFollowRedirects = true
            }
            if (conn.responseCode != 200) return@withContext "Download failed (HTTP ${conn.responseCode}). Has the v1 release been built yet?"
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        onProgress(if (total > 0) done / total.toFloat() else null)
                    }
                }
            }
            file.delete()
            if (!part.renameTo(file)) return@withContext "Could not save ffmpeg"
            file.setExecutable(true, true)

            // Sanity check: make sure it actually runs on this device
            val p = ProcessBuilder(file.absolutePath, "-version").redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            if (p.waitFor() != 0) {
                file.delete()
                return@withContext "ffmpeg won't run on this device: ${out.take(200)}"
            }
            null
        } catch (e: Exception) {
            part.delete()
            "Download failed: ${e.message}"
        }
    }

    companion object {
        const val DOWNLOAD_URL = "https://github.com/ezmanw/convertapity/releases/download/v1/ffmpeg-arm64"
    }
}
