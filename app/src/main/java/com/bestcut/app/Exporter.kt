@file:OptIn(UnstableApi::class)

package com.bestcut.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.media3.common.AudioProcessor
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** সব ক্লিপ জোড়া দিয়ে একটি MP4 বানায় এবং Movies/BestCut ফোল্ডারে সেভ করে। */
class Exporter(private val context: Context) {

    suspend fun export(clips: List<Clip>, height: Int, onProgress: (Float) -> Unit): Uri {
        val tmp = File(context.cacheDir, "bestcut_${System.currentTimeMillis()}.mp4")
        try {
            // Transformer অবশ্যই মেইন থ্রেড (Looper) থেকে চালাতে হয়
            withContext(Dispatchers.Main) { transform(clips, height, tmp, onProgress) }
            return withContext(Dispatchers.IO) { saveToMovies(tmp) }
        } finally {
            tmp.delete()
        }
    }

    private suspend fun transform(
        clips: List<Clip>,
        height: Int,
        out: File,
        onProgress: (Float) -> Unit
    ) = coroutineScope {
        val sequence = EditedMediaItemSequence(clips.map { editedItem(it, height) })
        val composition = Composition.Builder(sequence).build()

        val done = CompletableDeferred<Unit>()
        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    done.complete(Unit)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    done.completeExceptionally(exportException)
                }
            })
            .build()

        transformer.start(composition, out.absolutePath)

        val holder = ProgressHolder()
        val poller = launch {
            while (isActive) {
                if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onProgress(holder.progress / 100f)
                }
                delay(200)
            }
        }
        try {
            done.await()
        } catch (e: CancellationException) {
            transformer.cancel()
            throw e
        } finally {
            poller.cancel()
        }
    }

    private fun editedItem(c: Clip, height: Int): EditedMediaItem {
        val mediaItem = MediaItem.Builder()
            .setUri(c.uri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(c.startMs)
                    .setEndPositionMs(c.endMs)
                    .build()
            )
            .build()

        val audio = mutableListOf<AudioProcessor>()
        if (c.speed != 1f) {
            audio.add(SonicAudioProcessor().apply { setSpeed(c.speed) })
        }
        val video = ClipEffects.videoEffects(c, includeSpeed = true, targetHeight = height)

        return EditedMediaItem.Builder(mediaItem)
            .setRemoveAudio(c.muted)
            .setEffects(Effects(audio, video))
            .build()
    }

    private fun saveToMovies(file: File): Uri {
        val resolver = context.contentResolver
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "BestCut_$stamp.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/BestCut")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create output file")
        resolver.openOutputStream(uri)!!.use { os ->
            file.inputStream().use { it.copyTo(os) }
        }
        if (Build.VERSION.SDK_INT >= 29) {
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        return uri
    }
}
