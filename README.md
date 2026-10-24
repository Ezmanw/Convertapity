# Convertapity

A simple Material 3 Android app that converts media files with FFmpeg. Pick a file, choose an output
format, flip the toggles, tap Convert. Results are saved to `Downloads/Convertapity`.

## Features

| Output | Formats | Options |
|---|---|---|
| Video | MP4, MKV, WebM | include audio track, include video track, fast copy (no re-encode), limit resolution, quality, audio bitrate, mono, normalize volume |
| Audio | MP3, M4A (AAC), OGG, FLAC, WAV | bitrate, mono, normalize volume |
| GIF | GIF | fps, width, loop |
| Image | PNG, JPG, WebP (single frame) | frame timestamp |
| All | | strip metadata, overwrite existing |

Options only appear when they apply to the chosen output format.

## Getting the APK

Every push runs the **Build & release v1** GitHub Actions workflow (`.github/workflows/build.yml`),
which builds the debug APK and publishes it to the **v1** release on this repo. It's also attached to
each run as the `Convertapity-debug` artifact.

### One-time setup: FFmpegKit AAR

The official ffmpeg-kit artifacts were removed from Maven Central, so the build needs an AAR from you.
Use a "full" build (needs libmp3lame, libvorbis, libvpx, libopus). Either:

- commit it to `app/libs/` (see `app/libs/README.md`), or
- set the repo variable `FFMPEG_KIT_AAR_URL` to a direct download link
  (Settings > Secrets and variables > Actions > Variables).

## Building locally

Needs JDK 17 and Android SDK 35, plus the AAR in `app/libs/`.

```
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Layout

- `CommandBuilder.kt` builds the ffmpeg arguments from the chosen options
- `Converter.kt` runs ffmpeg, reports progress, saves to Downloads
- `ui/ConvertScreen.kt` the Compose UI
