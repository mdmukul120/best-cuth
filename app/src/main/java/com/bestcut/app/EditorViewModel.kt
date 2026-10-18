@file:OptIn(UnstableApi::class)

package com.bestcut.app

import android.app.Application
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditorViewModel(private val app: Application) : AndroidViewModel(app) {

    var clips by mutableStateOf<List<Clip>>(emptyList())
        private set

    var selectedId by mutableStateOf<Long?>(null)
        private set

    var export by mutableStateOf<ExportState>(ExportState.Idle)
        private set

    var quality by mutableIntStateOf(720)

    val thumbs = mutableStateMapOf<Long, Bitmap>()

    private var nextId = 1L
    private var exportJob: Job? = null

    val selected: Clip? get() = clips.firstOrNull { it.id == selectedId }
    val totalMs: Long get() = clips.sumOf { it.outputMs }

    // ---------- ক্লিপ যোগ ----------

    fun addClips(uris: List<Uri>) {
        viewModelScope.launch {
            for (uri in uris) {
                val meta = withContext(Dispatchers.IO) { readMeta(uri) } ?: continue
                val clip = Clip(id = nextId++, uri = uri, sourceDurationMs = meta.first)
                clips = clips + clip
                meta.second?.let { thumbs[clip.id] = it }
                if (selectedId == null) selectedId = clip.id
            }
        }
    }

    private fun readMeta(uri: Uri): Pair<Long, Bitmap?>? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(app, uri)
            val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            if (d == null || d <= 0L) {
                null
            } else {
                val frame = r.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                val thumb = frame?.let {
                    val h = 160
                    val w = (it.width * h / it.height.toFloat()).toInt().coerceAtLeast(1)
                    Bitmap.createScaledBitmap(it, w, h, true)
                }
                d to thumb
            }
        } catch (e: Exception) {
            null
        } finally {
            r.release()
        }
    }

    // ---------- সিলেকশন ও এডিট ----------

    fun select(id: Long?) {
        selectedId = id
    }

    private fun update(id: Long, f: (Clip) -> Clip) {
        clips = clips.map { if (it.id == id) f(it) else it }
    }

    fun trim(id: Long, start: Long, end: Long) = update(id) { c ->
        var s = start.coerceIn(0L, c.sourceDurationMs)
        var e = end.coerceIn(0L, c.sourceDurationMs)
        if (e - s < MIN_MS) {
            e = (s + MIN_MS).coerceAtMost(c.sourceDurationMs)
            s = (e - MIN_MS).coerceAtLeast(0L)
        }
        c.copy(startMs = s, endMs = e)
    }

    fun setSpeed(id: Long, speed: Float) = update(id) { it.copy(speed = speed) }
    fun setFilter(id: Long, filter: FilterType) = update(id) { it.copy(filter = filter) }
    fun setBrightness(id: Long, value: Float) = update(id) { it.copy(brightness = value) }
    fun setText(id: Long, text: String) = update(id) { it.copy(text = text) }
    fun toggleMute(id: Long) = update(id) { it.copy(muted = !it.muted) }

    /** [sourcePos] হলো মূল ভিডিওর ভেতরের সময় (ms)। সফল হলে true। */
    fun split(id: Long, sourcePos: Long): Boolean {
        val i = clips.indexOfFirst { it.id == id }
        if (i < 0) return false
        val c = clips[i]
        if (sourcePos - c.startMs < MIN_MS || c.endMs - sourcePos < MIN_MS) return false
        val first = c.copy(endMs = sourcePos)
        val second = c.copy(id = nextId++, startMs = sourcePos, text = "")
        clips = clips.toMutableList().apply {
            set(i, first)
            add(i + 1, second)
        }
        thumbs[c.id]?.let { thumbs[second.id] = it }
        selectedId = first.id
        return true
    }

    fun duplicate(id: Long) {
        val i = clips.indexOfFirst { it.id == id }
        if (i < 0) return
        val copy = clips[i].copy(id = nextId++)
        clips = clips.toMutableList().apply { add(i + 1, copy) }
        thumbs[id]?.let { thumbs[copy.id] = it }
        selectedId = copy.id
    }

    fun delete(id: Long) {
        val i = clips.indexOfFirst { it.id == id }
        if (i < 0) return
        val remaining = clips.filterNot { it.id == id }
        clips = remaining
        thumbs.remove(id)
        selectedId = remaining.getOrNull(minOf(i, remaining.lastIndex))?.id
    }

    fun move(id: Long, delta: Int) {
        val i = clips.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in clips.indices) return
        clips = clips.toMutableList().apply {
            val tmp = this[i]
            this[i] = this[j]
            this[j] = tmp
        }
    }

    // ---------- এক্সপোর্ট ----------

    fun startExport() {
        if (clips.isEmpty() || export is ExportState.Running) return
        val snapshot = clips
        val height = quality
        exportJob = viewModelScope.launch {
            export = ExportState.Running(0f)
            try {
                val uri = Exporter(app).export(snapshot, height) { p ->
                    export = ExportState.Running(p.coerceIn(0f, 1f))
                }
                export = ExportState.Done(uri)
            } catch (e: CancellationException) {
                export = ExportState.Idle
                throw e
            } catch (e: Throwable) {
                export = ExportState.Failed(e.message ?: "Export failed")
            }
        }
    }

    fun cancelExport() {
        exportJob?.cancel()
    }

    fun resetExport() {
        if (export !is ExportState.Running) export = ExportState.Idle
    }

    private companion object {
        const val MIN_MS = 300L
    }
}
