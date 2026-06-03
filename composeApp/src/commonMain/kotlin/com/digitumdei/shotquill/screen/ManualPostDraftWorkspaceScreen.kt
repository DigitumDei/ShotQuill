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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.digitumdei.shotquill.shared.domain.EditIntent
import com.digitumdei.shotquill.shared.domain.PostDraftId
import com.digitumdei.shotquill.shared.domain.QualityTier
import com.digitumdei.shotquill.shared.domain.RealismLevel
import com.digitumdei.shotquill.shared.domain.TargetPlatform
import com.digitumdei.shotquill.shared.domain.platformPreset
import com.digitumdei.shotquill.shared.storage.PostDraftRepository
import com.digitumdei.shotquill.shared.workflow.PostTextGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Composable
fun ManualPostDraftWorkspaceScreen(
    draftId: PostDraftId,
    postDraftRepository: PostDraftRepository,
    defaultTargetPlatform: TargetPlatform,
    defaultRealismLevel: RealismLevel = RealismLevel.Photoreal,
    defaultQualityTier: QualityTier = QualityTier.Standard,
    postTextGenerator: PostTextGenerator? = null,
    onNavigateToNewPost: () -> Unit,
) {
    val viewModel = remember(draftId, postDraftRepository, defaultTargetPlatform, defaultRealismLevel, defaultQualityTier, postTextGenerator) {
        ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = postDraftRepository,
            defaultTargetPlatform = defaultTargetPlatform,
            defaultRealismLevel = defaultRealismLevel,
            defaultQualityTier = defaultQualityTier,
            postTextGenerator = postTextGenerator,
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

    LaunchedEffect(viewModel) {
        operationMutex.withLock {
            withContext(Dispatchers.IO) {
                viewModel.load()
            }
        }
    }

    ManualPostDraftWorkspaceContent(
        state = state,
        onAnalyzeVision = { refresh { analyzeVisionDescription() } },
        onGeneratePostText = { refresh { generatePostText() } },
        onEditPhotoWithAi = { refresh { editPhotoWithAi() } },
        onCopyCaption = { refresh { markCaptionCopied() } },
        onCopyAltText = { refresh { markAltTextCopied() } },
        onShareOrExport = { refresh { markShareOrExportStarted() } },
        onTogglePromptHistory = { refresh { togglePromptHistory() } },
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
    onCopyCaption: () -> Unit,
    onCopyAltText: () -> Unit,
    onShareOrExport: () -> Unit,
    onTogglePromptHistory: () -> Unit,
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

        WorkspaceSection("Original photo", state.originalPhotoUri ?: "No original photo")
        WorkspaceSection("Edited photo", state.editedPhotoUri ?: "No edited photo yet")
        WorkspaceSection("Vision description", state.visionDescription ?: "No vision description yet")
        WorkspaceSection("Generated caption", state.generatedCaption ?: "No caption generated yet")
        WorkspaceSection("Generated alt text", state.generatedAltText ?: "No alt text generated yet")
        WorkspaceSection("Target platform", state.targetPlatform?.wireValue ?: "No target platform selected")
        WorkspaceSection("Draft status", state.draftStatus?.wireValue ?: "Unknown")

        OutlinedButton(
            onClick = onAnalyzeVision,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.actions.canAnalyzeVision,
        ) {
            Text("Analyze photo")
        }
        Button(
            onClick = onGeneratePostText,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.actions.canGeneratePostText,
        ) {
            Text("Generate post text")
        }
        state.photoEditForm.unsupportedModelWarning?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        val form = state.photoEditForm

        EnumDropdown(
            label = "Edit intent",
            selected = form.selectedIntent,
            values = EditIntent.entries,
            display = { "${it.wireValue} — ${it.promptIntent}" },
            onSelected = onUpdatePhotoEditIntent,
        )

        OutlinedTextField(
            value = form.userRefinementText,
            onValueChange = onUpdatePhotoEditRefinement,
            label = { Text("Refinement (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            minLines = 1,
            maxLines = 3,
        )

        EnumDropdown(
            label = "Realism level",
            selected = form.selectedRealismLevel,
            values = RealismLevel.entries,
            display = { "${it.wireValue} — ${it.promptIntent}" },
            onSelected = onUpdatePhotoEditRealism,
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
        )

        EnumDropdown(
            label = "Quality tier",
            selected = form.selectedQualityTier,
            values = QualityTier.entries,
            display = { it.wireValue },
            onSelected = onUpdatePhotoEditQualityTier,
        )

        Text(
            text = "Model: ${form.qualityTierModelNotes} — ${form.qualityTierCostNotes}",
            style = MaterialTheme.typography.bodySmall,
        )

        OutlinedButton(
            onClick = onEditPhotoWithAi,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.actions.canEditPhotoWithAi && form.operationState != PhotoEditFormOperationState.Loading,
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
            Text("Share or export")
        }

        OutlinedButton(
            onClick = onTogglePromptHistory,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.actions.canViewPromptHistory,
        ) {
            Text("View prompt history")
        }

        if (state.isPromptHistoryVisible) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.promptHistory.forEach { entry ->
                    Text(
                        text = "${entry.operationType.wireValue}: ${entry.prompt}",
                        style = MaterialTheme.typography.bodySmall,
                    )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> EnumDropdown(
    label: String,
    selected: T,
    values: List<T>,
    display: (T) -> String,
    onSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
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
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            values.forEach { value ->
                DropdownMenuItem(
                    text = { Text(display(value)) },
                    onClick = {
                        onSelected(value)
                        expanded = false
                    },
                )
            }
        }
    }
}
