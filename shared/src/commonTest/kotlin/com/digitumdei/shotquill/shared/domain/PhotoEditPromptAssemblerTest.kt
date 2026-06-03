package com.digitumdei.shotquill.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertFalse

class PhotoEditPromptAssemblerTest {
    private val createdAt = 1_700_000_000_000L
    private val draftId = PostDraftId("draft-1")
    private val mediaAssetId = MediaAssetId("media-1")
    private val photoEditRequestId = PhotoEditRequestId("photo-edit-request-1")

    @Test
    fun assemblesFullyPopulatedRequestWithExactOutput() {
        val request = samplePhotoEditRequest(
            prompt = "Make the image brighter while keeping it realistic.",
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            realismLevel = RealismLevel.Photoreal,
            qualityTier = QualityTier.High,
            subjectDescription = "A coffee cup on a wooden table",
            userRefinement = "Focus on the coffee cup",
        )

        val prompt = PhotoEditPromptAssembler.assemble(request)

        val expected = buildString {
            append("Edit the photo as follows: \"Make the image brighter while keeping it realistic.\"")
            append(". The target platform is Instagram Feed Square")
            append(" (1:1, 1080x1080px, fit framing)")
            append(".")
            append(" Improve the lighting and exposure of the image.")
            append(" Apply a photorealistic edit. Preserve natural camera realism and avoid visibly generated or illustrated details.")
            append(" Use high quality tier.")
            append(" The subject is A coffee cup on a wooden table.")
            append(" Preserve the subject's appearance.")
            append(" Focus on the coffee cup.")
        }
        assertEquals(expected, prompt)
    }

    @Test
    fun deterministicOutputsAreIdenticalAcrossRepeatedCalls() {
        val request = samplePhotoEditRequest()

        val first = PhotoEditPromptAssembler.assemble(request)
        val second = PhotoEditPromptAssembler.assemble(request)

        assertEquals(first, second)
    }

    @Test
    fun outputIsNaturalProseWithoutLabelledKeyValueSections() {
        val request = samplePhotoEditRequest()

        val prompt = PhotoEditPromptAssembler.assemble(request)

        assertFalse(prompt.contains("subjectDescription:"))
        assertFalse(prompt.contains("userRefinement:"))
        assertFalse(prompt.contains("realismLevel:"))
        assertFalse(prompt.contains("qualityTier:"))
        assertFalse(prompt.contains("targetPlatform:"))
        assertFalse(prompt.contains(" realism:"))
        assertFalse(prompt.contains("Additional user notes:"))
        assertFalse(prompt.contains(" intent:"))
        assertFalse(prompt.contains("EditIntent."))
    }

    @Test
    fun includesUserRefinementAsNaturalProse() {
        val request = samplePhotoEditRequest(
            userRefinement = "Keep the background soft",
        )

        val prompt = PhotoEditPromptAssembler.assemble(request)

        assertContains(prompt, "Keep the background soft.")
    }

    @Test
    fun omitsUserRefinementWhenNull() {
        val request = samplePhotoEditRequest(userRefinement = null)

        val prompt = PhotoEditPromptAssembler.assemble(request)

        assertFalse(prompt.contains("Focus on the coffee cup"))
    }

    @Test
    fun includesSubjectDescriptionWithPreservationInstruction() {
        val request = samplePhotoEditRequest(
            subjectDescription = "A red bicycle leaning against a wall",
        )

        val prompt = PhotoEditPromptAssembler.assemble(request)

        assertContains(prompt, "The subject is A red bicycle leaning against a wall.")
        assertContains(prompt, "Preserve the subject's appearance.")
    }

    @Test
    fun omitsSubjectDescriptionWhenNull() {
        val request = samplePhotoEditRequest(subjectDescription = null)

        val prompt = PhotoEditPromptAssembler.assemble(request)

        assertFalse(prompt.contains("The subject is"))
        assertFalse(prompt.contains("Preserve the subject"))
    }

    @Test
    fun includesAspectRatioAndDimensionsAndFramingForSizedPreset() {
        val request = samplePhotoEditRequest(targetPlatform = TargetPlatform.InstagramPortrait)

        val prompt = PhotoEditPromptAssembler.assemble(request)

        assertContains(prompt, "Instagram Portrait")
        assertContains(prompt, "4:5")
        assertContains(prompt, "1080x1350px")
        assertContains(prompt, "fit framing")
    }

