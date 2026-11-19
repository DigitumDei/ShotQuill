package com.digitumdei.shotquill.shared.settings

import com.digitumdei.shotquill.shared.domain.CropShape
import com.digitumdei.shotquill.shared.domain.LookSettings
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
            defaultCropShape = CropShape.Square,
            look = LookSettings(warmth = 0.5f, contrast = 0f, saturation = 0f, fade = 0f),
        )

        repository.saveSettings(settings)

        assertEquals(settings, repository.readSettings())
    }

    @Test
    fun detectsWhetherAnAiApiKeyIsConfigured() {
        val repository = InMemoryLocalSettingsRepository()

        assertFalse(repository.hasAiApiKey())

        repository.saveAiApiKey("  sk-test_1234567890  ")

        assertTrue(repository.hasAiApiKey())
        assertEquals("sk-test_1234567890", repository.getAiApiKey())
    }

    @Test
    fun clearingTheAiApiKeyRemovesConfiguredState() {
        val repository = InMemoryLocalSettingsRepository()
        repository.saveAiApiKey("sk-test_1234567890")

        repository.clearAiApiKey()

        assertFalse(repository.hasAiApiKey())
        assertNull(repository.getAiApiKey())
    }
}
