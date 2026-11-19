package com.digitumdei.shotquill

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.digitumdei.shotquill.clipboard.AndroidClipboardWriter
import com.digitumdei.shotquill.image.AndroidPhotoRenderer
import com.digitumdei.shotquill.media.ContentResolverMediaImporter
import com.digitumdei.shotquill.media.LogoImporter
import com.digitumdei.shotquill.media.MediaFileManager
import com.digitumdei.shotquill.media.rememberMediaCaptureHandler
import com.digitumdei.shotquill.model.MediaCaptureResult
import com.digitumdei.shotquill.model.MediaCaptureResultSaver
import com.digitumdei.shotquill.share.AndroidPostShareLauncher
import com.digitumdei.shotquill.shared.ai.AltTextWriter
import com.digitumdei.shotquill.shared.ai.ChatCompletionsClient
import com.digitumdei.shotquill.shared.ai.UrlConnectionAiHttpTransport
import com.digitumdei.shotquill.speech.rememberDictationController
import com.digitumdei.shotquill.shared.post.AndroidPhotoTreatmentStore
import com.digitumdei.shotquill.shared.settings.AndroidLocalSettingsRepository
import com.digitumdei.shotquill.shared.storage.AndroidDatabaseDriverFactory
import com.digitumdei.shotquill.shared.storage.SqlDelightManualWorkflowRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private class LogoPickCallbacks(
    val onPicked: (uri: String, caution: String?) -> Unit,
    val onFailed: (message: String) -> Unit,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val settingsRepository = AndroidLocalSettingsRepository(this)
        val postDraftRepository = SqlDelightManualWorkflowRepository(
            AndroidDatabaseDriverFactory(this).create(),
        )
        val treatmentStore = AndroidPhotoTreatmentStore(this)
        val photoRenderer = AndroidPhotoRenderer(filesDir)
        val mediaFileManager = MediaFileManager(filesDir)
        val contentResolverMediaImporter = ContentResolverMediaImporter(contentResolver, filesDir)
        val logoImporter = LogoImporter(contentResolver, filesDir)
        val clipboardWriter = AndroidClipboardWriter(this)
        val postShareLauncher = AndroidPostShareLauncher(this)
        val altTextWriter = AltTextWriter(
            settings = settingsRepository,
            client = ChatCompletionsClient(UrlConnectionAiHttpTransport()),
        )

        setContent {
            // Re-applied whenever the system theme flips, so the status and
            // navigation bar icons stay readable without restarting the app.
            val darkTheme = isSystemInDarkTheme()
            DisposableEffect(darkTheme) {
                val bars = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme }
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
                onDispose {}
            }

            val scope = rememberCoroutineScope()
            var captureResult by rememberSaveable(stateSaver = MediaCaptureResultSaver) {
                mutableStateOf<MediaCaptureResult?>(null)
            }
            var captureError by rememberSaveable { mutableStateOf<String?>(null) }

            val captureHandler = rememberMediaCaptureHandler(
                context = this@MainActivity,
                mediaFileManager = mediaFileManager,
                onImportGallery = { uri -> contentResolverMediaImporter.importFromContentUri(uri) },
                onResult = { result ->
                    captureResult = result
                    captureError = null
                },
                onError = { error ->
                    captureError = error
                    captureResult = null
                },
            )

            // The settings screen asks for a logo and is told the stored file's URI once it is copied in.
            // The system file chooser is used rather than the photo picker,
            // because the photo picker does not list SVG files.
            val logoCallbacks = remember { mutableStateOf<LogoPickCallbacks?>(null) }
            val logoPicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument(),
            ) { uri ->
                val callbacks = logoCallbacks.value
                logoCallbacks.value = null
                if (uri != null && callbacks != null) {
                    scope.launch {
                        try {
                            val imported = logoImporter.import(uri)
                            callbacks.onPicked(imported.uri, imported.caution)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            callbacks.onFailed(e.message ?: "The logo could not be imported.")
                        }
                    }
                }
            }

            val dictation = rememberDictationController(this@MainActivity)

            App(
                settingsRepository = settingsRepository,
                postDraftRepository = postDraftRepository,
                treatmentStore = treatmentStore,
                photoRenderer = photoRenderer,
                onCaptureFromCamera = captureHandler.launchCamera,
                onPickFromGallery = captureHandler.launchGallery,
                captureResult = captureResult,
                captureError = captureError,
                onClearCaptureResult = { captureResult = null },
                onClearCaptureError = { captureError = null },
                onCleanupCapture = { result ->
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            File(result.uri.removePrefix("file://")).delete()
                        }
                    }
                },
                onPickLogo = { onPicked, onFailed ->
                    logoCallbacks.value = LogoPickCallbacks(onPicked, onFailed)
                    logoPicker.launch(arrayOf("image/*"))
                },
                clipboardWriter = clipboardWriter,
                postShareLauncher = postShareLauncher,
                dictation = dictation,
                altTextWriter = altTextWriter,
            )
        }
    }
}
