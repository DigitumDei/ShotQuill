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

    @Test
    fun deliberatelyFailingTest_redPathProbe() {
        // Throwaway test on ci/red-path-check branch to prove CI fails on red.
        // Must NOT be merged. PR will be closed and branch deleted after CI goes red.
        assertTrue(false, "Intentional failure to confirm CI red path")
    }
}
