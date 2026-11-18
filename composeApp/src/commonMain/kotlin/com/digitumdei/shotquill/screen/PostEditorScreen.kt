package com.digitumdei.shotquill.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.digitumdei.shotquill.clipboard.ClipboardWriter
import com.digitumdei.shotquill.image.PhotoRenderer
import com.digitumdei.shotquill.share.PostShareLauncher
import com.digitumdei.shotquill.shared.domain.CaptionSnippet
import com.digitumdei.shotquill.shared.domain.CaptionStats
import com.digitumdei.shotquill.shared.domain.CropShape
import com.digitumdei.shotquill.shared.domain.LogoCorner
import com.digitumdei.shotquill.shared.domain.PostDraftId
import com.digitumdei.shotquill.shared.post.PostEditorStore
import com.digitumdei.shotquill.shared.settings.LocalSettingsRepository
import com.digitumdei.shotquill.speech.DictationController
import com.digitumdei.shotquill.speech.DictationState
import com.digitumdei.shotquill.ui.CornerPicker
import com.digitumdei.shotquill.ui.Panel
import com.digitumdei.shotquill.ui.PanelDivider
import com.digitumdei.shotquill.ui.PhotoStage
import com.digitumdei.shotquill.ui.ScreenHeader
import com.digitumdei.shotquill.ui.SectionLabel
import com.digitumdei.shotquill.ui.ShapeChips
import com.digitumdei.shotquill.ui.ShotQuillIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Composable
fun PostEditorScreen(
    draftId: PostDraftId,
    store: PostEditorStore,
    settingsRepository: LocalSettingsRepository,
    renderer: PhotoRenderer,
    clipboardWriter: ClipboardWriter,
    shareLauncher: PostShareLauncher,
    dictation: DictationController?,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel = remember(draftId, store, settingsRepository, renderer, clipboardWriter, shareLauncher) {
        PostEditorViewModel(
            draftId = draftId,
            store = store,
            settingsRepository = settingsRepository,
            renderer = renderer,
            clipboardWriter = clipboardWriter,
            shareLauncher = shareLauncher,
        )
    }
    val state = viewModel.state
    val scope = rememberCoroutineScope()
    val writeLock = remember(viewModel) { Mutex() }

    LaunchedEffect(viewModel) {
        writeLock.withLock { withContext(Dispatchers.IO) { viewModel.load() } }
    }

    // Typing and dragging change the screen at once and are written a moment
    // after the last change, so nothing is saved on every keystroke.
    LaunchedEffect(viewModel, state.caption) {
        delay(600)
        writeLock.withLock { withContext(Dispatchers.IO) { viewModel.persistCaption() } }
    }
    LaunchedEffect(viewModel, state.treatment) {
        delay(400)
        writeLock.withLock { withContext(Dispatchers.IO) { viewModel.persistTreatment() } }
    }
    DisposableEffect(viewModel) {
        onDispose {
            viewModel.persistCaption()
            viewModel.persistTreatment()
        }
    }
    DisposableEffect(dictation) {
        onDispose { dictation?.stop() }
    }

    LaunchedEffect(state.isMissing, state.isLoaded) {
        if (state.isMissing && !state.isLoaded && state.message == null) onBack()
    }

    PostEditorContent(
        state = state,
        onBack = onBack,
        onOpenSettings = onOpenSettings,
        dictationAvailable = dictation?.isAvailable == true,
        dictationState = dictation?.state ?: DictationState.Idle,
        onToggleDictation = {
            if (dictation != null) {
                if (dictation.state is DictationState.Listening) {
                    dictation.stop()
                } else {
                    dictation.start(viewModel::addDictation)
                }
            }
        },
        onCaptionChange = viewModel::updateCaption,
        onAddSnippet = viewModel::addSnippet,
        onSelectShape = viewModel::selectShape,
        onPanZoom = viewModel::panZoom,
        onLookEnabled = viewModel::setLookEnabled,
        onLogoEnabled = viewModel::setLogoEnabled,
        onLogoCorner = viewModel::setLogoCorner,
        onDismissMessage = viewModel::dismissMessage,
        onShare = {
            scope.launch {
                writeLock.withLock { withContext(Dispatchers.IO) { viewModel.share() } }
            }
        },
        onRemove = {
            scope.launch {
                writeLock.withLock { withContext(Dispatchers.IO) { viewModel.remove() } }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PostEditorContent(
    state: PostEditorState,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    dictationAvailable: Boolean,
    dictationState: DictationState,
    onToggleDictation: () -> Unit,
    onCaptionChange: (String) -> Unit,
    onAddSnippet: (CaptionSnippet) -> Unit,
    onSelectShape: (CropShape) -> Unit,
    onPanZoom: (Float, Float, Float) -> Unit,
    onLookEnabled: (Boolean) -> Unit,
    onLogoEnabled: (Boolean) -> Unit,
    onLogoCorner: (LogoCorner) -> Unit,
    onDismissMessage: () -> Unit,
    onShare: () -> Unit,
    onRemove: () -> Unit,
) {
    var confirmRemove by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        ScreenHeader(
            title = if (state.shared) "Shared post" else "New post",
            onBack = onBack,
        ) {
            IconButton(onClick = { confirmRemove = true }, enabled = state.isLoaded) {
                Icon(ShotQuillIcons.Delete, contentDescription = "Remove post")
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            val photoUri = state.originalUri
            val imageSize = state.imageSize
            if (photoUri != null && imageSize != null) {
                PhotoStage(
                    photoUri = photoUri,
                    imageSize = imageSize,
                    treatment = state.treatment,
                    look = state.look,
                    logo = state.logo,
                    logoSize = state.logoSize,
                    onPanZoom = onPanZoom,
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ShapeChips(selected = state.treatment.cropShape, onSelect = onSelectShape)
                    Text(
                        "Drag the photo to frame it. Pinch to zoom.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!state.isLoaded && !state.isMissing) {
                        CircularProgressIndicator()
                    }
                }
            }

            Panel {
                Column {
                    ToggleRow(
                        title = "Look",
                        detail = "Your usual light and colour",
                        checked = state.treatment.lookEnabled,
                        enabled = state.isLoaded,
                        onCheckedChange = onLookEnabled,
                    )
                    PanelDivider()
                    if (state.hasLogo) {
                        ToggleRow(
                            title = "Logo",
                            detail = "Stamped in the corner you pick",
                            checked = state.treatment.logoEnabled,
                            enabled = state.isLoaded,
                            onCheckedChange = onLogoEnabled,
                        )
                        if (state.treatment.logoEnabled) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Corner",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                CornerPicker(selected = state.logoCorner, onSelect = onLogoCorner)
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Logo", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "No logo chosen yet",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = onOpenSettings) { Text("Add in settings") }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val listening = dictationState is DictationState.Listening
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Caption", modifier = Modifier.weight(1f))
                    if (dictationAvailable) {
                        FilledTonalButton(
                            onClick = onToggleDictation,
                            enabled = state.isLoaded,
                            colors = if (listening) {
                                ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                ButtonDefaults.filledTonalButtonColors()
                            },
                        ) {
                            Icon(
                                if (listening) ShotQuillIcons.Stop else ShotQuillIcons.Mic,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(if (listening) "Stop" else "Dictate")
                        }
                    }
                }
                when (dictationState) {
                    is DictationState.Listening -> Text(
                        text = if (dictationState.heard.isBlank()) {
                            "Listening. Say your caption."
                        } else {
                            dictationState.heard
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    is DictationState.Failed -> Text(
                        text = dictationState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )

                    DictationState.Idle -> Unit
                }
                OutlinedTextField(
                    value = state.caption,
                    onValueChange = onCaptionChange,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.isLoaded,
                    minLines = 5,
                    placeholder = { Text("Say it in your own words") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    shape = MaterialTheme.shapes.medium,
                )
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    FlowRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.snippets.filter { it.text.isNotBlank() }.forEach { snippet ->
                            AssistChip(
                                onClick = { onAddSnippet(snippet) },
                                label = { Text(snippet.name.ifBlank { snippet.text.take(18) }) },
                                leadingIcon = {
                                    Icon(ShotQuillIcons.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    CaptionCounter(state.captionStats, modifier = Modifier.padding(top = 14.dp))
                }
            }
        }

        if (state.message != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(state.message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismissMessage) { Text("OK") }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Button(
                onClick = onShare,
                enabled = state.canShare,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = MaterialTheme.shapes.large,
            ) {
                if (state.isSharing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("Preparing image", style = MaterialTheme.typography.titleMedium)
                } else {
                    Icon(ShotQuillIcons.Share, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(if (state.shared) "Share again" else "Share", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove this post?") },
            text = { Text("It disappears from your list. The original photo stays in the app's storage.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemove = false
                        onRemove()
                    },
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text("Keep") }
            },
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    detail: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun CaptionCounter(stats: CaptionStats, modifier: Modifier = Modifier) {
    val over = stats.overCharacterLimit || stats.overHashtagLimit
    val tags = if (stats.hashtags == 1) "1 tag" else "${stats.hashtags} tags"
    Text(
        text = "${stats.characters} / ${CaptionStats.InstagramCaptionLimit}  ·  $tags",
        style = MaterialTheme.typography.bodySmall,
        color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
