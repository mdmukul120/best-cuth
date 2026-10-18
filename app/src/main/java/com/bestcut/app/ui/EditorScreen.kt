@file:OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)

package com.bestcut.app.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.bestcut.app.Clip
import com.bestcut.app.ClipEffects
import com.bestcut.app.EditorViewModel
import com.bestcut.app.ExportState
import com.bestcut.app.FilterType
import kotlinx.coroutines.delay

private enum class Tool(val label: String, val icon: ImageVector) {
    TRIM("Trim", Icons.Default.ContentCut),
    SPEED("Speed", Icons.Default.Speed),
    FILTER("Filter", Icons.Default.Palette),
    LIGHT("Light", Icons.Default.Tune),
    TEXT("Text", Icons.Default.TextFields),
    MOVE("Move", Icons.Default.SwapHoriz)
}

private class ToolEntry(
    val label: String,
    val icon: ImageVector,
    val active: Boolean = false,
    val onClick: () -> Unit
)

private fun fmtClock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(s / 60, s % 60)
}

private fun fmtSec(ms: Long): String = "%.1fs".format(ms / 1000f)

@Composable
fun EditorScreen(vm: EditorViewModel) {
    val context = LocalContext.current
    val clips = vm.clips
    val latestClips by rememberUpdatedState(clips)

    val exo = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(exo) { onDispose { exo.release() } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { exo.pause() }

    var isPlaying by remember { mutableStateOf(false) }
    var currentId by remember { mutableStateOf<Long?>(null) }
    var clipFraction by remember { mutableFloatStateOf(0f) }
    var timelineMs by remember { mutableLongStateOf(0L) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubMs by remember { mutableFloatStateOf(0f) }
    var activeTool by remember { mutableStateOf<Tool?>(null) }
    var showExport by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(30)
    ) { uris -> if (uris.isNotEmpty()) vm.addClips(uris) }
    val pickVideos = {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
    }

    // --- প্লেলিস্ট: শুধু ক্লিপ যোগ/ডিলিট/ট্রিম/স্প্লিট/মুভ হলে নতুন করে সাজানো হয় ---
    val structure = clips.map { Triple(it.id, it.startMs, it.endMs) }
    LaunchedEffect(structure) {
        val list = latestClips
        if (list.isEmpty()) {
            exo.clearMediaItems()
            return@LaunchedEffect
        }
        val keepId = (vm.selectedId ?: currentId)?.toString()
        val wasPlaying = exo.isPlaying
        val items = list.map { c ->
            MediaItem.Builder()
                .setUri(c.uri)
                .setMediaId(c.id.toString())
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(c.startMs)
                        .setEndPositionMs(c.endMs)
                        .build()
                )
                .build()
        }
        val idx = list.indexOfFirst { it.id.toString() == keepId }.coerceAtLeast(0)
        exo.setMediaItems(items, idx, 0L)
        exo.prepare()
        exo.playWhenReady = wasPlaying
    }

    // --- বর্তমান ক্লিপের স্পিড/ফিল্টার/মিউট প্রিভিউতে প্রয়োগ ---
    val current = clips.firstOrNull { it.id == currentId }
    LaunchedEffect(
        current?.id, current?.speed, current?.filter,
        current?.brightness, current?.text, current?.muted
    ) {
        val c = current ?: return@LaunchedEffect
        exo.playbackParameters = PlaybackParameters(c.speed)
        exo.volume = if (c.muted) 0f else 1f
        exo.setVideoEffects(ClipEffects.videoEffects(c, includeSpeed = false, targetHeight = null))
    }

    // --- প্লেহেড পোলিং ---
    LaunchedEffect(exo) {
        while (true) {
            val list = latestClips
            val i = exo.currentMediaItemIndex
            isPlaying = exo.isPlaying
            currentId = exo.currentMediaItem?.mediaId?.toLongOrNull()
            if (i in list.indices) {
                clipFraction = (exo.currentPosition.toFloat() / list[i].trimmedMs).coerceIn(0f, 1f)
                if (!scrubbing) {
                    val before = list.take(i).sumOf { it.outputMs }
                    timelineMs = before + (exo.currentPosition / list[i].speed).toLong()
                }
            }
            delay(80)
        }
    }

    fun seekTimeline(ms: Long) {
        val list = latestClips
        var acc = 0L
        for ((i, c) in list.withIndex()) {
            if (ms < acc + c.outputMs || i == list.lastIndex) {
                exo.seekTo(i, ((ms - acc) * c.speed).toLong().coerceIn(0L, c.trimmedMs))
                return
            }
            acc += c.outputMs
        }
    }

    fun togglePlay() {
        if (exo.isPlaying) {
            exo.pause()
        } else {
            if (exo.playbackState == Player.STATE_ENDED) exo.seekTo(0, 0L)
            exo.play()
        }
    }

    val selected = vm.selected
    val entries = buildList {
        add(ToolEntry("Add", Icons.Default.Add) { pickVideos() })
        add(ToolEntry("Split", Icons.Default.VerticalSplit) {
            val c = latestClips.getOrNull(exo.currentMediaItemIndex)
            val ok = c != null && vm.split(c.id, c.startMs + exo.currentPosition)
            if (!ok) {
                Toast.makeText(context, "Move the playhead inside a clip to split", Toast.LENGTH_SHORT).show()
            }
        })
        for (t in Tool.values()) {
            add(ToolEntry(t.label, t.icon, active = activeTool == t) {
                activeTool = if (activeTool == t) null else t
            })
        }
        if (selected != null) {
            add(
                ToolEntry(
                    if (selected.muted) "Muted" else "Mute",
                    if (selected.muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    active = selected.muted
                ) { vm.toggleMute(selected.id) }
            )
            add(ToolEntry("Copy", Icons.Default.ContentCopy) { vm.duplicate(selected.id) })
            add(ToolEntry("Delete", Icons.Default.Delete) { vm.delete(selected.id) })
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .imePadding()
    ) {
        TopBar(enabled = clips.isNotEmpty(), onExport = { exo.pause(); showExport = true })

        if (clips.isEmpty()) {
            EmptyState(onAdd = pickVideos)
        } else {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exo
                        useController = false
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black)
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { togglePlay() }) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Accent
                    )
                }
                Text(
                    fmtClock(if (scrubbing) scrubMs.toLong() else timelineMs),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = if (scrubbing) scrubMs else timelineMs.toFloat(),
                    onValueChange = {
                        scrubbing = true
                        scrubMs = it
                        seekTimeline(it.toLong())
                    },
                    onValueChangeFinished = { scrubbing = false },
                    valueRange = 0f..vm.totalMs.coerceAtLeast(1L).toFloat(),
                    colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )
                Text(
                    fmtClock(vm.totalMs),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Timeline(
                clips = clips,
                selectedId = vm.selectedId,
                currentId = currentId,
                fraction = clipFraction,
                thumbs = vm.thumbs,
                onSelect = { idx ->
                    vm.select(latestClips[idx].id)
                    exo.seekTo(idx, 0L)
                },
                onAdd = pickVideos
            )

            val tool = activeTool
            if (tool != null && selected != null) {
                Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    ToolPanel(tool = tool, clip = selected, vm = vm)
                }
            }

            LazyRow(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(entries) { e -> ToolButton(e) }
            }
        }
    }

    if (showExport) {
        ExportDialog(vm = vm, onDismiss = { showExport = false })
    }
}

