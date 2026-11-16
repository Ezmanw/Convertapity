package com.convertapity.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Height
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhotoSizeSelectLarge
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material.icons.rounded.SpeakerGroup
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.LayersClear
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.convertapity.app.AppViewModel
import com.convertapity.app.CommandBuilder
import com.convertapity.app.Kind
import com.convertapity.app.OutputFormat
import com.convertapity.app.UiState
import com.convertapity.app.allows
import com.convertapity.app.isLossy
import kotlin.math.roundToInt

@Composable
fun ConvertPane(s: UiState, vm: AppViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FormatPicker(s, vm::setFormat)
        OptionsSection(s, vm)
        CommandPreview(s)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormatPicker(s: UiState, onSelect: (OutputFormat) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val groups = listOf(
        "Video" to OutputFormat.entries.filter { it.kind == Kind.VIDEO || it.kind == Kind.GIF },
        "Audio" to OutputFormat.entries.filter { it.kind == Kind.AUDIO },
        "Image" to OutputFormat.images,
    )
    Section("Convert to") {
        groups.forEach { (title, formats) ->
            val anyAllowed = formats.any { s.media.allows(it) }
            AnimatedVisibility(anyAllowed, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        formats.forEach { f ->
                            val selected = s.format == f
                            FilterChip(
                                selected = selected,
                                onClick = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); onSelect(f) },
                                enabled = s.media.allows(f),
                                label = { Text(f.label) },
                                leadingIcon = {
                                    AnimatedVisibility(selected) { Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionsSection(s: UiState, vm: AppViewModel) {
    val o = s.options
    val kind = s.format.kind
    val set = vm::setOptions
    val reencodedAudio = (kind == Kind.VIDEO && o.includeAudio && !o.fastCopy) || kind == Kind.AUDIO

    Section("Options") {
        Reveal(kind == Kind.VIDEO) {
            Column {
                SwitchRow(Icons.Rounded.VolumeUp, "Include audio track", "Turn off for a silent video", o.includeAudio) { set(o.copy(includeAudio = it)) }
                SwitchRow(Icons.Rounded.Videocam, "Include video track", "Turn off to keep only the sound", o.includeVideo) { set(o.copy(includeVideo = it)) }
                SwitchRow(Icons.Rounded.Bolt, "Fast copy", "No re-encoding. Instant, but the codecs must fit the container", o.fastCopy) { set(o.copy(fastCopy = it)) }
            }
        }
        Reveal(kind == Kind.VIDEO && !o.fastCopy && o.includeVideo) {
            Column {
                SwitchRow(Icons.Rounded.AspectRatio, "Limit resolution", "Scale down to at most ${o.maxHeight}p", o.scaleDown) { set(o.copy(scaleDown = it)) }
                Reveal(o.scaleDown) {
                    SliderRow(Icons.Rounded.Height, "Max height", "${o.maxHeight}p", o.maxHeight.toFloat(), 240f..2160f, 15) {
                        set(o.copy(maxHeight = (it / 120).roundToInt() * 120))
                    }
                }
                SliderRow(Icons.Rounded.HighQuality, "Quality", qualityLabel(o.quality), (58 - o.quality).toFloat(), 18f..40f, 21) {
                    set(o.copy(quality = 58 - it.roundToInt()))
                }
            }
        }
        Reveal(reencodedAudio) {
            Column {
                SwitchRow(Icons.Rounded.SpeakerGroup, "Mono", "Mix down to one channel", o.mono) { set(o.copy(mono = it)) }
                SwitchRow(Icons.Rounded.GraphicEq, "Normalize volume", "Even out loudness (EBU R128)", o.normalizeVolume) { set(o.copy(normalizeVolume = it)) }
            }
        }
        Reveal((kind == Kind.AUDIO && s.format.isLossy) || (kind == Kind.VIDEO && o.includeAudio && !o.fastCopy)) {
            SliderRow(Icons.Rounded.MusicNote, "Audio bitrate", "${o.bitrateKbps} kbps", o.bitrateKbps.toFloat(), 64f..320f, 7) {
                set(o.copy(bitrateKbps = (it / 32).roundToInt() * 32))
            }
        }
        Reveal(kind == Kind.GIF) {
            Column {
                SwitchRow(Icons.Rounded.Loop, "Loop forever", null, o.gifLoop) { set(o.copy(gifLoop = it)) }
                SliderRow(Icons.Rounded.SlowMotionVideo, "Frame rate", "${o.gifFps} fps", o.gifFps.toFloat(), 5f..30f, 24) { set(o.copy(gifFps = it.roundToInt())) }
                SliderRow(Icons.Rounded.PhotoSizeSelectLarge, "Width", "${o.gifWidth} px", o.gifWidth.toFloat(), 120f..960f, 6) {
                    set(o.copy(gifWidth = (it / 120).roundToInt() * 120))
                }
            }
        }
        SwitchRow(Icons.Rounded.LayersClear, "Strip metadata", "Remove tags, location and other info", o.stripMetadata) { set(o.copy(stripMetadata = it)) }
    }
}

private fun qualityLabel(crf: Int) = when {
    crf <= 20 -> "Best"
    crf <= 24 -> "High"
    crf <= 29 -> "Medium"
    crf <= 34 -> "Low"
    else -> "Tiny"
} + " · CRF $crf"

@Composable
private fun Reveal(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(visible, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) { content() }
}

/** Collapsible view of the exact ffmpeg arguments, for the curious. */
@Composable
private fun CommandPreview(s: UiState) {
    var open by rememberSaveable { mutableStateOf(false) }
    Section("Advanced") {
        SwitchRow(Icons.Rounded.Code, "Show ffmpeg command", null, open) { open = it }
        Reveal(open) {
            val name = s.media?.name ?: "input"
            val cmd = (listOf("ffmpeg") + CommandBuilder.build(name, "output.${s.format.ext}", s.format, s.options))
                .joinToString(" ") { if (it.any { c -> c == ' ' || c == '\'' || c == ';' || c == '[' }) "\"$it\"" else it }
            Surface(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                SelectionContainer {
                    Text(
                        cmd,
                        Modifier.horizontalScroll(rememberScrollState()).padding(14.dp),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

