package com.digitumdei.shotquill

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.digitumdei.shotquill.clipboard.ClipboardWriter
import com.digitumdei.shotquill.image.PhotoRenderer
import com.digitumdei.shotquill.model.MediaCaptureResult
import com.digitumdei.shotquill.media.LogoPick
import com.digitumdei.shotquill.screen.HomeScreen
import com.digitumdei.shotquill.screen.PostEditorScreen
import com.digitumdei.shotquill.screen.SettingsScreen
import com.digitumdei.shotquill.screen.SettingsViewModel
import com.digitumdei.shotquill.share.PostShareLauncher
import com.digitumdei.shotquill.shared.ai.AltTextWriter
import com.digitumdei.shotquill.shared.domain.MediaAssetId
import com.digitumdei.shotquill.shared.domain.PostDraftId
import com.digitumdei.shotquill.shared.domain.PostFormat
import com.digitumdei.shotquill.shared.post.DraftBackedPostEditorStore
import com.digitumdei.shotquill.shared.post.InMemoryPhotoTreatmentStore
import com.digitumdei.shotquill.shared.post.PhotoTreatmentStore
import com.digitumdei.shotquill.shared.post.PostSummary
import com.digitumdei.shotquill.shared.settings.InMemoryLocalSettingsRepository
import com.digitumdei.shotquill.shared.settings.LocalSettingsRepository
import com.digitumdei.shotquill.shared.social.PostPublisher
import com.digitumdei.shotquill.shared.storage.PostDraftRepository
import com.digitumdei.shotquill.shared.workflow.NewPostCreator
import com.digitumdei.shotquill.speech.DictationController
import com.digitumdei.shotquill.ui.ShotQuillTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

internal enum class AppScreen {
    Home,
    Editor,
    Settings,
}

internal fun appScreenFromSaveable(value: String): AppScreen =
    AppScreen.entries.firstOrNull { it.name == value } ?: AppScreen.Home

/** Where the back action leads from each screen. Settings returns to wherever it was opened from. */
internal fun screenBehind(screen: AppScreen, settingsOpenedFrom: AppScreen): AppScreen = when (screen) {
    AppScreen.Home -> AppScreen.Home
    AppScreen.Editor -> AppScreen.Home
    AppScreen.Settings -> if (settingsOpenedFrom == AppScreen.Editor) AppScreen.Editor else AppScreen.Home
}

