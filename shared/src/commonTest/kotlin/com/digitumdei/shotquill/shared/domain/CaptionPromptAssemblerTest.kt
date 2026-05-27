package com.digitumdei.shotquill.shared.domain

import com.digitumdei.shotquill.shared.settings.ActiveBrandProfileStore
import com.digitumdei.shotquill.shared.settings.InMemoryLocalSettingsRepository
import com.digitumdei.shotquill.shared.storage.InMemoryBrandProfileRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertFalse

class CaptionPromptAssemblerTest {
    @Test
    fun assemblesFullCaptionPromptWithAllInputs() {
        val assembler = assemblerWithActiveBrandProfile()

        val prompt = assembler.assembleCaptionPrompt(
            visionDescription = "A lager can beside a sunny window.",
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            userNotes = "Keep it casual, promo for Friday",
            productName = "Copper Trail Lager",
            eventNote = "Summer launch event",
        )

        val expected = """
            Write a social caption for instagram_feed_square.
            
            Image description: A lager can beside a sunny window.
            
            User notes: Keep it casual, promo for Friday
            Product or subject: Copper Trail Lager
            Event or occasion: Summer launch event
            
            Active brand profile:
            Brand name: Copper Trail
            Short description: Small-batch brewery
            Default tone: Friendly and crisp
            Default hashtags: #lager #craftbeer
            Website/social links: https://copper.example
            Visual style notes: Sunlit taproom photography.
            Product or beer naming notes: Keep seasonal beer names intact.
            
            Return:
            - A main caption suitable for instagram_feed_square
            - A shorter caption variant
            - 2 to 4 minimal, relevant hashtags
        """.trimIndent().trim()
        assertEquals(expected, prompt)
    }

    @Test
    fun assemblesAltTextPromptPlainAndSeparate() {
        val assembler = assemblerWithActiveBrandProfile()

        val prompt = assembler.assembleAltTextPrompt("A lager can beside a sunny window.")

        val expected = """
            Write accessible alt text for this image.
            Describe the image plainly and factually. Do not use marketing language or hashtags.
            
            Image description: A lager can beside a sunny window.
            
            Image context: Copper Trail
        """.trimIndent().trim()
        assertEquals(expected, prompt)
    }

    @Test
    fun fallsBackWhenNoActiveBrandProfileExists() {
        val assembler = CaptionPromptAssembler(
            ActiveBrandProfileStore(
                InMemoryLocalSettingsRepository(),
                InMemoryBrandProfileRepository(),
            ),
        )

        val prompt = assembler.assembleCaptionPrompt(
            visionDescription = "A behind-the-scenes bottling line.",
            targetPlatform = TargetPlatform.BlueskyPost,
        )

        assertContains(prompt, "No active brand profile is configured; use a clear neutral voice.")
        assertFalse(prompt.contains("Active brand profile:"))
    }

    @Test
    fun includesPlatformNameInCaptionPrompt() {
        val assembler = CaptionPromptAssembler(
            ActiveBrandProfileStore(
                InMemoryLocalSettingsRepository(),
                InMemoryBrandProfileRepository(),
            ),
        )

        for (platform in TargetPlatform.entries) {
            val prompt = assembler.assembleCaptionPrompt(
                visionDescription = "A product shot.",
                targetPlatform = platform,
            )
            assertContains(prompt, platform.wireValue)
        }
    }

    @Test
    fun omitsOptionalFieldsWhenBlank() {
        val assembler = assemblerWithActiveBrandProfile()

        val prompt = assembler.assembleCaptionPrompt(
            visionDescription = "A clean product shot.",
            targetPlatform = TargetPlatform.InstagramPortrait,
            userNotes = "   ",
            productName = "",
            eventNote = null,
        )

        assertFalse(prompt.contains("User notes:"))
        assertFalse(prompt.contains("Product or subject:"))
        assertFalse(prompt.contains("Event or occasion:"))
    }

