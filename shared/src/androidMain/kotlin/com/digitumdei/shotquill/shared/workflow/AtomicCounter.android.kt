package com.digitumdei.shotquill.shared.workflow

import java.util.concurrent.atomic.AtomicInteger

internal actual class AtomicCounter actual constructor(initialValue: Int) {
    private val value = AtomicInteger(initialValue)

    actual fun getAndIncrement(): Int = value.getAndIncrement()
}
