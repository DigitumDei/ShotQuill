package com.digitumdei.shotquill.shared.workflow

internal expect class AtomicCounter(initialValue: Int) {
    fun getAndIncrement(): Int
}
