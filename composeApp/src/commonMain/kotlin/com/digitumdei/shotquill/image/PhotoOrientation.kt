package com.digitumdei.shotquill.image

/**
 * How a photo's stored pixels have to be turned to stand upright: rotated
 * clockwise by [degrees], then mirrored left to right when [mirrored].
 */
data class PhotoOrientation(val degrees: Int = 0, val mirrored: Boolean = false) {
    /** True when turning the photo upright swaps its width and height. */
    val swapsSides: Boolean get() = degrees % 180 != 0

    val isUpright: Boolean get() = degrees == 0 && !mirrored
}

/**
 * Reads the orientation a camera recorded in a photo's EXIF data. Phones
 * mostly write the four plain rotations, but the standard has four mirrored
 * ones as well, which front cameras and some editing tools use. Dropping the
 * mirror would export the photo flipped compared with how it is shown.
 */
fun photoOrientationFor(exifOrientation: Int): PhotoOrientation = when (exifOrientation) {
    ExifFlipHorizontal -> PhotoOrientation(degrees = 0, mirrored = true)
    ExifRotate180 -> PhotoOrientation(degrees = 180)
    ExifFlipVertical -> PhotoOrientation(degrees = 180, mirrored = true)
    ExifTranspose -> PhotoOrientation(degrees = 90, mirrored = true)
    ExifRotate90 -> PhotoOrientation(degrees = 90)
    ExifTransverse -> PhotoOrientation(degrees = 270, mirrored = true)
    ExifRotate270 -> PhotoOrientation(degrees = 270)
    else -> PhotoOrientation()
}

// The values the EXIF standard gives the orientation tag.
private const val ExifFlipHorizontal = 2
private const val ExifRotate180 = 3
private const val ExifFlipVertical = 4
private const val ExifTranspose = 5
private const val ExifRotate90 = 6
private const val ExifTransverse = 7
private const val ExifRotate270 = 8
