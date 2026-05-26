package com.digitumdei.shotquill

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.digitumdei.shotquill.media.MediaFileManager
import com.digitumdei.shotquill.media.rememberMediaCaptureHandler
import com.digitumdei.shotquill.model.MediaCaptureResult
import com.digitumdei.shotquill.shared.settings.AndroidLocalSettingsRepository
import com.digitumdei.shotquill.shared.storage.AndroidBrandProfileRepositoryFactory
import com.digitumdei.shotquill.shared.storage.AndroidDatabaseDriverFactory
import com.digitumdei.shotquill.shared.storage.SqlDelightManualWorkflowRepository

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settingsRepository = AndroidLocalSettingsRepository(this)
        val brandProfileRepository = AndroidBrandProfileRepositoryFactory(this).create()
        val manualWorkflowRepository = SqlDelightManualWorkflowRepository(
            AndroidDatabaseDriverFactory(this).create(),
        )
        val mediaFileManager = MediaFileManager(
            contentResolver = contentResolver,
            filesDir = filesDir,
        )

        setContent {
            var captureResult by remember { mutableStateOf<MediaCaptureResult?>(null) }
            var captureError by remember { mutableStateOf<String?>(null) }

            val captureHandler = rememberMediaCaptureHandler(
                context = this@MainActivity,
                mediaFileManager = mediaFileManager,
                onResult = { result ->
                    captureResult = result
                    captureError = null
                },
                onError = { error ->
                    captureError = error
                    captureResult = null
                },
            )

            App(
                settingsRepository = settingsRepository,
                brandProfileRepository = brandProfileRepository,
                manualWorkflowRepository = manualWorkflowRepository,
                onCaptureFromCamera = captureHandler.launchCamera,
                onPickFromGallery = captureHandler.launchGallery,
                captureResult = captureResult,
                captureError = captureError,
                onClearCaptureResult = { captureResult = null },
                onClearCaptureError = { captureError = null },
            )
        }
    }
}
