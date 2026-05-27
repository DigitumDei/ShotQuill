package com.digitumdei.shotquill

import kotlin.test.Test
import kotlin.test.assertEquals

class AppNavigationStateTest {
    @Test
    fun restoresKnownScreenName() {
        assertEquals(AppScreen.DraftWorkspace, appScreenFromSaveable("DraftWorkspace"))
    }

    @Test
    fun fallsBackToNewPostForUnknownScreenName() {
        assertEquals(AppScreen.NewPost, appScreenFromSaveable("RenamedScreen"))
    }
}
