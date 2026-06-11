package com.digitumdei.shotquill.share

data class ShareResult(
    val success: Boolean,
    val destinationUri: String? = null,
    val errorMessage: String? = null,
)

fun interface PostShareLauncher {
    fun share(imageUri: String?, text: String): ShareResult
}
