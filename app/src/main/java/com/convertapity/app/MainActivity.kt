package com.convertapity.app

import android.app.Application
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.convertapity.app.ui.ConvertScreen
import com.convertapity.app.ui.ConvertapityTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val input: Uri? = null,
    val inputName: String? = null,
    val format: OutputFormat = OutputFormat.MP3,
    val options: Options = Options(),
    val running: Boolean = false,
    val progress: Float? = null,
    val message: String? = null,
)

class ConvertViewModel(app: Application) : AndroidViewModel(app) {
    private val converter = Converter(app)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    fun pick(uri: Uri) = _state.update { it.copy(input = uri, inputName = converter.displayName(uri), message = null) }
    fun setFormat(f: OutputFormat) = _state.update { it.copy(format = f) }
    fun setOptions(o: Options) = _state.update { it.copy(options = o) }
    fun cancel() = converter.cancel()

    fun start() {
        val s = _state.value
        val input = s.input ?: return
        _state.update { it.copy(running = true, progress = 0f, message = null) }
        viewModelScope.launch {
            val r = converter.convert(input, s.format, s.options) { p -> _state.update { it.copy(progress = p) } }
            val msg = when (r) {
                is Result.Success -> "Saved to Downloads/Convertapity/${r.name}"
                is Result.Failure -> "Failed: ${r.message}"
                Result.Cancelled -> "Cancelled"
            }
            _state.update { it.copy(running = false, progress = null, message = msg) }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ConvertapityTheme { ConvertScreen() } }
    }
}
