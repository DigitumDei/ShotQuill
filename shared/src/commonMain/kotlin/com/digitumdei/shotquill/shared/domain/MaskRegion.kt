package com.digitumdei.shotquill.shared.domain

sealed class MaskBounds {
    data class Normalized(
        val left: Float,
        val top: Float,
        val width: Float,
        val height: Float,
    ) : MaskBounds() {
        init {
            require(left in 0.0f..1.0f) { "Normalized left must be in range [0.0, 1.0], got $left" }
            require(top in 0.0f..1.0f) { "Normalized top must be in range [0.0, 1.0], got $top" }
            require(width > 0.0f && width <= 1.0f) { "Normalized width must be in range (0.0, 1.0], got $width" }
            require(height > 0.0f && height <= 1.0f) { "Normalized height must be in range (0.0, 1.0], got $height" }
        }
    }

    data class Pixel(
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int,
    ) : MaskBounds() {
        init {
            require(left >= 0) { "Pixel left must be non-negative, got $left" }
            require(top >= 0) { "Pixel top must be non-negative, got $top" }
            require(width > 0) { "Pixel width must be positive, got $width" }
            require(height > 0) { "Pixel height must be positive, got $height" }
        }
    }
}

data class MaskRegion(
    val bounds: MaskBounds,
) {
    override fun toString(): String = when (bounds) {
        is MaskBounds.Normalized ->
            "normalized:${bounds.left},${bounds.top},${bounds.width},${bounds.height}"
        is MaskBounds.Pixel ->
            "pixel:${bounds.left},${bounds.top},${bounds.width},${bounds.height}"
    }

    companion object {
        fun parse(value: String): MaskRegion {
            val colonIndex = value.indexOf(':')
            require(colonIndex > 0) { "Invalid mask region format: $value" }
            val type = value.substring(0, colonIndex)
            val coords = value.substring(colonIndex + 1).split(",")
            require(coords.size == 4) { "Mask region requires exactly 4 coordinates, got ${coords.size}" }
            return when (type) {
                "pixel" -> MaskRegion(
                    MaskBounds.Pixel(
                        left = coords[0].toInt(),
                        top = coords[1].toInt(),
                        width = coords[2].toInt(),
                        height = coords[3].toInt(),
                    ),
                )
                "normalized" -> MaskRegion(
                    MaskBounds.Normalized(
                        left = coords[0].toFloat(),
                        top = coords[1].toFloat(),
                        width = coords[2].toFloat(),
                        height = coords[3].toFloat(),
                    ),
                )
                else -> error("Unknown mask region type: $type")
            }
        }
    }
}
