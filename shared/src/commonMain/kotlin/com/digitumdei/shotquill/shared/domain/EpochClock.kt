package com.digitumdei.shotquill.shared.domain

import kotlinx.datetime.Clock

object EpochClock {
    fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
}
