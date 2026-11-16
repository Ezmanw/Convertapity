package com.convertapity.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.convertapity.app.ui.AppScreen
import com.convertapity.app.ui.ConvertapityTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the main screens as PNGs in app/build/screenshots so the UI can be reviewed without a device. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h1400dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val video = MediaInfo(
        Uri.parse("content://test/video"), "Holiday clip 2026.mp4", 48_300_000, "video/mp4",
        durationMs = 83_400, width = 1920, height = 1080, hasVideo = true, hasAudio = true,
    )

    private fun fakeFrame(w: Int, h: Int, hue: Int): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val p = Paint().apply {
            shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(),
                android.graphics.Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.6f, 0.9f)),
                android.graphics.Color.HSVToColor(floatArrayOf((hue + 60f) % 360, 0.8f, 0.4f)), Shader.TileMode.CLAMP)
        }
        Canvas(b).drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        return b
    }

    private fun shoot(name: String, dark: Boolean = false, state: (UiState) -> UiState) {
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        vm.debugSetState(state(vm.state.value))
        if (dark) org.robolectric.RuntimeEnvironment.setQualifiers("+night")
        compose.setContent { ConvertapityTheme { AppScreen(vm) } }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2000)
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    private val loaded: (UiState) -> UiState = {
        it.copy(
            engine = Engine.Ready, media = video, thumbnail = fakeFrame(256, 144, 200).asImageBitmap(),
            format = OutputFormat.MP3, frames = FrameOptions(positionMs = 31_200, startMs = 10_000, endMs = 40_000),
            preview = fakeFrame(960, 540, 200).asImageBitmap(),
            filmstrip = List(10) { i -> fakeFrame(160, 90, 180 + i * 12).asImageBitmap() },
        )
    }

    @Test fun empty() = shoot("1_empty") { it.copy(engine = Engine.Missing()) }
    @Test fun convert() = shoot("2_convert", state = loaded)
    @Test fun convertVideo() = shoot("3_convert_mp4") { loaded(it).copy(format = OutputFormat.MP4, options = Options(scaleDown = true)) }
    @Test fun frames() = shoot("4_frames") { loaded(it).copy(mode = Mode.FRAMES) }
    @Test fun framesRange() = shoot("5_frames_range", dark = true) {
        loaded(it).let { s -> s.copy(mode = Mode.FRAMES, frames = s.frames.copy(scope = FrameScope.RANGE, positionMs = 10_000)) }
    }
    @Test fun running() = shoot("6_running") { loaded(it).copy(work = Work.Running("Converting to MP3", 0.42f)) }
}