@Composable
private fun TopBar(enabled: Boolean, onExport: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("BestCut", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Accent)
        Spacer(Modifier.weight(1f))
        Button(onClick = onExport, enabled = enabled, shape = RoundedCornerShape(20.dp)) {
            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Export")
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.EmptyState(onAdd: () -> Unit) {
    Column(
        Modifier
            .weight(1f)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.VideoLibrary,
            contentDescription = null,
            tint = Accent,
            modifier = Modifier.size(72.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text("Start a new project", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Add videos, trim, split, add filters and export.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAdd, shape = RoundedCornerShape(24.dp)) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Add videos")
        }
    }
}

@Composable
private fun Timeline(
    clips: List<Clip>,
    selectedId: Long?,
    currentId: Long?,
    fraction: Float,
    thumbs: Map<Long, Bitmap>,
    onSelect: (Int) -> Unit,
    onAdd: () -> Unit
) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        clips.forEachIndexed { index, c ->
            val w: Dp = (c.outputMs / 1000f * 40f).dp.coerceIn(80.dp, 360.dp)
            val label = buildString {
                append(fmtSec(c.outputMs))
                if (c.speed != 1f) append(" · ${c.speed}x")
                if (c.muted) append(" · mute")
            }
            Box(
                Modifier
                    .width(w)
                    .height(64.dp)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .then(if (c.id == selectedId) Modifier.border(2.dp, Accent, shape) else Modifier)
                    .clickable { onSelect(index) }
            ) {
                thumbs[c.id]?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alpha = 0.8f,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Text(
                    label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                )
                if (c.id == currentId) {
                    Box(
                        Modifier
                            .offset(x = w * fraction)
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(Color.White)
                    )
                }
            }
        }
        Box(
            Modifier
                .size(64.dp)
                .clip(shape)
                .background(Accent.copy(alpha = 0.15f))
                .clickable(onClick = onAdd),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add clip", tint = Accent)
        }
    }
}