@Composable
fun App(
    settingsRepository: LocalSettingsRepository? = null,
    postDraftRepository: PostDraftRepository? = null,
    treatmentStore: PhotoTreatmentStore? = null,
    photoRenderer: PhotoRenderer? = null,
    onCaptureFromCamera: (() -> Unit)? = null,
    onPickFromGallery: (() -> Unit)? = null,
    captureResult: MediaCaptureResult? = null,
    captureError: String? = null,
    onClearCaptureResult: (() -> Unit)? = null,
    onClearCaptureError: (() -> Unit)? = null,
    onCleanupCapture: ((MediaCaptureResult) -> Unit)? = null,
    /** Opens the phone's file chooser for a logo. What comes back arrives as [logoPick]. */
    onPickLogo: (() -> Unit)? = null,
    logoPick: LogoPick? = null,
    onLogoPickHandled: (() -> Unit)? = null,
    clipboardWriter: ClipboardWriter? = null,
    postShareLauncher: PostShareLauncher? = null,
    dictation: DictationController? = null,
    altTextWriter: AltTextWriter? = null,
    publishers: List<PostPublisher> = emptyList(),
) {
    val settings = settingsRepository ?: remember { InMemoryLocalSettingsRepository() }
    val store = remember(postDraftRepository, treatmentStore) {
        postDraftRepository?.let {
            DraftBackedPostEditorStore(drafts = it, treatments = treatmentStore ?: InMemoryPhotoTreatmentStore())
        }
    }
    val newPostCreator = remember(postDraftRepository) { postDraftRepository?.let { NewPostCreator(it) } }

    var screenName by rememberSaveable { mutableStateOf(AppScreen.Home.name) }
    var settingsOpenedFromName by rememberSaveable { mutableStateOf(AppScreen.Home.name) }
    var currentDraftId by rememberSaveable { mutableStateOf<String?>(null) }
    var saveError by rememberSaveable { mutableStateOf<String?>(null) }
    var posts by remember { mutableStateOf<List<PostSummary>>(emptyList()) }
    // Bumped whenever settings may have changed, so an open post picks up the new look and logo.
    var settingsVersion by remember { mutableIntStateOf(0) }

    val screen = appScreenFromSaveable(screenName)

    fun openSettings() {
        settingsOpenedFromName = screen.name
        screenName = AppScreen.Settings.name
    }

    fun goBack() {
        if (screen == AppScreen.Settings) settingsVersion++
        val behind = screenBehind(screen, appScreenFromSaveable(settingsOpenedFromName))
        if (behind == AppScreen.Home) currentDraftId = null
        screenName = behind.name
    }

    LaunchedEffect(store, screen) {
        if (screen != AppScreen.Editor && store != null) {
            posts = withContext(Dispatchers.IO) { store.listPosts() }
        }
    }

    // A chosen logo is normally taken up by the open settings screen. If the
    // app was restarted while the chooser was open and has come back somewhere
    // else, the choice is still kept rather than dropped.
    LaunchedEffect(logoPick, screen) {
        val pick = logoPick ?: return@LaunchedEffect
        if (screen == AppScreen.Settings) return@LaunchedEffect
        if (pick is LogoPick.Picked) SettingsViewModel(settings, photoRenderer).logoImported(pick.uri, pick.caution)
        onLogoPickHandled?.invoke()
    }

    LaunchedEffect(captureResult) {
        val result = captureResult ?: return@LaunchedEffect
        saveError = null
        if (newPostCreator == null) {
            saveError = "Storage is not available, so the photo could not be kept."
            onCleanupCapture?.invoke(result)
            onClearCaptureResult?.invoke()
            return@LaunchedEffect
        }
        val suffix = Random.nextInt(0, Int.MAX_VALUE)
        try {
            val draft = withContext(Dispatchers.IO) {
                newPostCreator.createDraftFromMedia(
                    draftId = PostDraftId("draft-${result.createdAtEpochMillis}-$suffix"),
                    mediaAssetId = MediaAssetId("media-${result.createdAtEpochMillis}-$suffix"),
                    format = PostFormat.SingleImage,
                    uri = result.uri,
                    mimeType = result.mimeType,
                    widthPx = result.widthPx,
                    heightPx = result.heightPx,
                    createdAtEpochMillis = result.createdAtEpochMillis,
                )
            }
            currentDraftId = draft.id.value
            screenName = AppScreen.Editor.name
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            saveError = "The photo could not be kept: ${e.message ?: "unknown error"}"
            onCleanupCapture?.invoke(result)
        }
        onClearCaptureResult?.invoke()
    }

    ShotQuillTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            PlatformBackHandler(enabled = screen != AppScreen.Home) { goBack() }

            val draftId = currentDraftId
            val contentModifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)
            Surface(modifier = contentModifier, color = MaterialTheme.colorScheme.background) {
                when {
                    screen == AppScreen.Editor &&
                        draftId != null &&
                        store != null &&
                        photoRenderer != null &&
                        clipboardWriter != null &&
                        postShareLauncher != null -> {
                        // Keyed on settingsVersion so returning from settings reloads the post with them.
                        androidx.compose.runtime.key(draftId, settingsVersion) {
                            PostEditorScreen(
                                draftId = PostDraftId(draftId),
                                store = store,
                                settingsRepository = settings,
                                renderer = photoRenderer,
                                clipboardWriter = clipboardWriter,
                                shareLauncher = postShareLauncher,
                                dictation = dictation,
                                altTextWriter = altTextWriter,
                                publishers = publishers,
                                onBack = { goBack() },
                                onOpenSettings = { openSettings() },
                            )
                        }
                    }

                    screen == AppScreen.Settings -> {
                        val viewModel = remember(settings, photoRenderer, store) { SettingsViewModel(settings, photoRenderer, posts = store) }
                        val previewUri = posts.firstOrNull()?.originalUri
                        val previewSize = remember(previewUri, photoRenderer) {
                            previewUri?.let { photoRenderer?.orientedSize(it) }
                        }
                        LaunchedEffect(logoPick, viewModel) {
                            when (val pick = logoPick) {
                                is LogoPick.Picked -> viewModel.logoImported(pick.uri, pick.caution)
                                is LogoPick.Failed -> viewModel.logoImportFailed(pick.message)
                                null -> return@LaunchedEffect
                            }
                            onLogoPickHandled?.invoke()
                        }
                        SettingsScreen(
                            viewModel = viewModel,
                            previewUri = previewUri,
                            previewSize = previewSize,
                            dictationOnDevice = dictation?.takeIf { it.isAvailable }?.isOnDevice,
                            publishers = publishers,
                            onPickLogo = onPickLogo,
                            onBack = { goBack() },
                        )
                    }

                    else -> {
                        HomeScreen(
                            posts = posts,
                            isBusy = captureResult != null,
                            errorMessage = captureError ?: saveError,
                            onTakePhoto = onCaptureFromCamera ?: {},
                            onPickFromGallery = onPickFromGallery ?: {},
                            onOpenPost = { post ->
                                currentDraftId = post.id.value
                                screenName = AppScreen.Editor.name
                            },
                            onOpenSettings = { openSettings() },
                            onDismissError = {
                                onClearCaptureError?.invoke()
                                saveError = null
                            },
                        )
                    }
                }
            }
        }
    }
}
