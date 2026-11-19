package com.digitumdei.shotquill.social

import android.content.Context
import com.digitumdei.shotquill.shared.settings.LocalSettingsRepository
import com.digitumdei.shotquill.shared.social.FirebaseHostSettings
import com.digitumdei.shotquill.shared.social.TemporaryImageHost
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.StorageReference
import java.util.concurrent.TimeUnit

/**
 * Temporary image hosting in the user's own Firebase project. The project is
 * described by values typed into settings, so nothing about it is built into
 * the app or kept in the repository. Uploads are made as a dedicated Firebase
 * user, and the address handed back carries its own access token, so the
 * storage rules do not need to allow public reads.
 */
class FirebaseImageHost(
    private val context: Context,
    private val settings: LocalSettingsRepository,
) : TemporaryImageHost {
    override val isConfigured: Boolean
        get() {
            val host = settings.readSettings().firebaseHost
            return host.hasProject && host.email.isNotBlank() &&
                settings.getSecret(FirebaseHostSettings.UploadPasswordSecret) != null
        }

    override fun upload(jpeg: ByteArray, fileName: String): String {
        val reference = reference(fileName)
        val metadata = StorageMetadata.Builder().setContentType("image/jpeg").build()
        await(reference.putBytes(jpeg, metadata), "upload the image")
        return await(reference.downloadUrl, "get the image's address").toString()
    }

    override fun delete(fileName: String) {
        await(reference(fileName).delete(), "remove the image")
    }

    private fun reference(fileName: String): StorageReference {
        val host = settings.readSettings().firebaseHost
        val password = settings.getSecret(FirebaseHostSettings.UploadPasswordSecret)
        check(host.hasProject && host.email.isNotBlank() && password != null) { "Image hosting is not set up." }
        val app = appFor(host)
        val auth = FirebaseAuth.getInstance(app)
        if (auth.currentUser?.email?.equals(host.email, ignoreCase = true) != true) {
            await(auth.signInWithEmailAndPassword(host.email.trim(), password), "sign in to Firebase")
        }
        return FirebaseStorage.getInstance(app).reference.child("$Folder/$fileName")
    }

    /** One Firebase app per distinct project, created the first time it is needed. */
    private fun appFor(host: FirebaseHostSettings): FirebaseApp {
        val name = "shotquill-${(host.projectId + host.appId + host.apiKey + host.storageBucket).hashCode()}"
        return FirebaseApp.getApps(context).firstOrNull { it.name == name }
            ?: FirebaseApp.initializeApp(
                context.applicationContext,
                FirebaseOptions.Builder()
                    .setApiKey(host.apiKey.trim())
                    .setApplicationId(host.appId.trim())
                    .setProjectId(host.projectId.trim())
                    .setStorageBucket(host.storageBucket.trim())
                    .build(),
                name,
            )
    }

    private fun <T> await(task: com.google.android.gms.tasks.Task<T>, step: String): T =
        try {
            Tasks.await(task, TimeoutSeconds, TimeUnit.SECONDS)
        } catch (e: Exception) {
            val cause = e.cause ?: e
            throw IllegalStateException("Firebase could not $step: ${cause.message ?: cause::class.simpleName}", cause)
        }

    private companion object {
        const val Folder = "shotquill-temp"
        const val TimeoutSeconds = 60L
    }
}
