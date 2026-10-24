# Convertapity

A simple Material 3 Android app that converts media files with FFmpeg. Pick a file, choose an output
format, flip the toggles, tap Convert. You choose where to save the result.

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

Every push runs the **Build & release v1** GitHub Actions workflow (`.github/workflows/build.yml`).
It builds the APK and publishes it to the **v1** release of this repo, together with `ffmpeg-arm64`.

1. Open the repo's **Releases** page, download `Convertapity-v1.apk` on your phone and install it.
2. Open the app and tap **Download ffmpeg** once (about 40 MB). After that it works offline.

Needs a 64-bit ARM phone (nearly every modern Android phone).

## Building locally

Needs JDK 17 and Android SDK 35.

```
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Notes

- The app targets SDK 28 on purpose. Android 10+ stops apps targeting 29+ from running a downloaded
  binary, and this app runs ffmpeg as one. That's fine for installing the APK yourself, but it
  rules out Google Play.
- ffmpeg is a static GPL build from johnvansickle.com, mirrored onto the v1 release by the workflow.

## Layout

- `CommandBuilder.kt` builds the ffmpeg arguments from the chosen options
- `FfmpegManager.kt` downloads and checks the ffmpeg binary
- `Converter.kt` runs ffmpeg, reports progress, writes the result to the file you pick
- `ui/ConvertScreen.kt` the Compose UI
