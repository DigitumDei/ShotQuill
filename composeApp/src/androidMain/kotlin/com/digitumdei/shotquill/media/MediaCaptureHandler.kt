package com.digitumdei.shotquill.media

import android.Manifest
import android.content.Context
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
import com.digitumdei.shotquill.model.MediaCaptureResult
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
            if (path != null) {
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            mediaFileManager.handleCameraCapture(File(path))
                        }
                    }.onSuccess { result ->
                        onResult(result)
                    }.onFailure { e ->
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
            val file = mediaFileManager.createCameraCaptureFile()
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            cameraFilePath = file.absolutePath
            cameraLauncher.launch(uri)
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
                    withContext(Dispatchers.IO) {
                        mediaFileManager.importFromContentUri(uri)
                    }
                }.onSuccess { result ->
                    onResult(result)
                }.onFailure { e ->
                    onError("Failed to import image: ${e.message}")
                }
            }
        }
    }

    return remember(context, mediaFileManager, onResult, onError) {
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
