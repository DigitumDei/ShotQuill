package com.digitumdei.shotquill.shared.domain

import kotlinx.datetime.Clock

interface EpochClock {
    fun nowMillis(): Long

    companion object Default : EpochClock {
        override fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
    }
}
