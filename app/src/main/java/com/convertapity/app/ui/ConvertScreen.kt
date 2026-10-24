package com.convertapity.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.convertapity.app.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConvertScreen(vm: ConvertViewModel = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::pick)
    }
    val saver = rememberLauncherForActivityResult(CreateFile) { uri -> uri?.let(vm::start) }
    val o = s.options
    val kind = s.format.kind

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("Convertapity") }) },
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!s.ffmpegSupported) {
                Card(Modifier.fillMaxWidth()) { Text("This device isn't 64-bit ARM, so the bundled ffmpeg can't run here.", Modifier.padding(16.dp)) }
            } else if (!s.ffmpegReady) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Converter engine needed", style = MaterialTheme.typography.titleMedium)
                        Text("Convertapity needs to download ffmpeg (about 40 MB) once before it can convert anything.")
                        if (s.downloading) {
                            val p = s.downloadProgress
                            if (p == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                            else LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                        } else {
                            Button(onClick = vm::downloadFfmpeg) { Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text("Download ffmpeg") }
                        }
                    }
                }
            }

            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(s.inputName ?: "No file selected", style = MaterialTheme.typography.titleMedium)
                    FilledTonalButton(onClick = { picker.launch(arrayOf("video/*", "audio/*", "image/*")) }, enabled = !s.running) {
                        Icon(Icons.Default.FileOpen, null); Spacer(Modifier.width(8.dp)); Text("Choose file")
                    }
                    FormatDropdown(s.format, !s.running, vm::setFormat)
                }
            }

            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text("Options", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium)

                    if (kind == Kind.VIDEO) {
                        ToggleRow("Include audio track", "Turn off to produce a silent video", o.includeAudio) { vm.setOptions(o.copy(includeAudio = it)) }
                        ToggleRow("Include video track", "Turn off to keep audio only", o.includeVideo) { vm.setOptions(o.copy(includeVideo = it)) }
                        ToggleRow("Fast copy (no re-encode)", "Instant, but format must be compatible", o.fastCopy) { vm.setOptions(o.copy(fastCopy = it)) }
                        if (!o.fastCopy) {
                            ToggleRow("Limit resolution", "Scale down to ${o.maxHeight}p max", o.scaleDown) { vm.setOptions(o.copy(scaleDown = it)) }
                            if (o.scaleDown) SliderRow("Max height", "${o.maxHeight}p", o.maxHeight.toFloat(), 240f..1080f, 8) { vm.setOptions(o.copy(maxHeight = (it / 120).roundToInt() * 120)) }
                            SliderRow("Video quality", "CRF ${o.quality} (lower = better)", o.quality.toFloat(), 18f..40f, 21) { vm.setOptions(o.copy(quality = it.roundToInt())) }
                        }
                    }
                    if (kind == Kind.VIDEO && o.includeAudio && !o.fastCopy || kind == Kind.AUDIO) {
                        ToggleRow("Mono audio", "Downmix to one channel", o.mono) { vm.setOptions(o.copy(mono = it)) }
                        ToggleRow("Normalize volume", "EBU R128 loudness", o.normalizeVolume) { vm.setOptions(o.copy(normalizeVolume = it)) }
                    }
                    if (kind == Kind.AUDIO && s.format in setOf(OutputFormat.MP3, OutputFormat.AAC, OutputFormat.OGG) ||
                        kind == Kind.VIDEO && o.includeAudio && !o.fastCopy) {
                        SliderRow("Audio bitrate", "${o.bitrateKbps} kbps", o.bitrateKbps.toFloat(), 64f..320f, 7) { vm.setOptions(o.copy(bitrateKbps = (it / 32).roundToInt() * 32)) }
                    }
                    if (kind == Kind.GIF) {
                        ToggleRow("Loop forever", null, o.gifLoop) { vm.setOptions(o.copy(gifLoop = it)) }
                        SliderRow("Frame rate", "${o.gifFps} fps", o.gifFps.toFloat(), 5f..30f, 24) { vm.setOptions(o.copy(gifFps = it.roundToInt())) }
                        SliderRow("Width", "${o.gifWidth}px", o.gifWidth.toFloat(), 120f..960f, 6) { vm.setOptions(o.copy(gifWidth = (it / 120).roundToInt() * 120)) }
                    }
                    if (kind == Kind.IMAGE) {
                        SliderRow("Grab frame at", "${o.frameAtSeconds}s", o.frameAtSeconds.toFloat(), 0f..120f, 119) { vm.setOptions(o.copy(frameAtSeconds = it.roundToInt())) }
                    }
                    ToggleRow("Strip metadata", "Remove tags, location, etc.", o.stripMetadata) { vm.setOptions(o.copy(stripMetadata = it)) }
                    ToggleRow("Overwrite existing", null, o.overwrite) { vm.setOptions(o.copy(overwrite = it)) }
                }
            }

            if (s.running) {
                val p = s.progress
                if (p == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                else LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                Button(onClick = vm::cancel, Modifier.fillMaxWidth()) { Icon(Icons.Default.Stop, null); Spacer(Modifier.width(8.dp)); Text("Cancel") }
            } else {
                Button(onClick = { saver.launch(s.format.mime to vm.suggestedName()) }, enabled = s.input != null && s.ffmpegReady, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Convert")
                }
            }
            s.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormatDropdown(selected: OutputFormat, enabled: Boolean, onSelect: (OutputFormat) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { if (enabled) open = it }) {
        OutlinedTextField(
            value = selected.label, onValueChange = {}, readOnly = true, label = { Text("Convert to") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            OutputFormat.entries.forEach {
                DropdownMenuItem(text = { Text(it.label) }, onClick = { onSelect(it); open = false })
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked, onChange) },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
    )
}

@Composable
private fun SliderRow(title: String, value: String, v: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title); Text(value, style = MaterialTheme.typography.labelLarge)
        }
        Slider(value = v, onValueChange = onChange, valueRange = range, steps = steps)
    }
}

/** Like CreateDocument, but the MIME type can change per call. Input is (mime, suggested file name). */
private val CreateFile = object : androidx.activity.result.contract.ActivityResultContract<Pair<String, String>, android.net.Uri?>() {
    override fun createIntent(context: android.content.Context, input: Pair<String, String>) =
        android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(android.content.Intent.CATEGORY_OPENABLE)
            .setType(input.first)
            .putExtra(android.content.Intent.EXTRA_TITLE, input.second)

    override fun parseResult(resultCode: Int, intent: android.content.Intent?) =
        intent.takeIf { resultCode == android.app.Activity.RESULT_OK }?.data
}
