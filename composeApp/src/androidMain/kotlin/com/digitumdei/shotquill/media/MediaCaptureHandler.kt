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
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import com.digitumdei.shotquill.model.MediaCaptureResult
import java.io.File

private data class CameraCaptureRef(
    val file: File,
    val uri: Uri,
)

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
    var cameraRef by remember { mutableStateOf<CameraCaptureRef?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        if (success) {
            val ref = cameraRef
            if (ref != null) {
                runCatching {
                    mediaFileManager.handleCameraCapture(ref.file)
                }.onSuccess { result ->
                    onResult(result)
                }.onFailure { e ->
                    onError("Failed to process camera capture: ${e.message}")
                }
            }
        } else {
            onError("Camera capture cancelled")
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
            cameraRef = CameraCaptureRef(file, uri)
            cameraLauncher.launch(uri)
        } else {
            onError("Camera permission denied")
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                mediaFileManager.importFromContentUri(uri)
            }.onSuccess { result ->
                onResult(result)
            }.onFailure { e ->
                onError("Failed to import image: ${e.message}")
            }
        } else {
            onError("Gallery selection cancelled")
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
