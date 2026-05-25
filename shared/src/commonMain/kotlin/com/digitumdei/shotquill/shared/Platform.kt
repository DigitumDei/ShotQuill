package com.digitumdei.shotquill.shared

interface Platform {
    val name: String
}

expect fun currentPlatform(): Platform
