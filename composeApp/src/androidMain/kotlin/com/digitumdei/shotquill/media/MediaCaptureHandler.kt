package com.digitumdei.shotquill.media

import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import com.digitumdei.shotquill.BuildConfig
import com.digitumdei.shotquill.model.MediaCaptureResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MediaCaptureHandler(
    val launchCamera: () -> Unit,
    val launchGallery: () -> Unit,
)

@Composable
fun rememberMediaCaptureHandler(
    context: Context,
    mediaFileManager: MediaFileManager,
    onImportGallery: (suspend (Uri) -> MediaCaptureResult)? = null,
    onResult: (MediaCaptureResult) -> Unit,
    onError: (String) -> Unit,
): MediaCaptureHandler {
    var cameraFilePath by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        if (success) {
            val path = cameraFilePath
            cameraFilePath = null
            if (path != null) {
                scope.launch {
                    runCatching {
                        mediaFileManager.handleCameraCapture(File(path))
                    }.onSuccess { result ->
                        onResult(result)
                    }.onFailure { e ->
                        if (e is CancellationException) throw e
                        onError("Failed to process camera capture: ${e.message}")
                    }
                }
            } else {
                onError("Camera capture failed - session was lost")
            }
        } else {
            val path = cameraFilePath
            cameraFilePath = null
            if (path != null) {
                runCatching { File(path).delete() }
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            scope.launch {
                val file = withContext(Dispatchers.IO) {
                    mediaFileManager.createCameraCaptureFile()
                }
                val uri = FileProvider.getUriForFile(
                    context,
                    "${BuildConfig.APPLICATION_ID}.fileprovider",
                    file,
                )
                val previousPath = cameraFilePath
                if (previousPath != null) {
                    withContext(Dispatchers.IO) { File(previousPath).delete() }
                }
                cameraFilePath = file.absolutePath
                cameraLauncher.launch(uri)
            }
        } else {
            onError("Camera permission denied")
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    if (onImportGallery != null) {
                        onImportGallery(uri)
                    } else {
                        error("Gallery import not available")
                    }
                }.onSuccess { result ->
                    onResult(result)
                }.onFailure { e ->
                    if (e is CancellationException) throw e
                    onError("Failed to import image: ${e.message}")
                }
            }
        }
    }

    return remember(context, mediaFileManager) {
        MediaCaptureHandler(
            launchCamera = {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            },
            launchGallery = {
                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
        )
    }
}
