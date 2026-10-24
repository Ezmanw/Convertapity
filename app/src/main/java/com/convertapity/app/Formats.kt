package com.convertapity.app

enum class Kind { VIDEO, AUDIO, GIF, IMAGE }

enum class OutputFormat(
    val label: String,
    val ext: String,
    val mime: String,
    val kind: Kind,
) {
    MP4("MP4 video", "mp4", "video/mp4", Kind.VIDEO),
    MKV("MKV video", "mkv", "video/x-matroska", Kind.VIDEO),
    WEBM("WebM video", "webm", "video/webm", Kind.VIDEO),
    GIF("GIF animation", "gif", "image/gif", Kind.GIF),
    MP3("MP3 audio", "mp3", "audio/mpeg", Kind.AUDIO),
    AAC("AAC audio", "m4a", "audio/mp4", Kind.AUDIO),
    OGG("OGG Vorbis", "ogg", "audio/ogg", Kind.AUDIO),
    FLAC("FLAC audio", "flac", "audio/flac", Kind.AUDIO),
    WAV("WAV audio", "wav", "audio/wav", Kind.AUDIO),
    PNG("PNG image (single frame)", "png", "image/png", Kind.IMAGE),
    JPG("JPG image (single frame)", "jpg", "image/jpeg", Kind.IMAGE),
    WEBP("WebP image (single frame)", "webp", "image/webp", Kind.IMAGE),
}

/** All user-tweakable settings. Each field is surfaced as a toggle/slider in the UI where it applies. */
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
    // Image
    val frameAtSeconds: Int = 0,
    // General
    val stripMetadata: Boolean = false,
    val overwrite: Boolean = true,
)