    @Test
    fun includesUserNotesWhenPresent() {
        val assembler = assemblerWithActiveBrandProfile()

        val prompt = assembler.assembleCaptionPrompt(
            visionDescription = "A clean product shot.",
            targetPlatform = TargetPlatform.FacebookPost,
            userNotes = "Focus on the craft angle",
        )

        assertContains(prompt, "User notes: Focus on the craft angle")
    }

    @Test
    fun includesProductNameWhenPresent() {
        val assembler = assemblerWithActiveBrandProfile()

        val prompt = assembler.assembleCaptionPrompt(
            visionDescription = "A clean product shot.",
            targetPlatform = TargetPlatform.InstagramStoryReel,
            productName = "Trailblazer IPA",
        )

        assertContains(prompt, "Product or subject: Trailblazer IPA")
    }

    @Test
    fun includesEventNoteWhenPresent() {
        val assembler = assemblerWithActiveBrandProfile()

        val prompt = assembler.assembleCaptionPrompt(
            visionDescription = "Crowd enjoying fresh pours.",
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            eventNote = "Grand reopening night",
        )

        assertContains(prompt, "Event or occasion: Grand reopening night")
    }

    @Test
    fun captionPromptRequestsCaptionShortCaptionAndHashtags() {
        val assembler = assemblerWithActiveBrandProfile()

        val prompt = assembler.assembleCaptionPrompt(
            visionDescription = "A taproom shot.",
            targetPlatform = TargetPlatform.BlueskyPost,
        )

        assertContains(prompt, "A main caption")
        assertContains(prompt, "A shorter caption variant")
        assertContains(prompt, "2 to 4 minimal, relevant hashtags")
    }

    @Test
    fun altTextPromptDoesNotIncludeMarketingInstructions() {
        val assembler = assemblerWithActiveBrandProfile()

        val prompt = assembler.assembleAltTextPrompt("A lager can beside a sunny window.")

        assertContains(prompt, "Describe the image plainly and factually")
        assertContains(prompt, "Do not use marketing language or hashtags")
        assertFalse(prompt.contains("caption"))
        assertFalse(prompt.contains("hashtags"))
        assertFalse(prompt.contains("Default tone:"))
        assertFalse(prompt.contains("Default hashtags:"))
        assertFalse(prompt.contains("Website/social links:"))
    }

    @Test
    fun altTextPromptOmitsBrandContextWhenNoProfile() {
        val assembler = CaptionPromptAssembler(
            ActiveBrandProfileStore(
                InMemoryLocalSettingsRepository(),
                InMemoryBrandProfileRepository(),
            ),
        )

        val prompt = assembler.assembleAltTextPrompt("A taproom interior.")

        assertContains(prompt, "Write accessible alt text")
        assertFalse(prompt.contains("Image context:"))
    }

    @Test
    fun deterministicOutputsAreIdenticalAcrossRepeatedCalls() {
        val assembler = assemblerWithActiveBrandProfile()

        val first = assembler.assembleCaptionPrompt(
            visionDescription = "A lager can beside a sunny window.",
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            userNotes = "Keep it casual",
        )
        val second = assembler.assembleCaptionPrompt(
            visionDescription = "A lager can beside a sunny window.",
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            userNotes = "Keep it casual",
        )

        assertEquals(first, second)
    }

    @Test
    fun trimsBlankOptionalInputs() {
        val assembler = assemblerWithActiveBrandProfile()

        val prompt = assembler.assembleCaptionPrompt(
            visionDescription = "  A bright taproom  ",
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            productName = "  Hoppy Day IPA  ",
        )

        assertContains(prompt, "Image description: A bright taproom")
        assertContains(prompt, "Product or subject: Hoppy Day IPA")
    }

    private fun assemblerWithActiveBrandProfile(): CaptionPromptAssembler {
        val settingsRepository = InMemoryLocalSettingsRepository()
        val profileRepository = InMemoryBrandProfileRepository()
        val store = ActiveBrandProfileStore(settingsRepository, profileRepository)
        store.saveActiveBrandProfile(sampleProfile())
        return CaptionPromptAssembler(store)
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