@Composable
private fun ToolPanel(tool: Tool, clip: Clip, vm: EditorViewModel) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        when (tool) {
            Tool.TRIM -> {
                var range by remember(clip.id, clip.startMs, clip.endMs) {
                    mutableStateOf(clip.startMs.toFloat()..clip.endMs.toFloat())
                }
                val len = (range.endInclusive - range.start).toLong()
                Text(
                    "${fmtSec(range.start.toLong())} – ${fmtSec(range.endInclusive.toLong())}   (${fmtSec(len)})",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                RangeSlider(
                    value = range,
                    onValueChange = { range = it },
                    valueRange = 0f..clip.sourceDurationMs.toFloat(),
                    onValueChangeFinished = {
                        vm.trim(clip.id, range.start.toLong(), range.endInclusive.toLong())
                    }
                )
            }

            Tool.SPEED -> LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf(0.25f, 0.5f, 0.75f, 1f, 1.5f, 2f, 3f, 4f)) { s ->
                    FilterChip(
                        selected = clip.speed == s,
                        onClick = { vm.setSpeed(clip.id, s) },
                        label = { Text("${s}x") }
                    )
                }
            }

            Tool.FILTER -> LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(FilterType.values().toList()) { f ->
                    FilterChip(
                        selected = clip.filter == f,
                        onClick = { vm.setFilter(clip.id, f) },
                        label = { Text(f.label) }
                    )
                }
            }

            Tool.LIGHT -> {
                var b by remember(clip.id, clip.brightness) { mutableFloatStateOf(clip.brightness) }
                Text(
                    "Brightness  ${(b * 100).toInt()}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = b,
                    onValueChange = { b = it },
                    valueRange = -0.5f..0.5f,
                    onValueChangeFinished = { vm.setBrightness(clip.id, b) }
                )
            }

            Tool.TEXT -> {
                var t by remember(clip.id) { mutableStateOf(clip.text) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = t,
                        onValueChange = { t = it },
                        singleLine = true,
                        label = { Text("Caption") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { vm.setText(clip.id, t.trim()) }) { Text("Apply") }
                }
            }

            Tool.MOVE -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { vm.move(clip.id, -1) }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Earlier")
                }
                OutlinedButton(onClick = { vm.move(clip.id, 1) }) {
                    Text("Later")
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun ToolButton(e: ToolEntry) {
    val tint = if (e.active) Accent else MaterialTheme.colorScheme.onSurface
    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = e.onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(e.icon, contentDescription = e.label, tint = tint)
        Text(e.label, fontSize = 11.sp, color = tint)
    }
}

@Composable
private fun ExportDialog(vm: EditorViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val state = vm.export

    AlertDialog(
        onDismissRequest = { if (state !is ExportState.Running) onDismiss() },
        title = { Text("Export video") },
        text = {
            when (state) {
                ExportState.Idle -> Column {
                    Text("Resolution", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (h in listOf(480, 720, 1080)) {
                            FilterChip(
                                selected = vm.quality == h,
                                onClick = { vm.quality = h },
                                label = { Text("${h}p") }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Keep the app open while exporting.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is ExportState.Running -> Column {
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text("${(state.progress * 100).toInt()}%")
                }

                is ExportState.Done -> Text("Saved to Movies/BestCut")
                is ExportState.Failed -> Text(state.message)
            }
        },
        confirmButton = {
            when (state) {
                ExportState.Idle -> Button(onClick = { vm.startExport() }) { Text("Export") }
                is ExportState.Running -> Unit
                is ExportState.Done -> Button(onClick = {
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW)
                                .setDataAndType(state.uri, "video/mp4")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        )
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(context, "No video player found", Toast.LENGTH_SHORT).show()
                    }
                    vm.resetExport()
                    onDismiss()
                }) { Text("Open") }

                is ExportState.Failed -> TextButton(onClick = { vm.resetExport() }) { Text("Try again") }
            }
        },
        dismissButton = {
            when (state) {
                is ExportState.Running -> TextButton(onClick = { vm.cancelExport() }) { Text("Cancel") }
                else -> TextButton(onClick = { vm.resetExport(); onDismiss() }) { Text("Close") }
            }
        }
    )
}
