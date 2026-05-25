package com.digitumdei.shotquill

import com.digitumdei.shotquill.shared.Greeting
import org.junit.Test
import kotlin.test.assertTrue

class AppModuleSanityTest {
    @Test
    fun appModuleCanReachSharedGreeting() {
        val greeting = Greeting().greet()
        assertTrue(greeting.isNotBlank(), "Shared greeting should not be blank")
    }
}