    @Test
    fun includesOnlyFramingWithoutDimensionsForOriginalPreset() {
        val request = samplePhotoEditRequest(targetPlatform = TargetPlatform.Original)

        val prompt = PhotoEditPromptAssembler.assemble(request)

        assertContains(prompt, "Original (no resize)")
        assertContains(prompt, "no_resize framing")
        assertFalse(prompt.contains("px"))
    }

    @Test
    fun includesPlatformAndFramingForEveryTargetPlatform() {
        for (platform in TargetPlatform.entries) {
            val request = samplePhotoEditRequest(targetPlatform = platform)

            val prompt = PhotoEditPromptAssembler.assemble(request)

            assertContains(prompt, platform.platformPreset.displayName)
            assertContains(prompt, platform.platformPreset.defaultFramingBehavior.wireValue + " framing")
        }
    }

    @Test
    fun includesRealismLevelIntentInPrompt() {
        for (realism in RealismLevel.entries) {
            val request = samplePhotoEditRequest(realismLevel = realism)

            val prompt = PhotoEditPromptAssembler.assemble(request)

            assertContains(prompt, "Apply a ${realism.adjective} edit.")
            assertContains(prompt, realism.promptIntent)
        }
    }

    @Test
    fun includesQualityTierInPrompt() {
        for (tier in QualityTier.entries) {
            val request = samplePhotoEditRequest(qualityTier = tier)

            val prompt = PhotoEditPromptAssembler.assemble(request)

            assertContains(prompt, "Use ${tier.wireValue} quality tier.")
        }
    }

    @Test
    fun everyEditIntentProducesDistinctNaturalLanguageInstruction() {
        val expectations = mapOf(
            EditIntent.ImproveLighting to "Improve the lighting and exposure of the image",
            EditIntent.AddLogoOverlay to "Overlay a logo or watermark onto the image",
            EditIntent.RemoveObject to "Remove the specified object or element from the image",
            EditIntent.CropOrExtend to "Crop or extend the image to the target dimensions",
            EditIntent.BackgroundAdjustment to "Adjust or replace the background of the image",
            EditIntent.SubtleRetouch to "Apply subtle retouching to enhance the image while keeping it natural",
            EditIntent.StyleTransfer to "Apply a specific artistic style to the image",
            EditIntent.Custom to "Follow the user's custom editing instructions",
        )

        assertEquals(expectations.keys, EditIntent.entries.toSet(), "expectations must cover every EditIntent")

        for (intent in EditIntent.entries) {
            val expectedSnippet = expectations.getValue(intent)
            val request = samplePhotoEditRequest(intent = intent)
            val prompt = PhotoEditPromptAssembler.assemble(request)
            assertContains(prompt, expectedSnippet, "EditIntent.${intent.name} should produce expected instruction")
        }
    }

    @Test
    fun trimsPromptWhitespace() {
        val request = samplePhotoEditRequest(prompt = "  Make it brighter  ")

        val prompt = PhotoEditPromptAssembler.assemble(request)

        assertContains(prompt, """Edit the photo as follows: "Make it brighter".""")
    }

    @Test
    fun trimsSubjectDescriptionWhitespace() {
        val request = samplePhotoEditRequest(subjectDescription = "  A cat on a sofa  ")

        val prompt = PhotoEditPromptAssembler.assemble(request)

        assertContains(prompt, "The subject is A cat on a sofa.")
    }

    @Test
    fun trimsUserRefinementWhitespace() {
        val request = samplePhotoEditRequest(userRefinement = "  Crop tighter  ")

        val prompt = PhotoEditPromptAssembler.assemble(request)

        assertContains(prompt, "Crop tighter.")
    }

    private fun samplePhotoEditRequest(
        prompt: String = "Make the image brighter.",
        targetPlatform: TargetPlatform = TargetPlatform.InstagramFeedSquare,
        intent: EditIntent = EditIntent.ImproveLighting,
        realismLevel: RealismLevel = RealismLevel.Photoreal,
        qualityTier: QualityTier = QualityTier.Standard,
        subjectDescription: String? = null,
        userRefinement: String? = null,
    ): PhotoEditRequest = PhotoEditRequest(
        id = photoEditRequestId,
        draftId = draftId,
        sourceMediaAssetId = mediaAssetId,
        intent = intent,
        realismLevel = realismLevel,
        qualityTier = qualityTier,
        prompt = prompt,
        userRefinement = userRefinement,
        subjectDescription = subjectDescription,
        targetPlatform = targetPlatform,
        maskRegion = null,
        createdAtEpochMillis = createdAt,
    )
}