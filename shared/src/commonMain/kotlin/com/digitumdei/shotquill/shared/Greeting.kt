package com.digitumdei.shotquill.shared

class Greeting {
    private val platform: Platform = currentPlatform()

    fun greet(): String = "ShotQuill on ${platform.name}"
}
