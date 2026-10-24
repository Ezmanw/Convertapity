# Convertapity

Simple Material 3 (Jetpack Compose) Android app that converts media with FFmpeg.
Pick a file, pick an output format, flip the option toggles, convert. Output goes to `Downloads/Convertapity`.

Per-format options: include audio / video track, fast copy, resolution limit, quality, mono, normalize volume,
audio bitrate, GIF fps/width/loop, frame timestamp for images, strip metadata, overwrite.

## Build
1. Drop an FFmpegKit AAR into `app/libs/` (see `app/libs/README.md`).
2. `./gradlew assembleDebug` (needs Android SDK 35 + JDK 17).
