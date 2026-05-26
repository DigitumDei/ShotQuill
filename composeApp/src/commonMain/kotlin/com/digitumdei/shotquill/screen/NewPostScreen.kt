package com.digitumdei.shotquill.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.digitumdei.shotquill.model.MediaCaptureResult

enum class NewPostStep {
    ChooseSource,
    Processing,
    Complete,
    Error,
}

@Composable
fun NewPostScreen(
    onCaptureFromCamera: () -> Unit,
    onPickFromGallery: () -> Unit,
    onNavigateToSettings: () -> Unit,
    captureResult: MediaCaptureResult?,
    errorMessage: String?,
    onDismissResult: () -> Unit,
    onDismissError: () -> Unit,
) {
    var step by remember(captureResult, errorMessage) {
        mutableStateOf(
            when {
                captureResult != null -> NewPostStep.Complete
                errorMessage != null -> NewPostStep.Error
                else -> NewPostStep.ChooseSource
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            text = "New Post",
            style = MaterialTheme.typography.headlineSmall,
        )

        when (step) {
            NewPostStep.ChooseSource -> {
                Text(
                    text = "Take a photo or choose one from your gallery to start a draft.",
                    style = MaterialTheme.typography.bodyMedium,
                )

                Button(
                    onClick = onCaptureFromCamera,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Take Photo")
                }

                OutlinedButton(
                    onClick = onPickFromGallery,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Choose from Gallery")
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Settings")
                }
            }

            NewPostStep.Processing -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Processing image...")
                }
            }

            NewPostStep.Complete -> {
                val result = captureResult ?: return@Column
                Text(
                    text = "Draft created!",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = buildString {
                        appendLine("Image saved to: ${result.uri.takeLast(40)}")
                        result.mimeType?.let { appendLine("Type: $it") }
                        if (result.widthPx != null && result.heightPx != null) {
                            appendLine("Dimensions: ${result.widthPx} x ${result.heightPx}")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onDismissResult) {
                        Text("Create Another")
                    }
                    OutlinedButton(onClick = onNavigateToSettings) {
                        Text("Settings")
                    }
                }
            }

            NewPostStep.Error -> {
                Text(
                    text = errorMessage ?: "An unknown error occurred.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = {
                        onDismissError()
                        step = NewPostStep.ChooseSource
                    }) {
                        Text("Try Again")
                    }
                    OutlinedButton(onClick = onNavigateToSettings) {
                        Text("Settings")
                    }
                }
            }
        }
    }
}
