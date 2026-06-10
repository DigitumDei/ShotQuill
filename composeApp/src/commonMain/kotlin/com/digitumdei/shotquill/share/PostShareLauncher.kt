package com.digitumdei.shotquill.share

fun interface PostShareLauncher {
    fun share(imageUri: String?, text: String): Boolean
}
