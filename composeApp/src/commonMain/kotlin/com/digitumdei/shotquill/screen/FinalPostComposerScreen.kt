package com.digitumdei.shotquill.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.digitumdei.shotquill.clipboard.ClipboardWriter
import com.digitumdei.shotquill.share.PostShareLauncher
import com.digitumdei.shotquill.shared.domain.EpochClock
import com.digitumdei.shotquill.shared.domain.PostDraftId
import com.digitumdei.shotquill.shared.domain.TargetPlatform
import com.digitumdei.shotquill.shared.domain.platformPreset
import com.digitumdei.shotquill.shared.storage.ManualWorkflowRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Composable
fun FinalPostComposerScreen(
    draftId: PostDraftId,
    repository: ManualWorkflowRepository,
    clipboardWriter: ClipboardWriter,
    postShareLauncher: PostShareLauncher,
    clock: EpochClock = EpochClock.Default,
    defaultTargetPlatform: TargetPlatform = TargetPlatform.InstagramFeedSquare,
    onBack: () -> Unit,
) {
    val viewModel = remember(draftId, repository, clipboardWriter, postShareLauncher, clock, defaultTargetPlatform) {
        FinalPostComposerViewModel(
            draftId = draftId,
            repository = repository,
            clipboardWriter = clipboardWriter,
            postShareLauncher = postShareLauncher,
            clock = clock,
            defaultTargetPlatform = defaultTargetPlatform,
        )
    }
    val state = viewModel.state
    val coroutineScope = rememberCoroutineScope()
    val operationMutex = remember(viewModel) { Mutex() }
    val persistJob = remember { mutableStateOf<Job?>(null) }

    fun refresh(block: FinalPostComposerViewModel.() -> Unit) {
        coroutineScope.launch {
            operationMutex.withLock {
                withContext(Dispatchers.IO) {
                    viewModel.block()
                }
            }
        }
    }

    fun queueFinalPostContentPersistence() {
        persistJob.value?.cancel()
        persistJob.value = coroutineScope.launch {
            delay(500L)
            operationMutex.withLock {
                withContext(Dispatchers.IO) {
                    viewModel.persistFinalPostContent()
                }
            }
        }
    }

    LaunchedEffect(viewModel) {
        operationMutex.withLock {
            withContext(Dispatchers.IO) {
                viewModel.load()
            }
        }
    }

    FinalPostComposerContent(
        state = state,
        onSelectOriginalPhoto = { refresh { selectOriginalPhoto() } },
        onSelectEditedPhoto = { refresh { selectEditedPhoto() } },
        onUpdateCaption = {
            viewModel.updateCaption(it)
            queueFinalPostContentPersistence()
        },
        onUpdateAltText = {
            viewModel.updateAltText(it)
            queueFinalPostContentPersistence()
        },
        onCopyCaption = { viewModel.copyCaption() },
        onCopyAltText = { viewModel.copyAltText() },
        onShare = { refresh { shareOrExport() } },
        onArchive = { refresh { archive() } },
        onBack = onBack,
    )
}

@Composable
fun FinalPostComposerContent(
    state: FinalPostComposerState,
    onSelectOriginalPhoto: () -> Unit,
    onSelectEditedPhoto: () -> Unit,
    onUpdateCaption: (String) -> Unit,
    onUpdateAltText: (String) -> Unit,
    onCopyCaption: () -> Unit,
    onCopyAltText: () -> Unit,
    onShare: () -> Unit,
    onArchive: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Final Post Composer", style = MaterialTheme.typography.headlineSmall)
            OutlinedButton(onClick = onBack) {
                Text("Back")
            }
        }

        state.statusMessage?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }

        WorkspaceSection("Selected photo", state.selectedPhotoUri ?: "No photo selected")

        WorkspaceSection("Original photo", state.originalPhotoUri ?: "No original photo")

        WorkspaceSection("Edited photo", state.editedPhotoUri ?: "No edited photo yet")

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.isShowingEdited) {
                OutlinedButton(
                    onClick = onSelectOriginalPhoto,
                    enabled = state.isLoaded,
                ) {
                    Text("Original")
                }
                Button(
                    onClick = onSelectEditedPhoto,
                    enabled = state.actions.canSelectEdited,
                ) {
                    Text("Edited")
                }
            } else {
                Button(
                    onClick = onSelectOriginalPhoto,
                    enabled = state.isLoaded,
                ) {
                    Text("Original")
                }
                OutlinedButton(
                    onClick = onSelectEditedPhoto,
                    enabled = state.actions.canSelectEdited,
                ) {
                    Text("Edited")
                }
            }
        }

        state.shortCaption?.let {
            WorkspaceSection("Short caption", it)
        }

        val hashtagText = state.hashtags.joinToString(" ") { tag ->
            if (tag.startsWith("#")) tag else "#$tag"
        }
        if (hashtagText.isNotEmpty()) {
            WorkspaceSection("Hashtags", hashtagText)
        }

        WorkspaceSection(
            "Target platform",
            state.targetPlatform?.platformPreset?.displayName ?: "No target platform selected",
        )

        OutlinedTextField(
            value = state.caption ?: "",
            onValueChange = onUpdateCaption,
            label = { Text("Caption") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            minLines = 3,
            maxLines = 6,
            enabled = state.isLoaded,
        )

        OutlinedTextField(
            value = state.altText ?: "",
            onValueChange = onUpdateAltText,
            label = { Text("Alt text") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            minLines = 2,
            maxLines = 4,
            enabled = state.isLoaded,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onCopyCaption,
                enabled = !state.caption.isNullOrBlank(),
            ) {
                Text("Copy caption")
            }
            OutlinedButton(
                onClick = onCopyAltText,
                enabled = state.altText != null,
            ) {
                Text("Copy alt text")
            }
        }

        Text(
            "Tap Share to open the image in the system share sheet. The caption is automatically copied to your clipboard — paste it manually in your target app.",
            style = MaterialTheme.typography.bodySmall,
        )

        Button(
            onClick = onShare,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.actions.canShare,
        ) {
            Text("Share image")
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onArchive,
                modifier = Modifier.weight(1f),
                enabled = state.actions.canArchive,
            ) {
                Text("Archive draft")
            }
        }
    }
}

@Composable
private fun WorkspaceSection(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
