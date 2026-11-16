package com.convertapity.app

enum class Kind { VIDEO, AUDIO, GIF, IMAGE }

enum class OutputFormat(
    val label: String,
    val ext: String,
    val mime: String,
    val kind: Kind,
) {
    MP4("MP4", "mp4", "video/mp4", Kind.VIDEO),
    MKV("MKV", "mkv", "video/x-matroska", Kind.VIDEO),
    WEBM("WebM", "webm", "video/webm", Kind.VIDEO),
    GIF("GIF", "gif", "image/gif", Kind.GIF),
    MP3("MP3", "mp3", "audio/mpeg", Kind.AUDIO),
    AAC("M4A", "m4a", "audio/mp4", Kind.AUDIO),
    OGG("OGG", "ogg", "audio/ogg", Kind.AUDIO),
    FLAC("FLAC", "flac", "audio/flac", Kind.AUDIO),
    WAV("WAV", "wav", "audio/wav", Kind.AUDIO),
    PNG("PNG", "png", "image/png", Kind.IMAGE),
    JPG("JPG", "jpg", "image/jpeg", Kind.IMAGE),
    WEBP("WebP", "webp", "image/webp", Kind.IMAGE);

    companion object {
        val images = listOf(PNG, JPG, WEBP)
    }
}

/** Which output formats make sense for a given input. */
fun MediaInfo?.allows(f: OutputFormat): Boolean = when {
    this == null -> true
    isVideo -> true
    isImage -> f.kind == Kind.IMAGE
    else -> f.kind == Kind.AUDIO // audio-only input
}

/** All user-tweakable conversion settings. Each is surfaced as a toggle/slider where it applies. */
data class Options(
    // Video
    val includeAudio: Boolean = true,
    val includeVideo: Boolean = true,
    val fastCopy: Boolean = false,       // stream copy, no re-encode
    val scaleDown: Boolean = false,
    val maxHeight: Int = 720,
    val quality: Int = 23,               // CRF, lower = better
    // Audio
    val mono: Boolean = false,
    val bitrateKbps: Int = 192,
    val normalizeVolume: Boolean = false,
    // GIF
    val gifFps: Int = 12,
    val gifWidth: Int = 480,
    val gifLoop: Boolean = true,
    // General
    val stripMetadata: Boolean = false,
)

enum class FrameScope { SINGLE, RANGE }

/** Frame-export settings. fps == 0 means every frame. */
data class FrameOptions(
    val positionMs: Long = 0,
    val scope: FrameScope = FrameScope.SINGLE,
    val startMs: Long = 0,
    val endMs: Long = 0,
    val fps: Float = 1f,
    val format: OutputFormat = OutputFormat.PNG,
    val halfSize: Boolean = false,
) {
    /** Rough number of frames a range export will produce, or null for "every frame". */
    fun estimatedCount(): Int? =
        if (fps <= 0f) null else maxOf(1, kotlin.math.ceil((endMs - startMs) / 1000.0 * fps).toInt())

    companion object {
        val rates = listOf(0.2f to "Every 5 s", 0.5f to "Every 2 s", 1f to "1 / s", 2f to "2 / s", 5f to "5 / s", 10f to "10 / s", 0f to "Every frame")
    }
}
