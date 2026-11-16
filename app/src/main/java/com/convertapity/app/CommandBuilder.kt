package com.convertapity.app

import java.util.Locale

/** Builds ffmpeg argument lists. Pure functions so they're easy to reason about. */
object CommandBuilder {

    fun build(input: String, output: String, format: OutputFormat, o: Options): List<String> {
        val args = mutableListOf("-y", "-i", input)
        if (o.stripMetadata) args += listOf("-map_metadata", "-1")

        when (format.kind) {
            Kind.VIDEO -> video(args, format, o)
            Kind.AUDIO -> audio(args, format, o)
            Kind.GIF -> gif(args, o)
            Kind.IMAGE -> args += listOf("-an", "-frames:v", "1") + imageQuality(format)
        }
        args += output
        return args
    }

    /** One frame at [ms]. */
    fun frame(input: String, output: String, ms: Long, format: OutputFormat, halfSize: Boolean): List<String> =
        listOf("-y", "-ss", seconds(ms), "-i", input, "-frames:v", "1", "-an") +
            (if (halfSize) listOf("-vf", "scale=iw/2:-2") else emptyList()) +
            imageQuality(format) + output

    /** Every frame (fps == 0) or [fps] frames per second between [startMs] and [endMs]. [pattern] like out/frame_%05d.png */
    fun frames(input: String, pattern: String, startMs: Long, endMs: Long, fps: Float, format: OutputFormat, halfSize: Boolean): List<String> {
        val vf = buildList {
            if (fps > 0f) add("fps=${String.format(Locale.US, "%.3f", fps)}")
            if (halfSize) add("scale=iw/2:-2")
        }
        return listOf("-y", "-ss", seconds(startMs), "-i", input, "-t", seconds(endMs - startMs), "-an") +
            (if (vf.isNotEmpty()) listOf("-vf", vf.joinToString(",")) else listOf("-fps_mode", "passthrough")) +
            imageQuality(format) + pattern
    }

    private fun seconds(ms: Long) = String.format(Locale.US, "%.3f", ms / 1000.0)

    private fun imageQuality(f: OutputFormat) = when (f) {
        OutputFormat.JPG -> listOf("-q:v", "2")
        OutputFormat.WEBP -> listOf("-quality", "90")
        else -> emptyList()
    }

    private fun video(a: MutableList<String>, f: OutputFormat, o: Options) {
        if (!o.includeVideo) a += "-vn"
        if (!o.includeAudio) a += "-an"

        if (o.fastCopy) {
            a += listOf("-c", "copy")
            return
        }
        if (o.includeVideo) {
            if (o.scaleDown) a += listOf("-vf", "scale=-2:'min(${o.maxHeight},ih)'")
            if (f == OutputFormat.WEBM) {
                a += listOf("-c:v", "libvpx-vp9", "-crf", o.quality.toString(), "-b:v", "0")
            } else {
                a += listOf("-c:v", "libx264", "-preset", "veryfast", "-crf", o.quality.toString(), "-pix_fmt", "yuv420p")
            }
        }
        if (o.includeAudio) {
            a += listOf("-c:a", if (f == OutputFormat.WEBM) "libopus" else "aac", "-b:a", "${o.bitrateKbps}k")
            if (o.mono) a += listOf("-ac", "1")
            if (o.normalizeVolume) a += listOf("-af", "loudnorm")
        }
    }

    private fun audio(a: MutableList<String>, f: OutputFormat, o: Options) {
        a += "-vn"
        val codec = when (f) {
            OutputFormat.MP3 -> "libmp3lame"
            OutputFormat.AAC -> "aac"
            OutputFormat.OGG -> "libvorbis"
            OutputFormat.FLAC -> "flac"
            else -> "pcm_s16le"
        }
        a += listOf("-c:a", codec)
        if (f.isLossy) a += listOf("-b:a", "${o.bitrateKbps}k")
        if (o.mono) a += listOf("-ac", "1")
        if (o.normalizeVolume) a += listOf("-af", "loudnorm")
    }

    private fun gif(a: MutableList<String>, o: Options) {
        a += "-an"
        // Two-pass palette in one filter graph: much better colours than the default GIF palette
        a += listOf(
            "-vf",
            "fps=${o.gifFps},scale=${o.gifWidth}:-1:flags=lanczos,split[a][b];[a]palettegen[p];[b][p]paletteuse",
        )
        a += listOf("-loop", if (o.gifLoop) "0" else "-1")
    }
}

val OutputFormat.isLossy get() = this == OutputFormat.MP3 || this == OutputFormat.AAC || this == OutputFormat.OGG
