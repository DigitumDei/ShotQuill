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

    private fun assembleFromRequest(request: PhotoEditRequest): String = PhotoEditPromptAssembler.buildPrompt(
        intent = request.intent,
        realismLevel = request.realismLevel,
        qualityTier = request.qualityTier,
        targetPlatform = request.targetPlatform,
        maskRegion = request.maskRegion,
        subjectDescription = request.subjectDescription,
        userRefinement = request.userRefinement,
    )

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

        val prompt = assembleFromRequest(request)

        val expected = buildString {
            append("Edit this image: Improve the lighting and exposure of the image.")
            append(" Apply a photorealistic edit. Preserve natural camera realism and avoid visibly generated or illustrated details.")
            append(" Use high quality tier.")
            append(" Frame the result for Instagram Feed Square at 1:1, 1080x1080px, and fit the content to the frame.")
            append(" The subject is A coffee cup on a wooden table.")
            append(" Preserve the subject's appearance.")
            append(" Focus on the coffee cup.")
        }
        assertEquals(expected, prompt)
    }

    @Test
    fun deterministicOutputsAreIdenticalAcrossRepeatedCalls() {
        val request = samplePhotoEditRequest()

        val first = assembleFromRequest(request)
        val second = assembleFromRequest(request)

        assertEquals(first, second)
    }

    @Test
    fun outputIsNaturalProseWithoutLabelledKeyValueSections() {
        val request = samplePhotoEditRequest()

        val prompt = assembleFromRequest(request)

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

        val prompt = assembleFromRequest(request)

        assertContains(prompt, "Keep the background soft.")
    }

    @Test
    fun omitsUserRefinementWhenNull() {
        val request = samplePhotoEditRequest(userRefinement = null)

        val prompt = assembleFromRequest(request)

        assertFalse(prompt.contains("Focus on the coffee cup"))
    }

    @Test
    fun includesSubjectDescriptionWithPreservationInstruction() {
        val request = samplePhotoEditRequest(
            subjectDescription = "A red bicycle leaning against a wall",
        )

        val prompt = assembleFromRequest(request)

        assertContains(prompt, "The subject is A red bicycle leaning against a wall.")
        assertContains(prompt, "Preserve the subject's appearance.")
    }

    @Test
    fun omitsSubjectDescriptionWhenNull() {
        val request = samplePhotoEditRequest(subjectDescription = null)

        val prompt = assembleFromRequest(request)

        assertFalse(prompt.contains("The subject is"))
        assertFalse(prompt.contains("Preserve the subject"))
    }

    @Test
    fun includesAspectRatioAndDimensionsAndFramingForSizedPreset() {
        val request = samplePhotoEditRequest(targetPlatform = TargetPlatform.InstagramPortrait)

        val prompt = assembleFromRequest(request)

        assertContains(prompt, "Instagram Portrait")
        assertContains(prompt, "4:5")
        assertContains(prompt, "1080x1350px")
        assertContains(prompt, "fit the content to the frame")
    }

    @Test
    fun includesOnlyFramingWithoutDimensionsForOriginalPreset() {
        val request = samplePhotoEditRequest(targetPlatform = TargetPlatform.Original)

        val prompt = assembleFromRequest(request)

        assertContains(prompt, "Original (no resize)")
        assertContains(prompt, "preserve the original dimensions without resizing")
        assertFalse(prompt.contains("px"))
    }

    @Test
    fun includesPlatformAndFramingForEveryTargetPlatform() {
        for (platform in TargetPlatform.entries) {
            val request = samplePhotoEditRequest(targetPlatform = platform)

            val prompt = assembleFromRequest(request)

            assertContains(prompt, platform.platformPreset.displayName)
            assertContains(prompt, platform.platformPreset.defaultFramingBehavior.naturalDescription)
        }
    }

    @Test
    fun includesRealismLevelIntentInPrompt() {
        for (realism in RealismLevel.entries) {
            val request = samplePhotoEditRequest(realismLevel = realism)

            val prompt = assembleFromRequest(request)

            assertContains(prompt, "Apply a ${realism.adjective} edit.")
            assertContains(prompt, realism.promptIntent)
        }
    }

    @Test
    fun includesQualityTierInPrompt() {
        for (tier in QualityTier.entries) {
            val request = samplePhotoEditRequest(qualityTier = tier)

            val prompt = assembleFromRequest(request)

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
            val prompt = assembleFromRequest(request)
            assertContains(prompt, expectedSnippet, message = "EditIntent.${intent.name} should produce expected instruction")
        }
    }

    @Test
    fun normalizesSubjectDescriptionEndingWithTerminalPunctuation() {
        val request = samplePhotoEditRequest(
            subjectDescription = "A cat on a sofa!",
        )

        val prompt = assembleFromRequest(request)

        assertContains(prompt, "The subject is A cat on a sofa.")
        assertFalse(prompt.contains("!."))
    }

    @Test
    fun omitsSubjectDescriptionWhenNormalizationRemovesAllCharacters() {
        val request = samplePhotoEditRequest(
            subjectDescription = "?!...",
        )

        val prompt = assembleFromRequest(request)

        assertFalse(prompt.contains("The subject is"))
        assertFalse(prompt.contains("Preserve the subject"))
        assertFalse(prompt.contains(" is ."))
    }

    @Test
    fun normalizesUserRefinementEndingWithTerminalPunctuation() {
        val request = samplePhotoEditRequest(
            userRefinement = "Focus on the coffee cup?",
        )

        val prompt = assembleFromRequest(request)

        assertContains(prompt, "Focus on the coffee cup.")
        assertFalse(prompt.contains("?."))
    }

    @Test
    fun omitsUserRefinementWhenNormalizationRemovesAllCharacters() {
        val request = samplePhotoEditRequest(
            userRefinement = "?!...",
        )

        val prompt = assembleFromRequest(request)

        assertFalse(prompt.contains(" ."))
        assertFalse(prompt.endsWith(" "))
    }

    @Test
    fun trimsSubjectDescriptionWhitespace() {
        val request = samplePhotoEditRequest(subjectDescription = "  A cat on a sofa  ")

        val prompt = assembleFromRequest(request)

        assertContains(prompt, "The subject is A cat on a sofa.")
    }

    @Test
    fun trimsUserRefinementWhitespace() {
        val request = samplePhotoEditRequest(userRefinement = "  Crop tighter  ")

        val prompt = assembleFromRequest(request)

        assertContains(prompt, "Crop tighter.")
    }

    @Test
    fun includesMaskRegionWhenPresent() {
        val request = samplePhotoEditRequest(
            maskRegion = MaskRegion(MaskBounds.Normalized(0.25f, 0.1f, 0.5f, 0.5f)),
        )

        val prompt = assembleFromRequest(request)

        assertContains(prompt, "The edit is constrained to the region spanning")
        assertContains(prompt, "0.25 to 0.75 horizontally")
        assertContains(prompt, "0.1 to 0.6 vertically")
        assertContains(prompt, "in normalized coordinates")
    }

    @Test
    fun includesPixelMaskRegionWithNaturalCoordinates() {
        val request = samplePhotoEditRequest(
            maskRegion = MaskRegion(MaskBounds.Pixel(100, 200, 400, 400)),
        )

        val prompt = assembleFromRequest(request)

        assertContains(prompt, "The edit is constrained to the pixel region from (100, 200) to (500, 600).")
    }

    @Test
    fun omitsMaskRegionWhenNull() {
        val request = samplePhotoEditRequest(maskRegion = null)

        val prompt = assembleFromRequest(request)

        assertFalse(prompt.contains("constrained to the region"))
        assertFalse(prompt.contains("pixel region"))
    }

    @Test
    fun includesLogoOverlayWordingForAddLogoOverlay() {
        val request = samplePhotoEditRequest(
            intent = EditIntent.AddLogoOverlay,
            subjectDescription = "A storefront with a blank sign area",
            userRefinement = "Place the logo in the top-right corner",
            maskRegion = MaskRegion(MaskBounds.Normalized(0.7f, 0.0f, 0.3f, 0.2f)),
        )

        val prompt = assembleFromRequest(request)

        assertContains(prompt, "Overlay a logo or watermark onto the image")
        assertContains(prompt, "The subject is A storefront with a blank sign area.")
        assertContains(prompt, "Preserve the subject's appearance.")
        assertContains(prompt, "Place the logo in the top-right corner.")
        assertContains(prompt, "The edit is constrained to the region spanning")
    }

    @Test
    fun includesObjectRemovalWordingForRemoveObject() {
        val request = samplePhotoEditRequest(
            intent = EditIntent.RemoveObject,
            subjectDescription = "A parked red car with a scratch on the door",
            userRefinement = "Remove the scratch completely and fill naturally",
            maskRegion = MaskRegion(MaskBounds.Pixel(300, 400, 150, 50)),
        )

        val prompt = assembleFromRequest(request)

        assertContains(prompt, "Remove the specified object or element from the image")
        assertContains(prompt, "The subject is A parked red car with a scratch on the door.")
        assertContains(prompt, "Preserve the subject's appearance.")
        assertContains(prompt, "Remove the scratch completely and fill naturally.")
        assertContains(prompt, "The edit is constrained to the pixel region from (300, 400) to (450, 450).")
    }

    @Test
    fun maskRegionDescriptionIsNaturalProseNoColonLabels() {
        val normalized = samplePhotoEditRequest(
            maskRegion = MaskRegion(MaskBounds.Normalized(0.1f, 0.2f, 0.3f, 0.4f)),
        )
        val pixel = samplePhotoEditRequest(
            maskRegion = MaskRegion(MaskBounds.Pixel(50, 60, 100, 200)),
        )

        val normalizedPrompt = assembleFromRequest(normalized)
        val pixelPrompt = assembleFromRequest(pixel)

        assertFalse(normalizedPrompt.contains("maskRegion:"))
        assertFalse(normalizedPrompt.contains("MaskBounds."))
        assertFalse(normalizedPrompt.contains("Normalized("))
        assertFalse(normalizedPrompt.contains("left="))
        assertFalse(pixelPrompt.contains("maskRegion:"))
        assertFalse(pixelPrompt.contains("MaskBounds."))
        assertFalse(pixelPrompt.contains("Pixel("))
        assertFalse(pixelPrompt.contains("left="))
    }

    @Test
    fun persistedRequestPromptMatchesBuildPromptOutput() {
        val assembledPrompt = PhotoEditPromptAssembler.buildPrompt(
            intent = EditIntent.ImproveLighting,
            realismLevel = RealismLevel.Photoreal,
            qualityTier = QualityTier.High,
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            maskRegion = null,
            subjectDescription = "A coffee cup on a wooden table",
            userRefinement = "Focus on the coffee cup",
        )

        val persisted = PhotoEditRequest(
            id = photoEditRequestId,
            draftId = draftId,
            sourceMediaAssetId = mediaAssetId,
            intent = EditIntent.ImproveLighting,
            realismLevel = RealismLevel.Photoreal,
            qualityTier = QualityTier.High,
            prompt = assembledPrompt,
            userRefinement = "Focus on the coffee cup",
            subjectDescription = "A coffee cup on a wooden table",
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            maskRegion = null,
            createdAtEpochMillis = createdAt,
        )

        val rebuilt = PhotoEditPromptAssembler.buildPrompt(
            intent = persisted.intent,
            realismLevel = persisted.realismLevel,
            qualityTier = persisted.qualityTier,
            targetPlatform = persisted.targetPlatform,
            maskRegion = persisted.maskRegion,
            subjectDescription = persisted.subjectDescription,
            userRefinement = persisted.userRefinement,
        )

        assertEquals(persisted.prompt, rebuilt, "Persisted assembled prompt must match fresh build — no double-assembly")
    }

    @Test
    fun persistedRequestWithAllOptionsRoundTripsThroughBuildPrompt() {
        val assembled = PhotoEditPromptAssembler.buildPrompt(
            intent = EditIntent.RemoveObject,
            realismLevel = RealismLevel.Flat,
            qualityTier = QualityTier.Medium,
            targetPlatform = TargetPlatform.InstagramPortrait,
            maskRegion = MaskRegion(MaskBounds.Pixel(100, 200, 300, 400)),
            subjectDescription = "A person standing in front of a wall",
            userRefinement = "Keep the person centered",
        )

        val persisted = PhotoEditRequest(
            id = photoEditRequestId,
            draftId = draftId,
            sourceMediaAssetId = mediaAssetId,
            intent = EditIntent.RemoveObject,
            realismLevel = RealismLevel.Flat,
            qualityTier = QualityTier.Medium,
            prompt = assembled,
            userRefinement = "Keep the person centered",
            subjectDescription = "A person standing in front of a wall",
            targetPlatform = TargetPlatform.InstagramPortrait,
            maskRegion = MaskRegion(MaskBounds.Pixel(100, 200, 300, 400)),
            createdAtEpochMillis = createdAt,
        )

        val rebuilt = PhotoEditPromptAssembler.buildPrompt(
            intent = persisted.intent,
            realismLevel = persisted.realismLevel,
            qualityTier = persisted.qualityTier,
            targetPlatform = persisted.targetPlatform,
            maskRegion = persisted.maskRegion,
            subjectDescription = persisted.subjectDescription,
            userRefinement = persisted.userRefinement,
        )

        assertEquals(persisted.prompt, rebuilt)
    }

    private fun samplePhotoEditRequest(
        prompt: String = "Make the image brighter.",
        targetPlatform: TargetPlatform = TargetPlatform.InstagramFeedSquare,
        intent: EditIntent = EditIntent.ImproveLighting,
        realismLevel: RealismLevel = RealismLevel.Photoreal,
        qualityTier: QualityTier = QualityTier.Standard,
        subjectDescription: String? = null,
        userRefinement: String? = null,
        maskRegion: MaskRegion? = null,
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
        maskRegion = maskRegion,
        createdAtEpochMillis = createdAt,
    )
}
