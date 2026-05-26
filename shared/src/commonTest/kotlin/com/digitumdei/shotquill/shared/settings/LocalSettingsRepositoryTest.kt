package com.digitumdei.shotquill.shared.settings

import com.digitumdei.shotquill.shared.domain.BrandProfileId
import com.digitumdei.shotquill.shared.domain.QualityTier
import com.digitumdei.shotquill.shared.domain.RealismLevel
import com.digitumdei.shotquill.shared.domain.TargetPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalSettingsRepositoryTest {
    @Test
    fun readsAndWritesNonSecretSettings() {
        val repository = InMemoryLocalSettingsRepository()
        val settings = LocalAppSettings(
            defaultTargetPlatform = TargetPlatform.BlueskyPost,
            defaultQualityTier = QualityTier.High,
            defaultRealismLevel = RealismLevel.Polished,
            activeBrandProfileId = BrandProfileId("brand-1"),
            promptHistoryEnabled = false,
        )

        repository.saveSettings(settings)

        assertEquals(settings, repository.readSettings())
    }

    @Test
    fun detectsWhetherOpenAiApiKeyIsConfigured() {
        val repository = InMemoryLocalSettingsRepository()

        assertFalse(repository.hasOpenAiApiKey())

        repository.saveOpenAiApiKey("  sk-test_1234567890  ")

        assertTrue(repository.hasOpenAiApiKey())
        assertEquals("sk-test_1234567890", repository.getOpenAiApiKey())
    }

    @Test
    fun clearingOpenAiApiKeyRemovesConfiguredState() {
        val repository = InMemoryLocalSettingsRepository()
        repository.saveOpenAiApiKey("sk-test_1234567890")

        repository.clearOpenAiApiKey()

        assertFalse(repository.hasOpenAiApiKey())
        assertNull(repository.getOpenAiApiKey())
    }
}
