package com.digitumdei.shotquill.shared.domain

import com.digitumdei.shotquill.shared.settings.ActiveBrandProfileStore

class CaptionRequestFactory(
    private val activeBrandProfileStore: ActiveBrandProfileStore,
) {
    fun createCaptionRequest(
        id: CaptionRequestId,
        draftId: PostDraftId,
        targetPlatform: TargetPlatform,
        photoDescription: String,
        createdAtEpochMillis: Long,
    ): CaptionRequest {
        val brandProfile = activeBrandProfileStore.readActiveBrandProfile()
        return CaptionRequest(
            id = id,
            draftId = draftId,
            targetPlatform = targetPlatform,
            prompt = buildCaptionPrompt(photoDescription, targetPlatform, brandProfile),
            tone = brandProfile?.voice,
            brandProfileId = brandProfile?.id,
            createdAtEpochMillis = createdAtEpochMillis,
        )
    }

    fun buildAltTextPrompt(photoDescription: String): String =
        buildString {
            appendLine("Write accessible alt text for this image.")
            appendLine("Image description: ${photoDescription.trim()}")
            activeBrandProfileStore.readActiveBrandProfile()?.let {
                appendLine()
                appendBrandProfileContext(it)
            }
        }.trim()

    private fun buildCaptionPrompt(
        photoDescription: String,
        targetPlatform: TargetPlatform,
        brandProfile: BrandProfile?,
    ): String =
        buildString {
            appendLine("Write a social caption for ${targetPlatform.wireValue}.")
            appendLine("Image description: ${photoDescription.trim()}")
            if (brandProfile == null) {
                appendLine("No active brand profile is configured; use a clear neutral voice.")
            } else {
                appendLine()
                appendBrandProfileContext(brandProfile)
            }
        }.trim()

    private fun StringBuilder.appendBrandProfileContext(profile: BrandProfile) {
        appendLine("Active brand profile:")
        appendLine("Brand name: ${profile.displayName}")
        profile.audience?.takeIf { it.isNotBlank() }?.let {
            appendLine("Short description: $it")
        }
        appendLine("Default tone: ${profile.voice}")
        if (profile.defaultHashtags.isNotEmpty()) {
            appendLine("Default hashtags: ${profile.defaultHashtags.joinToString(" ")}")
        }
        if (profile.websiteOrSocialLinks.isNotEmpty()) {
            appendLine("Website/social links: ${profile.websiteOrSocialLinks.joinToString(", ")}")
        }
        profile.visualStyleNotes?.takeIf { it.isNotBlank() }?.let {
            appendLine("Visual style notes: $it")
        }
        profile.productNamingNotes?.takeIf { it.isNotBlank() }?.let {
            appendLine("Product or beer naming notes: $it")
        }
    }
}
