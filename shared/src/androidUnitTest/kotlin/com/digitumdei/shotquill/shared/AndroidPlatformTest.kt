package com.digitumdei.shotquill.shared

import kotlin.test.Test
import kotlin.test.assertTrue

class AndroidPlatformTest {
    @Test
    fun androidPlatformReportsAndroidName() {
        val name = currentPlatform().name
        assertTrue(
            actual = name.startsWith("Android "),
            message = "Expected Android platform name, was: $name",
        )
    }
}
