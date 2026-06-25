package com.digitumdei.shotquill.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.digitumdei.shotquill.clipboard.ClipboardWriter
import com.digitumdei.shotquill.shared.domain.EditIntent
import com.digitumdei.shotquill.shared.domain.PhotoEditResultId
import com.digitumdei.shotquill.shared.domain.PostDraftId
import com.digitumdei.shotquill.shared.domain.PromptHistoryEntryId
import com.digitumdei.shotquill.shared.domain.QualityTier
import com.digitumdei.shotquill.shared.domain.RealismLevel
import com.digitumdei.shotquill.shared.domain.TargetPlatform
import com.digitumdei.shotquill.shared.domain.platformPreset
import com.digitumdei.shotquill.shared.storage.PostDraftRepository
import com.digitumdei.shotquill.shared.storage.PromptHistoryRepository
import com.digitumdei.shotquill.shared.workflow.AnalyzeVision
import com.digitumdei.shotquill.shared.workflow.PhotoEditExecutor
import com.digitumdei.shotquill.shared.workflow.PostTextGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant

@Composable
fun ManualPostDraftWorkspaceScreen(
    draftId: PostDraftId,
    postDraftRepository: PostDraftRepository,
    promptHistoryRepository: PromptHistoryRepository? = null,
    clipboardWriter: ClipboardWriter? = null,
    defaultTargetPlatform: TargetPlatform,
    defaultRealismLevel: RealismLevel = RealismLevel.Photoreal,
    defaultQualityTier: QualityTier = QualityTier.Standard,
    postTextGenerator: PostTextGenerator? = null,
    photoEditExecutor: PhotoEditExecutor? = null,
    analyzeVision: AnalyzeVision? = null,
    onNavigateToNewPost: () -> Unit,
    onNavigateToFinalComposer: () -> Unit = {},
) {
    val viewModel = remember(draftId, postDraftRepository, promptHistoryRepository, clipboardWriter, defaultTargetPlatform, defaultRealismLevel, defaultQualityTier, postTextGenerator, photoEditExecutor, analyzeVision) {
        ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = postDraftRepository,
            promptHistoryRepository = promptHistoryRepository,
            clipboardWriter = clipboardWriter,
            defaultTargetPlatform = defaultTargetPlatform,
            defaultRealismLevel = defaultRealismLevel,
            defaultQualityTier = defaultQualityTier,
            postTextGenerator = postTextGenerator,
            photoEditExecutor = photoEditExecutor,
            analyzeVision = analyzeVision,
        )
    }
    val state = viewModel.state
    val coroutineScope = rememberCoroutineScope()
    val operationMutex = remember(viewModel) { Mutex() }

    fun refresh(block: ManualPostDraftWorkspaceViewModel.() -> Unit) {
        coroutineScope.launch {
            operationMutex.withLock {
                withContext(Dispatchers.IO) {
                    viewModel.block()
                }
            }
        }
    }

    // AI operations DROP (rather than queue) a tap that arrives while another
    // operation holds the lock, so a double-tap cannot enqueue a duplicate AI
    // call. Cheap, non-duplicate actions (selection, copy, toggles) keep using
    // the queuing helpers above so they still apply after a running operation.
    fun refreshAiOperation(block: ManualPostDraftWorkspaceViewModel.() -> Unit) {
        coroutineScope.launch {
            if (operationMutex.tryLock()) {
                try {
                    withContext(Dispatchers.IO) {
                        viewModel.block()
                    }
                } finally {
                    operationMutex.unlock()
                }
            }
        }
    }

    fun refreshInMemory(block: ManualPostDraftWorkspaceViewModel.() -> Unit) {
        coroutineScope.launch {
            operationMutex.withLock {
                viewModel.block()
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

    ManualPostDraftWorkspaceContent(
        state = state,
        onAnalyzeVision = { refreshAiOperation { analyzeVisionDescription() } },
        onGeneratePostText = { refreshAiOperation { generatePostText() } },
        onEditPhotoWithAi = { refreshAiOperation { editPhotoWithAi() } },
        onSelectEditedPhoto = { refresh { selectEditedPhoto() } },
        onSelectOriginalPhoto = { refresh { selectOriginalPhoto() } },
        onCopyCaption = { refreshInMemory { markCaptionCopied() } },
        onCopyAltText = { refreshInMemory { markAltTextCopied() } },
        onCopyPromptHistoryEntry = { entryId -> refreshInMemory { copyPromptHistoryEntryPrompt(entryId) } },
        onSelectHistoricalEditedPhoto = { resultId -> refresh { selectHistoricalEditedPhoto(resultId) } },
        onShareOrExport = onNavigateToFinalComposer,
        onTogglePromptHistory = { refreshInMemory { togglePromptHistory() } },
        onTogglePhotoEditHistory = { refreshInMemory { togglePhotoEditHistory() } },
        onNavigateToNewPost = onNavigateToNewPost,
        onUpdatePhotoEditIntent = { viewModel.updatePhotoEditIntent(it) },
        onUpdatePhotoEditRefinement = { viewModel.updatePhotoEditRefinement(it) },
        onUpdatePhotoEditRealism = { viewModel.updatePhotoEditRealism(it) },
        onUpdatePhotoEditTargetPlatform = { viewModel.updatePhotoEditTargetPlatform(it) },
        onUpdatePhotoEditQualityTier = { viewModel.updatePhotoEditQualityTier(it) },
    )
}

@Composable
fun ManualPostDraftWorkspaceContent(
    state: ManualPostDraftWorkspaceState,
    onAnalyzeVision: () -> Unit,
    onGeneratePostText: () -> Unit,
    onEditPhotoWithAi: () -> Unit,
    onSelectEditedPhoto: () -> Unit,
    onSelectOriginalPhoto: () -> Unit,
    onCopyCaption: () -> Unit,
    onCopyAltText: () -> Unit,
    onCopyPromptHistoryEntry: (PromptHistoryEntryId) -> Unit,
    onSelectHistoricalEditedPhoto: (PhotoEditResultId) -> Unit,
    onShareOrExport: () -> Unit,
    onTogglePromptHistory: () -> Unit,
    onTogglePhotoEditHistory: () -> Unit,
    onNavigateToNewPost: () -> Unit,
    onUpdatePhotoEditIntent: (EditIntent) -> Unit,
    onUpdatePhotoEditRefinement: (String) -> Unit,
    onUpdatePhotoEditRealism: (RealismLevel) -> Unit,
    onUpdatePhotoEditTargetPlatform: (TargetPlatform) -> Unit,
    onUpdatePhotoEditQualityTier: (QualityTier) -> Unit,
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
            Column {
                Text("Draft Workspace", style = MaterialTheme.typography.headlineSmall)
                Text(state.draftId.value, style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = onNavigateToNewPost) {
                Text("New Post")
            }
        }

        state.statusMessage?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }

        if (state.isAiOperationInProgress) {
            Text(
                text = "Working on ${state.activeAiOperation?.displayName ?: "AI operation"}...",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        PhotoPreviewSection(
            originalPhotoUri = state.originalPhotoUri,
            editedPhotoUri = state.editedPhotoUri,
            activePhotoUri = state.activePhotoUri,
            onSelectOriginalPhoto = onSelectOriginalPhoto,
            onSelectEditedPhoto = onSelectEditedPhoto,
        )

        WorkspaceSection("Vision description", state.visionDescription ?: "No vision description yet")
        WorkspaceSection("Generated caption", state.generatedCaption ?: "No caption generated yet")
        WorkspaceSection("Generated alt text", state.generatedAltText ?: "No alt text generated yet")
        WorkspaceSection("Target platform", state.targetPlatform?.wireValue ?: "No target platform selected")
        WorkspaceSection("Draft status", state.draftStatus?.wireValue ?: "Unknown")

        OutlinedButton(
            onClick = onAnalyzeVision,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.actions.canAnalyzeVision && !state.isAiOperationInProgress,
        ) {
            Text("Analyze photo")
        }
        Button(
            onClick = onGeneratePostText,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.actions.canGeneratePostText && !state.isAiOperationInProgress,
        ) {
            Text("Generate post text")
        }
        val form = state.photoEditForm
        val formEnabled = form.operationState != PhotoEditFormOperationState.Loading

        EnumDropdown(
            label = "Edit intent",
            selected = form.selectedIntent,
            values = EditIntent.entries,
            display = { "${it.wireValue} — ${it.promptIntent}" },
            onSelected = onUpdatePhotoEditIntent,
            enabled = formEnabled,
        )

        OutlinedTextField(
            value = form.userRefinementText,
            onValueChange = onUpdatePhotoEditRefinement,
            label = { Text("Refinement (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            minLines = 1,
            maxLines = 3,
            enabled = formEnabled,
        )

        EnumDropdown(
            label = "Realism level",
            selected = form.selectedRealismLevel,
            values = RealismLevel.entries,
            display = { "${it.wireValue} — ${it.promptIntent}" },
            onSelected = onUpdatePhotoEditRealism,
            enabled = formEnabled,
        )

        EnumDropdown(
            label = "Target platform / framing",
            selected = form.selectedTargetPlatform,
            values = TargetPlatform.entries,
            display = {
                val preset = it.platformPreset
                "${preset.displayName} (${preset.defaultFramingBehavior.wireValue})"
            },
            onSelected = onUpdatePhotoEditTargetPlatform,
            enabled = formEnabled,
        )

        EnumDropdown(
            label = "Quality tier",
            selected = form.selectedQualityTier,
            values = QualityTier.entries,
            display = { it.wireValue },
            onSelected = onUpdatePhotoEditQualityTier,
            enabled = formEnabled,
        )

        Text(
            text = "Model: ${form.qualityTierModelNotes} — ${form.qualityTierCostNotes}",
            style = MaterialTheme.typography.bodySmall,
        )

        OutlinedButton(
            onClick = onEditPhotoWithAi,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.actions.canEditPhotoWithAi && form.operationState != PhotoEditFormOperationState.Loading && !state.isAiOperationInProgress,
        ) {
            Text(if (state.editedPhotoUri != null) "Re-run photo edit" else "Run photo edit")
        }

        if (form.operationState == PhotoEditFormOperationState.Loading) {
            Text(
                text = "Editing photo...",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        form.latestSummary?.let { summary ->
            Text(
                text = "Latest result (${form.latestModelName ?: "unknown"}): $summary",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (state.photoEditPromptHistory.isNotEmpty()) {
            OutlinedButton(
                onClick = onTogglePhotoEditHistory,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isPhotoEditHistoryVisible) "Hide photo edit history" else "View photo edit history")
            }
        }

        if (state.isPhotoEditHistoryVisible) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Previous edits", style = MaterialTheme.typography.titleMedium)
                state.photoEditHistoryItems.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectHistoricalEditedPhoto(item.resultId) },
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = item.editedPhotoUri,
                            contentDescription = "Edited photo",
                            modifier = Modifier.size(60.dp),
                            contentScale = ContentScale.Crop,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            item.modelName?.let {
                                Text(it, style = MaterialTheme.typography.titleSmall)
                            }
                            item.summary?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                text = Instant.fromEpochMilliseconds(item.createdAtEpochMillis).toString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                state.photoEditPromptHistory.forEach { entry ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = buildString {
                                    append(entry.operationType.displayName)
                                    if (entry.isFailure) append(" (Failed)")
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = if (entry.isFailure) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            )
                            OutlinedButton(
                                onClick = { onCopyPromptHistoryEntry(entry.id) },
                                enabled = state.actions.canCopyPrompt,
                            ) {
                                Text("Copy prompt")
                            }
                        }
                        Text(
                            text = Instant.fromEpochMilliseconds(entry.createdAtEpochMillis).toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        entry.provider?.let {
                            Text(
                                text = "Provider: $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        entry.requestSettings?.let {
                            Text(
                                text = "Settings: $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        entry.modelName?.let {
                            Text(
                                text = "Model: $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        entry.resultReference?.let {
                            Text(
                                text = "Ref: $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        entry.responseSummary?.let {
                            Text(
                                text = "Response: $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        entry.errorMessage?.let {
                            Text(
                                text = "Error: $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Text(
                            text = entry.prompt,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onCopyCaption,
                enabled = state.actions.canCopyCaption,
            ) {
                Text("Copy caption")
            }
            OutlinedButton(
                onClick = onCopyAltText,
                enabled = state.actions.canCopyAltText,
            ) {
                Text("Copy alt text")
            }
        }

        Button(
            onClick = onShareOrExport,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.actions.canShareOrExport,
        ) {
            Text("Review final share")
        }

        OutlinedButton(
            onClick = onTogglePromptHistory,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.actions.canViewPromptHistory,
        ) {
            Text("View prompt history")
        }

        if (state.isPromptHistoryVisible) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.promptHistory.forEach { entry ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = buildString {
                                    append(entry.operationType.displayName)
                                    if (entry.isFailure) append(" (Failed)")
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = if (entry.isFailure) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            )
                            OutlinedButton(
                                onClick = { onCopyPromptHistoryEntry(entry.id) },
                                enabled = state.actions.canCopyPrompt,
                            ) {
                                Text("Copy prompt")
                            }
                        }
                        Text(
                            text = Instant.fromEpochMilliseconds(entry.createdAtEpochMillis).toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        entry.provider?.let {
                            Text(
                                text = "Provider: $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        entry.requestSettings?.let {
                            Text(
                                text = "Settings: $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        entry.modelName?.let {
                            Text(
                                text = "Model: $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        entry.resultReference?.let {
                            Text(
                                text = "Ref: $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        entry.responseSummary?.let {
                            Text(
                                text = "Response: $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        entry.errorMessage?.let {
                            Text(
                                text = "Error: $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Text(
                            text = entry.prompt,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
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

@Composable
private fun PhotoPreviewSection(
    originalPhotoUri: String?,
    editedPhotoUri: String?,
    activePhotoUri: String?,
    onSelectOriginalPhoto: () -> Unit,
    onSelectEditedPhoto: () -> Unit,
) {
    val tabs = listOf(
        "Original" to (originalPhotoUri != null),
        "Edited" to (editedPhotoUri != null),
    )
    val selectedTabIndex = if (activePhotoUri == editedPhotoUri && editedPhotoUri != null) 1 else 0

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
            tabs.forEachIndexed { index, (label, enabled) ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = {
                        when (index) {
                            0 -> onSelectOriginalPhoto()
                            1 -> onSelectEditedPhoto()
                        }
                    },
                    enabled = enabled,
                    text = { Text(label) },
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            contentAlignment = Alignment.Center,
        ) {
            val uriToShow = when (selectedTabIndex) {
                1 -> editedPhotoUri
                else -> originalPhotoUri
            }
            if (uriToShow != null) {
                AsyncImage(
                    model = uriToShow,
                    contentDescription = if (selectedTabIndex == 0) "Original photo" else "Edited photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Text(
                    text = if (selectedTabIndex == 0) "No original photo" else "No edited photo yet",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> EnumDropdown(
    label: String,
    selected: T,
    values: List<T>,
    display: (T) -> String,
    onSelected: (T) -> Unit,
    enabled: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = display(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            enabled = enabled,
        )
        ExposedDropdownMenu(
            expanded = enabled && expanded,
            onDismissRequest = { expanded = false },
        ) {
            values.forEach { value ->
                DropdownMenuItem(
                    text = { Text(display(value)) },
                    onClick = {
                        if (enabled) {
                            onSelected(value)
                        }
                        expanded = false
                    },
                )
            }
        }
    }
}
