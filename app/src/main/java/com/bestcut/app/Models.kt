package com.bestcut.app

import android.net.Uri

enum class FilterType(val label: String) {
    NONE("Original"),
    BW("B&W"),
    WARM("Warm"),
    COOL("Cool"),
    VIVID("Vivid")
}

/** টাইমলাইনের একটি ক্লিপ। সব মান মিলিসেকেন্ডে। */
data class Clip(
    val id: Long,
    val uri: Uri,
    val sourceDurationMs: Long,
    val startMs: Long = 0L,
    val endMs: Long = sourceDurationMs,
    val speed: Float = 1f,
    val muted: Boolean = false,
    val filter: FilterType = FilterType.NONE,
    val brightness: Float = 0f,
    val text: String = ""
) {
    val trimmedMs: Long get() = endMs - startMs
    val outputMs: Long get() = (trimmedMs / speed).toLong().coerceAtLeast(1L)
}

sealed interface ExportState {
    data object Idle : ExportState
    data class Running(val progress: Float) : ExportState
    data class Done(val uri: Uri) : ExportState
    data class Failed(val message: String) : ExportState
}
