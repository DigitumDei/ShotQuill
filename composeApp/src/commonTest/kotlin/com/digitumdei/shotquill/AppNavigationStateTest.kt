package com.digitumdei.shotquill

import kotlin.test.Test
import kotlin.test.assertEquals

class AppNavigationStateTest {
    @Test
    fun restoresKnownScreenName() {
        assertEquals(AppScreen.Editor, appScreenFromSaveable("Editor"))
    }

    @Test
    fun fallsBackToHomeForUnknownScreenName() {
        assertEquals(AppScreen.Home, appScreenFromSaveable("DraftWorkspace"))
    }

    @Test
    fun backFromTheEditorLeadsHome() {
        assertEquals(AppScreen.Home, screenBehind(AppScreen.Editor, settingsOpenedFrom = AppScreen.Home))
    }

    @Test
    fun backFromSettingsReturnsToWhereItWasOpened() {
        assertEquals(AppScreen.Editor, screenBehind(AppScreen.Settings, settingsOpenedFrom = AppScreen.Editor))
        assertEquals(AppScreen.Home, screenBehind(AppScreen.Settings, settingsOpenedFrom = AppScreen.Home))
    }

    @Test
    fun homeHasNothingBehindIt() {
        assertEquals(AppScreen.Home, screenBehind(AppScreen.Home, settingsOpenedFrom = AppScreen.Editor))
    }
}
