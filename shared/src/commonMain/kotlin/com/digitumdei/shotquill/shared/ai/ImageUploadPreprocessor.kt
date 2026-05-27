package com.digitumdei.shotquill.shared.ai

data class ImageUploadPreprocessingConfig(
    val maxLongEdgePx: Int = 1568,
    val jpegQuality: Int = 85,
    val providerRequiresPng: Boolean = false,
) {
    init {
        require(maxLongEdgePx > 0) { "Maximum long edge must be greater than zero" }
        require(jpegQuality in 1..100) { "JPEG quality must be between 1 and 100" }
    }
}

fun interface AiImageUploadPreprocessor {
    fun preprocess(
        image: AiImageInput,
        config: ImageUploadPreprocessingConfig = ImageUploadPreprocessingConfig(),
    ): AiImageInput
}

expect object PlatformImageUploadPreprocessor : AiImageUploadPreprocessor
