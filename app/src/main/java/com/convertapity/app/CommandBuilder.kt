package com.convertapity.app

/** Builds the ffmpeg argument list. Pure function so it is easy to reason about / unit test. */
object CommandBuilder {

    fun build(input: String, output: String, format: OutputFormat, o: Options): List<String> {
        val args = mutableListOf<String>()
        if (o.overwrite) args += "-y" else args += "-n"
        if (format.kind == Kind.IMAGE && o.frameAtSeconds > 0) args += listOf("-ss", o.frameAtSeconds.toString())
        args += listOf("-i", input)
        if (o.stripMetadata) args += listOf("-map_metadata", "-1")

        when (format.kind) {
            Kind.VIDEO -> video(args, format, o)
            Kind.AUDIO -> audio(args, format, o)
            Kind.GIF -> gif(args, o)
            Kind.IMAGE -> image(args, format)
        }
        args += output
        return args
    }

    private fun video(a: MutableList<String>, f: OutputFormat, o: Options) {
        if (!o.includeVideo) a += "-vn"
        if (!o.includeAudio) a += "-an"

        if (o.fastCopy) {
            a += listOf("-c", "copy")
            return
        }
        if (o.includeVideo) {
            val vf = mutableListOf<String>()
            if (o.scaleDown) vf += "scale=-2:'min(${o.maxHeight},ih)'"
            if (vf.isNotEmpty()) a += listOf("-vf", vf.joinToString(","))
            if (f == OutputFormat.WEBM) {
                a += listOf("-c:v", "libvpx-vp9", "-crf", o.quality.toString(), "-b:v", "0")
            } else {
                a += listOf("-c:v", "mpeg4", "-q:v", crfToQscale(o.quality).toString())
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
        val lossy = f == OutputFormat.MP3 || f == OutputFormat.AAC || f == OutputFormat.OGG
        if (lossy) a += listOf("-b:a", "${o.bitrateKbps}k")
        if (o.mono) a += listOf("-ac", "1")
        if (o.normalizeVolume) a += listOf("-af", "loudnorm")
    }

    private fun gif(a: MutableList<String>, o: Options) {
        a += "-an"
        a += listOf("-vf", "fps=${o.gifFps},scale=${o.gifWidth}:-1:flags=lanczos")
        a += listOf("-loop", if (o.gifLoop) "0" else "-1")
    }

    private fun image(a: MutableList<String>, f: OutputFormat) {
        a += listOf("-an", "-frames:v", "1")
        if (f == OutputFormat.JPG) a += listOf("-q:v", "2")
    }

    // CRF 18..40 -> mpeg4 qscale 2..15 (the bundled mpeg4 encoder has no CRF)
    private fun crfToQscale(crf: Int): Int = ((crf - 18) * 13 / 22 + 2).coerceIn(2, 15)
}
