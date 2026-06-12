package com.digitumdei.shotquill.share

data class ShareResult(
    val success: Boolean,
    /**
     * URI of the content handed to the system share sheet (e.g. the resolved
     * content provider URI), recorded for diagnostics. The receiving app chosen
     * in the share sheet is not knowable, so this is the closest available
     * record of where the share went.
     */
    val destinationUri: String? = null,
    val errorMessage: String? = null,
)

fun interface PostShareLauncher {
    fun share(imageUri: String?, text: String): ShareResult
}
