package com.digitumdei.shotquill.model

import androidx.compose.runtime.saveable.Saver

data class MediaCaptureResult(
    val uri: String,
    val mimeType: String?,
    val widthPx: Int?,
    val heightPx: Int?,
    val createdAtEpochMillis: Long,
)

val MediaCaptureResultSaver = Saver<MediaCaptureResult?, String>(
    save = { result ->
        result?.let {
            "${it.uri}|${it.mimeType ?: ""}|${it.widthPx ?: -1}|${it.heightPx ?: -1}|${it.createdAtEpochMillis}"
        } ?: ""
    },
    restore = { value ->
        if (value.isNotEmpty()) {
            val parts = value.split("|", limit = 5)
            MediaCaptureResult(
                uri = parts[0],
                mimeType = parts[1].ifEmpty { null },
                widthPx = parts[2].toIntOrNull()?.takeIf { it >= 0 },
                heightPx = parts[3].toIntOrNull()?.takeIf { it >= 0 },
                createdAtEpochMillis = parts[4].toLong(),
            )
        } else null
    },
)
