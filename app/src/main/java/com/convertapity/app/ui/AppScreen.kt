package com.convertapity.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Transform
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.convertapity.app.AppViewModel
import com.convertapity.app.Engine
import com.convertapity.app.FrameScope
import com.convertapity.app.MediaInfo
import com.convertapity.app.Mode
import com.convertapity.app.UiState
import com.convertapity.app.Work
import com.convertapity.app.formatSize
import com.convertapity.app.formatTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(vm: AppViewModel = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val snackbar = remember { SnackbarHostState() }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val openPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::pick) }
    val saveFile = rememberLauncherForActivityResult(CreateFile) { it?.let(vm::start) }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(vm::start) }
    val pick = { openPicker.launch(arrayOf("video/*", "audio/*", "image/*")) }

    LaunchedEffect(Unit) {
        vm.noticeFlow.collect { n ->
            if (n.openUri != null) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            val r = snackbar.showSnackbar(n.text, actionLabel = if (n.openUri != null) "Open" else null, withDismissAction = n.openUri == null)
            if (r == SnackbarResult.ActionPerformed && n.openUri != null) {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW).setDataAndType(n.openUri, n.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                    )
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Convertapity", fontWeight = FontWeight.SemiBold) },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.topAppBarColors(scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            ActionBar(
                s,
                onGo = {
                    when {
                        s.mode == Mode.FRAMES && s.frames.scope == FrameScope.RANGE -> pickFolder.launch(null)
                        else -> saveFile.launch(vm.outputMime() to vm.suggestedName())
                    }
                },
                onCancel = vm::cancel,
            )
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AnimatedVisibility(
                visible = s.engine != Engine.Ready,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) { EngineCard(s.engine, vm::downloadEngine) }

            FileCard(s, pick)

            AnimatedVisibility(
                visible = s.media?.isVideo == true,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                ConnectedChoice(
                    options = listOf(Mode.CONVERT to "Convert", Mode.FRAMES to "Frames"),
                    selected = s.mode,
                    onSelect = vm::setMode,
                    icons = listOf(Icons.Rounded.Transform, Icons.Rounded.PhotoLibrary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            AnimatedContent(
                targetState = s.mode,
                transitionSpec = {
                    val dir = if (targetState == Mode.FRAMES) 1 else -1
                    (slideInHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { it / 4 * dir } + fadeIn()) togetherWith
                        (slideOutHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { -it / 4 * dir } + fadeOut()) using
                        SizeTransform(clip = false)
                },
                label = "mode",
            ) { mode ->
                when (mode) {
                    Mode.CONVERT -> ConvertPane(s, vm)
                    Mode.FRAMES -> FramesPane(s, vm)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// --- Engine download ---

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EngineCard(engine: Engine, onDownload: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(28.dp), color = c.tertiaryContainer, contentColor = c.onTertiaryContainer) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (engine == Engine.Unsupported) Icons.Rounded.ErrorOutline else Icons.Rounded.CloudDownload, null)
                Spacer(Modifier.width(12.dp))
                Text(
                    if (engine == Engine.Unsupported) "Device not supported" else "One-time setup",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            when (engine) {
                Engine.Unsupported -> Text("The converter engine needs a 64-bit ARM phone.")
                is Engine.Missing -> {
                    Text("Convertapity needs to download its converter engine (ffmpeg, about 42 MB). After that it works offline.")
                    engine.error?.let { Text(it, color = c.error, style = MaterialTheme.typography.bodySmall) }
                    Button(onClick = onDownload) {
                        Icon(Icons.Rounded.CloudDownload, null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(if (engine.error != null) "Try again" else "Download engine")
                    }
                }
                is Engine.Downloading -> {
                    val p = engine.progress
                    if (p == null) {
                        LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                    } else {
                        val animated by animateFloatAsState(p, label = "dl")
                        LinearWavyProgressIndicator(progress = { animated }, modifier = Modifier.fillMaxWidth())
                        Text("${(p * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                    }
                }
                Engine.Ready -> Unit
            }
        }
    }
}

// --- File card ---

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
private fun FileCard(s: UiState, onPick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Surface(
        onClick = onPick,
        enabled = s.work is Work.Idle,
        shape = RoundedCornerShape(28.dp),
        color = c.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        AnimatedContent(
            targetState = Triple(s.media, s.loadingMedia, s.media == null),
            contentKey = { (m, loading, _) -> if (loading) "loading" else m?.uri },
            transitionSpec = {
                (fadeIn(tween(220, delayMillis = 60)) + scaleIn(initialScale = 0.94f)) togetherWith fadeOut(tween(120)) using SizeTransform(clip = false)
            },
            label = "file",
        ) { (media, loading, _) ->
            when {
                loading -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                media == null -> EmptyPicker()
                else -> SelectedFile(media, s)
            }
        }
    }
}

@Composable
private fun EmptyPicker() {
    val c = MaterialTheme.colorScheme
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(1f, 1.08f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "scale")
    Column(
        Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(72.dp).scale(scale).clip(MaterialShapesCookie).background(c.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.UploadFile, null, Modifier.size(32.dp), tint = c.onPrimaryContainer) }
        Text("Choose a file", style = MaterialTheme.typography.titleLarge)
        Text("Video, audio or image", style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectedFile(m: MediaInfo, s: UiState) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(84.dp).clip(RoundedCornerShape(20.dp)).background(c.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(typeIcon(m), null, tint = c.onSecondaryContainer)
            val thumbAlpha by animateFloatAsState(if (s.thumbnail != null) 1f else 0f, tween(400), label = "thumb")
            s.thumbnail?.let { Image(it, null, Modifier.size(84.dp).alpha(thumbAlpha), contentScale = ContentScale.Crop) }
        }
        Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(m.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (m.durationMs > 0) Tag(formatTime(m.durationMs, tenths = false))
                if (m.width > 0) Tag("${m.width}×${m.height}")
                if (m.sizeBytes > 0) Tag(formatSize(m.sizeBytes))
                if (m.isVideo && !m.hasAudio) Tag("No audio")
            }
        }
        Icon(Icons.Rounded.SwapHoriz, "Change file", Modifier.padding(start = 8.dp), tint = c.onSurfaceVariant)
    }
}

private fun typeIcon(m: MediaInfo): ImageVector = when {
    m.isVideo -> Icons.Rounded.Movie
    m.isImage -> Icons.Rounded.Image
    m.hasAudio -> Icons.Rounded.AudioFile
    else -> Icons.Rounded.InsertDriveFile
}

@Composable
private fun Tag(text: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 3.dp), style = MaterialTheme.typography.labelMedium)
    }
}

// --- Bottom action bar ---

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ActionBar(s: UiState, onGo: () -> Unit, onCancel: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Surface(color = c.surfaceContainer, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        AnimatedContent(
            targetState = s.work,
            contentKey = { it::class },
            transitionSpec = {
                (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut()) using SizeTransform(clip = false)
            },
            modifier = Modifier.navigationBarsPadding().padding(16.dp),
            label = "action",
        ) { work ->
            when (work) {
                Work.Idle -> {
                    val ready = s.engine == Engine.Ready && s.media != null
                    Button(
                        onClick = onGo,
                        enabled = ready,
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Icon(if (s.mode == Mode.FRAMES) Icons.Rounded.PhotoLibrary else Icons.Rounded.Transform, null)
                        Spacer(Modifier.width(12.dp))
                        AnimatedContent(goLabel(s), label = "go") { Text(it, style = MaterialTheme.typography.titleMedium) }
                    }
                }
                is Work.Running -> Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row {
                            Text(work.label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            work.progress?.let { Text("${(it * 100).toInt()}%", style = MaterialTheme.typography.titleSmall, color = c.primary) }
                        }
                        val p = work.progress
                        if (p == null) {
                            LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                        } else {
                            val animated by animateFloatAsState(p, spring(stiffness = Spring.StiffnessLow), label = "p")
                            LinearWavyProgressIndicator(progress = { animated }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    FilledTonalIconButton(onClick = onCancel, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.Close, "Cancel") }
                }
                Work.Done -> Row(
                    Modifier.fillMaxWidth().height(64.dp).clip(CircleShape).background(c.primaryContainer),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val pop = remember { androidx.compose.animation.core.Animatable(0.4f) }
                    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium)) }
                    Icon(Icons.Rounded.Done, null, Modifier.scale(pop.value), tint = c.onPrimaryContainer)
                    Spacer(Modifier.width(8.dp))
                    Text("Done", style = MaterialTheme.typography.titleMedium, color = c.onPrimaryContainer)
                }
            }
        }
    }
}

private fun goLabel(s: UiState): String = when {
    s.media == null -> "Choose a file first"
    s.mode == Mode.CONVERT -> "Convert to ${s.format.label}"
    s.frames.scope == FrameScope.SINGLE -> "Export frame"
    else -> s.frames.estimatedCount()?.let { "Export ~$it frames" } ?: "Export all frames"
}

/** Like CreateDocument, but the MIME type can change per call. Input is (mime, suggested file name). */
private val CreateFile = object : ActivityResultContract<Pair<String, String>, Uri?>() {
    override fun createIntent(context: android.content.Context, input: Pair<String, String>) =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(input.first)
            .putExtra(Intent.EXTRA_TITLE, input.second)

    override fun parseResult(resultCode: Int, intent: Intent?) =
        intent.takeIf { resultCode == android.app.Activity.RESULT_OK }?.data
}
