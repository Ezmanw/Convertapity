package com.convertapity.app

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Mode { CONVERT, FRAMES }

sealed interface Engine {
    data object Ready : Engine
    data object Unsupported : Engine
    data class Missing(val error: String? = null) : Engine
    data class Downloading(val progress: Float?) : Engine
}

sealed interface Work {
    data object Idle : Work
    data class Running(val label: String, val progress: Float?) : Work
    data object Done : Work
}

data class UiState(
    val engine: Engine = Engine.Ready,
    val media: MediaInfo? = null,
    val loadingMedia: Boolean = false,
    val thumbnail: ImageBitmap? = null,
    val mode: Mode = Mode.CONVERT,
    val format: OutputFormat = OutputFormat.MP3,
    val options: Options = Options(),
    val frames: FrameOptions = FrameOptions(),
    val preview: ImageBitmap? = null,
    val filmstrip: List<ImageBitmap?> = emptyList(),
    val work: Work = Work.Idle,
)

/** One-shot messages for the snackbar. */
data class Notice(val text: String, val openUri: Uri? = null, val mime: String? = null)

@OptIn(FlowPreview::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val ffmpeg = FfmpegManager(app)
    private val probe = MediaProbe(app)
    private val converter = Converter(app, ffmpeg)

    private val _state = MutableStateFlow(
        UiState(
            engine = when {
                !ffmpeg.supported -> Engine.Unsupported
                ffmpeg.isInstalled() -> Engine.Ready
                else -> Engine.Missing()
            },
        ),
    )
    val state: StateFlow<UiState> = _state

    private val notices = Channel<Notice>(Channel.BUFFERED)
    val noticeFlow = notices.receiveAsFlow()

    private val seek = MutableSharedFlow<Long>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var filmstripJob: Job? = null

    init {
        // Debounced preview frame for the scrubber
        viewModelScope.launch {
            seek.debounce(40).collect { ms ->
                val m = _state.value.media ?: return@collect
                if (!m.isVideo) return@collect
                probe.frameAt(ms, 960)?.let { bmp -> _state.update { it.copy(preview = bmp.asImageBitmap()) } }
            }
        }
    }

    // --- Engine ---

    fun downloadEngine() {
        _state.update { it.copy(engine = Engine.Downloading(0f)) }
        viewModelScope.launch {
            val err = ffmpeg.download { p -> _state.update { it.copy(engine = Engine.Downloading(p)) } }
            _state.update { it.copy(engine = if (err == null) Engine.Ready else Engine.Missing(err)) }
            if (err == null) notices.send(Notice("Converter engine ready"))
        }
    }

    // --- Input ---

    fun pick(uri: Uri) {
        converter.release()
        filmstripJob?.cancel()
        _state.update { it.copy(loadingMedia = true) }
        viewModelScope.launch {
            val m = probe.open(uri)
            val s = _state.value
            val format = when {
                m.allows(s.format) -> s.format
                m.isImage -> OutputFormat.JPG
                else -> OutputFormat.MP3
            }
            _state.update {
                it.copy(
                    media = m, loadingMedia = false, thumbnail = null, preview = null, filmstrip = emptyList(),
                    format = format,
                    mode = if (m.isVideo) it.mode else Mode.CONVERT,
                    frames = it.frames.copy(positionMs = 0, startMs = 0, endMs = m.durationMs),
                )
            }
            probe.thumbnail(m, 256)?.let { bmp -> _state.update { it.copy(thumbnail = bmp.asImageBitmap()) } }
            if (m.isVideo) {
                seek.tryEmit(0)
                loadFilmstrip(m)
            }
        }
    }

    private fun loadFilmstrip(m: MediaInfo) {
        val n = 10
        _state.update { it.copy(filmstrip = List(n) { null }) }
        filmstripJob = viewModelScope.launch {
            for (i in 0 until n) {
                val ms = (m.durationMs * (i + 0.5) / n).toLong()
                val bmp: Bitmap = probe.frameAt(ms, 160) ?: continue
                _state.update { s -> s.copy(filmstrip = s.filmstrip.toMutableList().also { it[i] = bmp.asImageBitmap() }) }
            }
        }
    }

    // --- Settings ---

    fun setMode(m: Mode) = _state.update { it.copy(mode = m) }
    fun setFormat(f: OutputFormat) = _state.update { it.copy(format = f) }
    fun setOptions(o: Options) = _state.update { it.copy(options = o) }

    fun setFrames(f: FrameOptions) {
        _state.update { it.copy(frames = f) }
        seek.tryEmit(f.positionMs)
    }

    /** Range slider moved: preview follows whichever end moved. */
    fun setRange(startMs: Long, endMs: Long) {
        val f = _state.value.frames
        val pos = if (startMs != f.startMs) startMs else if (endMs != f.endMs) endMs else f.positionMs
        setFrames(f.copy(startMs = startMs, endMs = endMs, positionMs = pos))
    }

    fun suggestedName(): String {
        val s = _state.value
        val base = s.media?.baseName ?: "output"
        return when (s.mode) {
            Mode.CONVERT -> "$base.${s.format.ext}"
            Mode.FRAMES -> "${base}_${formatTime(s.frames.positionMs).replace(':', '-')}.${s.frames.format.ext}"
        }
    }

    fun outputMime(): String = _state.value.let { if (it.mode == Mode.CONVERT) it.format.mime else it.frames.format.mime }

    // --- Work ---

    fun cancel() = converter.cancel()

    /** [output] is a file for convert / single frame, a folder tree for a frame range. */
    fun start(output: Uri) {
        val s = _state.value
        val media = s.media ?: return
        val label = when {
            s.mode == Mode.CONVERT -> "Converting to ${s.format.label}"
            s.frames.scope == FrameScope.SINGLE -> "Exporting frame"
            else -> "Exporting frames"
        }
        _state.update { it.copy(work = Work.Running(label, null)) }
        val progress: (Float?) -> Unit = { p -> _state.update { it.copy(work = Work.Running(label, p)) } }
        viewModelScope.launch {
            val r = when {
                s.mode == Mode.CONVERT -> converter.convert(media, output, s.format, s.options, progress)
                s.frames.scope == FrameScope.SINGLE -> converter.exportFrame(media, output, s.frames)
                else -> converter.exportFrames(media, output, s.frames, progress)
            }
            when (r) {
                is Result.Success -> {
                    _state.update { it.copy(work = Work.Done) }
                    notices.send(Notice(r.message, r.uri, r.mime))
                    delay(1600)
                    _state.update { if (it.work == Work.Done) it.copy(work = Work.Idle) else it }
                }
                is Result.Failure -> {
                    _state.update { it.copy(work = Work.Idle) }
                    notices.send(Notice("Failed: ${r.message.lines().lastOrNull { l -> l.isNotBlank() } ?: r.message}"))
                }
                Result.Cancelled -> {
                    _state.update { it.copy(work = Work.Idle) }
                    notices.send(Notice("Cancelled"))
                }
            }
        }
    }

    /** For screenshot tests: show an arbitrary state. */
    internal fun debugSetState(s: UiState) { _state.value = s }

    override fun onCleared() {
        probe.close()
        converter.release()
    }
}
