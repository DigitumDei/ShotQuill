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

    @Test
    fun reportsTheMissingFinalComposerDependency() {
        assertEquals(
            "Draft repository not available",
            finalComposerUnavailableMessage(
                hasManualWorkflowRepository = false,
                hasDraftId = true,
                hasClipboardWriter = true,
                hasPostShareLauncher = true,
            ),
        )
        assertEquals(
            "Draft ID not available",
            finalComposerUnavailableMessage(
                hasManualWorkflowRepository = true,
                hasDraftId = false,
                hasClipboardWriter = true,
                hasPostShareLauncher = true,
            ),
        )
        assertEquals(
            "Clipboard writer not available",
            finalComposerUnavailableMessage(
                hasManualWorkflowRepository = true,
                hasDraftId = true,
                hasClipboardWriter = false,
                hasPostShareLauncher = true,
            ),
        )
        assertEquals(
            "Share launcher not available",
            finalComposerUnavailableMessage(
                hasManualWorkflowRepository = true,
                hasDraftId = true,
                hasClipboardWriter = true,
                hasPostShareLauncher = false,
            ),
        )
    }
}
