package com.digitumdei.shotquill.shared.domain

data class AspectRatio(
    val width: Int,
    val height: Int,
) {
    init {
        require(width > 0) { "AspectRatio width must be greater than zero, got $width" }
        require(height > 0) { "AspectRatio height must be greater than zero, got $height" }
    }
}

enum class FramingBehavior(val wireValue: String) {
    Fit("fit"),
    Fill("fill"),
    Stretch("stretch"),
    NoResize("no_resize");

    companion object {
        fun fromWireValue(value: String): FramingBehavior? = entries.firstOrNull { it.wireValue == value }
    }
}

data class PlatformPreset(
    val platform: TargetPlatform,
    val displayName: String,
    val aspectRatio: AspectRatio?,
    val recommendedWidthPx: Int?,
    val recommendedHeightPx: Int?,
    val defaultFramingBehavior: FramingBehavior,
) {
    init {
        require(recommendedWidthPx == null || recommendedWidthPx > 0) { "recommendedWidthPx must be null or greater than zero, got $recommendedWidthPx" }
        require(recommendedHeightPx == null || recommendedHeightPx > 0) { "recommendedHeightPx must be null or greater than zero, got $recommendedHeightPx" }
        require(displayName.isNotBlank()) { "displayName cannot be blank" }
    }

    companion object {
        val defaults: Map<TargetPlatform, PlatformPreset> = TargetPlatform.entries.associateBy(
            { it },
            { platform -> when (platform) {
                TargetPlatform.InstagramFeedSquare -> PlatformPreset(
                    platform = platform,
                    displayName = "Instagram Feed Square",
                    aspectRatio = AspectRatio(width = 1, height = 1),
                    recommendedWidthPx = 1080,
                    recommendedHeightPx = 1080,
                    defaultFramingBehavior = FramingBehavior.Fit,
                )
                TargetPlatform.InstagramPortrait -> PlatformPreset(
                    platform = platform,
                    displayName = "Instagram Portrait",
                    aspectRatio = AspectRatio(width = 4, height = 5),
                    recommendedWidthPx = 1080,
                    recommendedHeightPx = 1350,
                    defaultFramingBehavior = FramingBehavior.Fit,
                )
                TargetPlatform.InstagramStoryReel -> PlatformPreset(
                    platform = platform,
                    displayName = "Instagram Story / Reel",
                    aspectRatio = AspectRatio(width = 9, height = 16),
                    recommendedWidthPx = 1080,
                    recommendedHeightPx = 1920,
                    defaultFramingBehavior = FramingBehavior.Fill,
                )
                TargetPlatform.FacebookPost -> PlatformPreset(
                    platform = platform,
                    displayName = "Facebook Post",
                    aspectRatio = AspectRatio(width = 191, height = 100),
                    recommendedWidthPx = 1200,
                    recommendedHeightPx = 630,
                    defaultFramingBehavior = FramingBehavior.Fit,
                )
                TargetPlatform.BlueskyPost -> PlatformPreset(
                    platform = platform,
                    displayName = "Bluesky Post",
                    aspectRatio = AspectRatio(width = 3, height = 2),
                    recommendedWidthPx = 1200,
                    recommendedHeightPx = 800,
                    defaultFramingBehavior = FramingBehavior.Fit,
                )
                TargetPlatform.Original -> PlatformPreset(
                    platform = platform,
                    displayName = "Original (no resize)",
                    aspectRatio = null,
                    recommendedWidthPx = null,
                    recommendedHeightPx = null,
                    defaultFramingBehavior = FramingBehavior.NoResize,
                )
            } },
        )
    }
}
