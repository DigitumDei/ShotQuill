package com.digitumdei.shotquill.shared

import kotlin.test.Test
import kotlin.test.assertTrue

class GreetingTest {
    @Test
    fun greetingMentionsShotQuill() {
        val greeting = Greeting().greet()
        assertTrue(
            actual = greeting.startsWith("ShotQuill on "),
            message = "Expected greeting to start with 'ShotQuill on ', was: $greeting",
        )
    }
}
