package com.convertapity.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Crop169
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.PhotoSizeSelectSmall
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.convertapity.app.AppViewModel
import com.convertapity.app.FrameOptions
import com.convertapity.app.FrameScope
import com.convertapity.app.OutputFormat
import com.convertapity.app.UiState
import com.convertapity.app.formatTime

@Composable
fun FramesPane(s: UiState, vm: AppViewModel) {
    val m = s.media ?: return
    val f = s.frames
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Preview(s)
        Filmstrip(s) { ms -> vm.setFrames(f.copy(positionMs = ms)) }

        ConnectedChoice(
            options = listOf(FrameScope.SINGLE to "This frame", FrameScope.RANGE to "Range"),
            selected = f.scope,
            onSelect = { vm.setFrames(f.copy(scope = it, positionMs = if (it == FrameScope.RANGE) f.startMs else f.positionMs)) },
            icons = listOf(Icons.Rounded.Crop169, Icons.Rounded.ViewCarousel),
            modifier = Modifier.fillMaxWidth(),
        )

        Section(if (f.scope == FrameScope.SINGLE) "Position" else "Range") {
            if (f.scope == FrameScope.SINGLE) Scrubber(f, m.durationMs, vm::setFrames)
            else RangePicker(f, m.durationMs, vm)
        }

        FrameFormatSection(f, vm::setFrames)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Preview(s: UiState) {
    val m = s.media ?: return
    val f = s.frames
    Surface(shape = RoundedCornerShape(28.dp), color = Color.Black, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().aspectRatio(m.aspect.coerceIn(0.75f, 2.2f))) {
            Crossfade(s.preview, animationSpec = tween(120), label = "preview") { img ->
                if (img != null) Image(img, "Frame preview", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator(color = Color.White) }
            }
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.55f),
                contentColor = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
            ) {
                Text(
                    formatTime(f.positionMs) + " / " + formatTime(m.durationMs, tenths = false),
                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                )
            }
        }
    }
}

/** Evenly spaced thumbnails; tap to jump. Range mode dims frames outside the range. */
@Composable
private fun Filmstrip(s: UiState, onSeek: (Long) -> Unit) {
    val m = s.media ?: return
    val f = s.frames
    val n = s.filmstrip.size
    if (n == 0) return
    val c = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    Row(
        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp)).background(c.surfaceContainerHighest),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        s.filmstrip.forEachIndexed { i, img ->
            val at = (m.durationMs * (i + 0.5) / n).toLong()
            val slot = m.durationMs / n
            val current = f.positionMs in (at - slot / 2)..(at + slot / 2)
            val inRange = f.scope == FrameScope.SINGLE || at in f.startMs..f.endMs
            val alpha by animateFloatAsState(if (img == null) 0f else if (inRange) 1f else 0.3f, tween(300), label = "a")
            val border by animateDpAsState(if (current) 3.dp else 0.dp, label = "b")
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSeek(at)
                    }
                    .border(border, c.primary, RoundedCornerShape(6.dp)),
            ) {
                if (img != null) Image(img, null, Modifier.fillMaxSize().alpha(alpha), contentScale = ContentScale.Crop)
            }
        }
    }
}

@Composable
private fun Scrubber(f: FrameOptions, durationMs: Long, onChange: (FrameOptions) -> Unit) {
    val haptics = LocalHapticFeedback.current
    fun step(delta: Long) {
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        onChange(f.copy(positionMs = (f.positionMs + delta).coerceIn(0, durationMs)))
    }
    Column(Modifier.padding(horizontal = 20.dp)) {
        Slider(
            value = f.positionMs.toFloat(),
            onValueChange = { onChange(f.copy(positionMs = it.toLong())) },
            valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = { step(-100) }) { Icon(Icons.Rounded.KeyboardArrowLeft, "Back 0.1 s") }
            Text(
                formatTime(f.positionMs),
                Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            FilledTonalIconButton(onClick = { step(100) }) { Icon(Icons.Rounded.KeyboardArrowRight, "Forward 0.1 s") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RangePicker(f: FrameOptions, durationMs: Long, vm: AppViewModel) {
    val haptics = LocalHapticFeedback.current
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RangeSlider(
            value = f.startMs.toFloat()..f.endMs.toFloat(),
            onValueChange = { vm.setRange(it.start.toLong(), it.endInclusive.toLong()) },
            valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatTime(f.startMs), style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"))
            Text(
                formatTime(f.endMs - f.startMs) + " long",
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Text(formatTime(f.endMs), style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"))
        }
        Text("Frames to grab", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FrameOptions.rates.forEach { (rate, label) ->
                val selected = f.fps == rate
                FilterChip(
                    selected = selected,
                    onClick = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); vm.setFrames(f.copy(fps = rate)) },
                    label = { Text(label) },
                    leadingIcon = { AnimatedVisibility(selected) { Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) } },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Text("Estimated", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            ValueBadge(f.estimatedCount()?.let { "$it frames" } ?: "All frames")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FrameFormatSection(f: FrameOptions, onChange: (FrameOptions) -> Unit) {
    val haptics = LocalHapticFeedback.current
    Section("Image format") {
        FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutputFormat.images.forEach { fmt ->
                val selected = f.format == fmt
                FilterChip(
                    selected = selected,
                    onClick = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); onChange(f.copy(format = fmt)) },
                    label = { Text(fmt.label) },
                    leadingIcon = { AnimatedVisibility(selected) { Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) } },
                )
            }
        }
        SwitchRow(Icons.Rounded.PhotoSizeSelectSmall, "Half resolution", "Smaller files, quicker export", f.halfSize) { onChange(f.copy(halfSize = it)) }
    }
}

