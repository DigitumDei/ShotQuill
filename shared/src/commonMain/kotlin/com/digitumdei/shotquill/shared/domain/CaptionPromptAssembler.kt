package com.digitumdei.shotquill.shared.domain

import com.digitumdei.shotquill.shared.settings.ActiveBrandProfileStore

class CaptionPromptAssembler(
    private val activeBrandProfileStore: ActiveBrandProfileStore,
) {
    fun assembleCaptionPrompt(
        visionDescription: String,
        targetPlatform: TargetPlatform,
        userNotes: String? = null,
        productName: String? = null,
        eventNote: String? = null,
    ): String {
        val brandProfile = activeBrandProfileStore.readActiveBrandProfile()
        return buildString {
            appendLine("Write a social caption for ${targetPlatform.wireValue}.")
            appendLine()
            appendLine("Image description: ${visionDescription.trim()}")
            appendLine()
            userNotes?.takeIf { it.isNotBlank() }?.let {
                appendLine("User notes: ${it.trim()}")
            }
            productName?.takeIf { it.isNotBlank() }?.let {
                appendLine("Product or subject: ${it.trim()}")
            }
            eventNote?.takeIf { it.isNotBlank() }?.let {
                appendLine("Event or occasion: ${it.trim()}")
            }
            appendLine()
            if (brandProfile == null) {
                appendLine("No active brand profile is configured; use a clear neutral voice.")
            } else {
                appendBrandProfileContext(brandProfile)
            }
            appendLine()
            appendLine("Return:")
            appendLine("- A main caption suitable for ${targetPlatform.wireValue}")
            appendLine("- A shorter caption variant")
            appendLine("- 2 to 4 minimal, relevant hashtags")
        }.trim()
    }

    fun assembleAltTextPrompt(visionDescription: String): String {
        val brandProfile = activeBrandProfileStore.readActiveBrandProfile()
        return buildString {
            appendLine("Write accessible alt text for this image.")
            appendLine("Describe the image plainly and factually. Do not use marketing language or hashtags.")
            appendLine()
            appendLine("Image description: ${visionDescription.trim()}")
            if (brandProfile != null) {
                appendLine()
                appendLine("Image context: ${brandProfile.displayName}")
            }
        }.trim()
    }

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
