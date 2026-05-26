package com.digitumdei.shotquill.shared.domain

import com.digitumdei.shotquill.shared.settings.ActiveBrandProfileStore
import com.digitumdei.shotquill.shared.settings.InMemoryLocalSettingsRepository
import com.digitumdei.shotquill.shared.storage.InMemoryBrandProfileRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CaptionRequestFactoryTest {
    @Test
    fun includesActiveBrandProfileDataInCaptionRequest() {
        val settingsRepository = InMemoryLocalSettingsRepository()
        val profileRepository = InMemoryBrandProfileRepository()
        val store = ActiveBrandProfileStore(settingsRepository, profileRepository)
        store.saveActiveBrandProfile(sampleProfile())
        val factory = CaptionRequestFactory(store)

        val request = factory.createCaptionRequest(
            id = CaptionRequestId("caption-request-1"),
            draftId = PostDraftId("draft-1"),
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            photoDescription = "A lager can beside a sunny window.",
            createdAtEpochMillis = 1_700_000_000_000L,
        )

        assertEquals(BrandProfileId("brand-1"), request.brandProfileId)
        assertEquals("Friendly and crisp", request.tone)
        assertTrue(request.prompt.contains("Brand name: Copper Trail"))
        assertTrue(request.prompt.contains("Short description: Small-batch brewery"))
        assertTrue(request.prompt.contains("Default hashtags: #lager #craftbeer"))
        assertTrue(request.prompt.contains("Website/social links: https://copper.example"))
        assertTrue(request.prompt.contains("Visual style notes: Sunlit taproom photography."))
        assertTrue(request.prompt.contains("Product or beer naming notes: Keep seasonal beer names intact."))
    }

    @Test
    fun fallsBackWhenNoActiveBrandProfileExists() {
        val factory = CaptionRequestFactory(
            ActiveBrandProfileStore(
                InMemoryLocalSettingsRepository(),
                InMemoryBrandProfileRepository(),
            ),
        )

        val request = factory.createCaptionRequest(
            id = CaptionRequestId("caption-request-1"),
            draftId = PostDraftId("draft-1"),
            targetPlatform = TargetPlatform.BlueskyPost,
            photoDescription = "A behind-the-scenes bottling line.",
            createdAtEpochMillis = 1_700_000_000_000L,
        )

        assertNull(request.brandProfileId)
        assertNull(request.tone)
        assertTrue(request.prompt.contains("No active brand profile is configured"))
    }

    @Test
    fun altTextPromptCanLoadActiveBrandProfile() {
        val settingsRepository = InMemoryLocalSettingsRepository()
        val profileRepository = InMemoryBrandProfileRepository()
        val store = ActiveBrandProfileStore(settingsRepository, profileRepository)
        store.saveActiveBrandProfile(sampleProfile())
        val factory = CaptionRequestFactory(store)

        val prompt = factory.buildAltTextPrompt("A brewer pouring a sample.")

        assertTrue(prompt.contains("Brand name: Copper Trail"))
        assertTrue(prompt.contains("Default tone: Friendly and crisp"))
    }

    @Test
    fun omitsOptionalBrandFieldsWhenTheyAreMissing() {
        val settingsRepository = InMemoryLocalSettingsRepository()
        val profileRepository = InMemoryBrandProfileRepository()
        val store = ActiveBrandProfileStore(settingsRepository, profileRepository)
        store.saveActiveBrandProfile(
            sampleProfile().copy(
                audience = null,
                defaultHashtags = emptyList(),
                websiteOrSocialLinks = emptyList(),
                visualStyleNotes = null,
                productNamingNotes = null,
            ),
        )
        val factory = CaptionRequestFactory(store)

        val request = factory.createCaptionRequest(
            id = CaptionRequestId("caption-request-2"),
            draftId = PostDraftId("draft-1"),
            targetPlatform = TargetPlatform.InstagramStory,
            photoDescription = "A clean product shot.",
            createdAtEpochMillis = 1_700_000_000_000L,
        )
        val altTextPrompt = factory.buildAltTextPrompt("A clean product shot.")

        assertTrue(request.prompt.contains("Brand name: Copper Trail"))
        assertTrue(altTextPrompt.contains("Brand name: Copper Trail"))
        assertFalse(request.prompt.contains("Short description:"))
        assertFalse(request.prompt.contains("Default hashtags:"))
        assertFalse(request.prompt.contains("Website/social links:"))
        assertFalse(request.prompt.contains("Visual style notes:"))
        assertFalse(request.prompt.contains("Product or beer naming notes:"))
    }

    private fun sampleProfile(): BrandProfile =
        BrandProfile(
            id = BrandProfileId("brand-1"),
            displayName = "Copper Trail",
            voice = "Friendly and crisp",
            audience = "Small-batch brewery",
            defaultHashtags = listOf("#lager", "#craftbeer"),
            websiteOrSocialLinks = listOf("https://copper.example"),
            visualStyleNotes = "Sunlit taproom photography.",
            productNamingNotes = "Keep seasonal beer names intact.",
            imageAssets = emptyList(),
            createdAtEpochMillis = 1_700_000_000_000L,
            updatedAtEpochMillis = 1_700_000_000_000L,
        )
}
