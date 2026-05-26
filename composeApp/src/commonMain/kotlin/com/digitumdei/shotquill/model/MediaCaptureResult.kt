package com.digitumdei.shotquill.model

data class MediaCaptureResult(
    val uri: String,
    val mimeType: String?,
    val widthPx: Int?,
    val heightPx: Int?,
    val createdAtEpochMillis: Long,
)
