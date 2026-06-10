package com.digitumdei.shotquill.clipboard

fun interface ClipboardWriter {
    fun copy(label: String, text: String)
}
