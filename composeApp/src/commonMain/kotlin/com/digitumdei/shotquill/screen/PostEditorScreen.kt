package com.digitumdei.shotquill.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.digitumdei.shotquill.clipboard.ClipboardWriter
import com.digitumdei.shotquill.image.PhotoRenderer
import com.digitumdei.shotquill.share.PostShareLauncher
import com.digitumdei.shotquill.shared.ai.AltTextWriter
import com.digitumdei.shotquill.shared.domain.CaptionSnippet
import com.digitumdei.shotquill.shared.domain.CaptionStats
import com.digitumdei.shotquill.shared.domain.CropShape
import com.digitumdei.shotquill.shared.domain.LogoCorner
import com.digitumdei.shotquill.shared.domain.PostDraftId
import com.digitumdei.shotquill.shared.post.PostEditorStore
import com.digitumdei.shotquill.shared.settings.LocalSettingsRepository
import com.digitumdei.shotquill.shared.social.AccountSuggestion
import com.digitumdei.shotquill.shared.social.BlueskyTextLimit
import com.digitumdei.shotquill.shared.social.MentionCheck
import com.digitumdei.shotquill.shared.social.MentionStatus
import com.digitumdei.shotquill.shared.social.PostPublisher
import com.digitumdei.shotquill.shared.social.PublishTarget
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Serialises reading and writing of posts across editor screens and the post
 * list. Network calls never hold it.
 */
internal val editorStorageLock = Mutex()

/** Outlives any one editor screen, for the save that happens as a screen is left. */
private val editorFlushScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

@Composable
fun PostEditorScreen(
    draftId: PostDraftId,
    store: PostEditorStore,
    settingsRepository: LocalSettingsRepository,
    renderer: PhotoRenderer,
    clipboardWriter: ClipboardWriter,
    shareLauncher: PostShareLauncher,
    dictation: DictationController?,
    altTextWriter: AltTextWriter?,
    publishers: List<PostPublisher>,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel = remember(
        draftId, store, settingsRepository, renderer, clipboardWriter, shareLauncher, altTextWriter, publishers,
    ) {
        PostEditorViewModel(
            draftId = draftId,
            store = store,
            settingsRepository = settingsRepository,
            renderer = renderer,
            clipboardWriter = clipboardWriter,
            shareLauncher = shareLauncher,
            altTextWriter = altTextWriter,
            publishers = publishers,
        )
    }
    val state = viewModel.state
    val scope = rememberCoroutineScope()
    // One lock for every editor, so a post that is reopened waits for the
    // last screen's saving to finish before it reads.
    val writeLock = editorStorageLock

    LaunchedEffect(viewModel) {
        writeLock.withLock { withContext(Dispatchers.IO) { viewModel.load() } }
    }

    // Typing and dragging change the screen at once and are written a moment
    // after the last change, so nothing is saved on every keystroke.
    LaunchedEffect(viewModel, state.caption) {
        delay(600)
        writeLock.withLock { withContext(Dispatchers.IO) { viewModel.persistCaption() } }
    }
    LaunchedEffect(viewModel, state.altText) {
        delay(600)
        writeLock.withLock { withContext(Dispatchers.IO) { viewModel.persistAltText() } }
    }
    LaunchedEffect(viewModel, state.blueskyText) {
        delay(600)
        writeLock.withLock { withContext(Dispatchers.IO) { viewModel.persistBlueskyText() } }
    }
    LaunchedEffect(viewModel, state.tags) {
        delay(600)
        writeLock.withLock { withContext(Dispatchers.IO) { viewModel.persistTags() } }
    }
    LaunchedEffect(viewModel, state.treatment) {
        delay(400)
        writeLock.withLock { withContext(Dispatchers.IO) { viewModel.persistTreatment() } }
    }
    // Accounts mentioned for Bluesky are looked up once typing pauses. The
    // lookups wait on the network, so they have a lock of their own and never
    // hold up saving.
    val lookupLock = remember(viewModel) { Mutex() }
    LaunchedEffect(viewModel, state.isLoaded, state.blueskyPostText, state.instagramPostText) {
        delay(900)
        lookupLock.withLock { withContext(Dispatchers.IO) { viewModel.checkMentions() } }
    }
    // Suggestions for a name still being typed are wanted sooner than a check
    // of a finished one.
    LaunchedEffect(viewModel, state.isLoaded, state.blueskyText, state.tagsFor(PublishTarget.Bluesky)) {
        delay(350)
        lookupLock.withLock { withContext(Dispatchers.IO) { viewModel.fetchSuggestions() } }
    }
    LaunchedEffect(viewModel, state.isLoaded) {
        if (state.isLoaded) lookupLock.withLock { withContext(Dispatchers.IO) { viewModel.loadMentionHistory() } }
    }
    val uriHandler = LocalUriHandler.current
    DisposableEffect(viewModel) {
        onDispose {
            // Whatever was typed in the last moment is still saved, but not on
            // the main thread and not by this screen's own scope, which ends
            // with the screen.
            editorFlushScope.launch { editorStorageLock.withLock { viewModel.persistAll() } }
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
        instagramMentions = viewModel.mentionChecks(PublishTarget.Instagram),
        blueskyMentions = viewModel.mentionChecks(PublishTarget.Bluesky),
        suggestionsFor = viewModel::suggestionsFor,
        onApplySuggestion = viewModel::applySuggestion,
        onOpenLink = { address ->
            try {
                uriHandler.openUri(address)
            } catch (_: Exception) {
                // Nothing on the phone can open a web address; the name is still shown.
            }
        },
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
        onAltTextChange = viewModel::updateAltText,
        onBlueskyTextChange = viewModel::updateBlueskyText,
        onTagsChange = viewModel::updateTags,
        onPublish = { target ->
            // Posting waits on the network, so it does not hold the storage
            // lock: saving carries on while a post is on its way.
            scope.launch { withContext(Dispatchers.IO) { viewModel.publish(target) } }
        },
        onSelectAltTextModel = viewModel::selectAltTextModel,
        onCopyAltText = viewModel::copyAltText,
        onWriteAltText = {
            scope.launch { withContext(Dispatchers.IO) { viewModel.writeAltText() } }
        },
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
    instagramMentions: List<MentionCheck>,
    blueskyMentions: List<MentionCheck>,
    /** Accounts to offer under a box while a mention is being typed in it. */
    suggestionsFor: (MentionField) -> List<AccountSuggestion>,
    onApplySuggestion: (MentionField, AccountSuggestion) -> Unit,
    onOpenLink: (String) -> Unit,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    dictationAvailable: Boolean,
    dictationState: DictationState,
    onToggleDictation: () -> Unit,
    onCaptionChange: (String) -> Unit,
    onAltTextChange: (String) -> Unit,
    onBlueskyTextChange: (String) -> Unit,
    onTagsChange: (PublishTarget, String) -> Unit,
    onPublish: (PublishTarget) -> Unit,
    onSelectAltTextModel: (String) -> Unit,
    onCopyAltText: () -> Unit,
    onWriteAltText: () -> Unit,
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
    var confirmPublish by remember { mutableStateOf<PublishTarget?>(null) }

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
                MentionSuggestions(suggestionsFor(MentionField.Caption)) { onApplySuggestion(MentionField.Caption, it) }
                OutlinedTextField(
                    value = state.tagsFor(PublishTarget.Instagram),
                    onValueChange = { onTagsChange(PublishTarget.Instagram, it) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.isLoaded,
                    label = { Text("Instagram hashtags") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                    shape = MaterialTheme.shapes.medium,
                )
                MentionSuggestions(suggestionsFor(MentionField.InstagramTags)) {
                    onApplySuggestion(MentionField.InstagramTags, it)
                }
                MentionLines(instagramMentions, onOpenLink)
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

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Alt text", modifier = Modifier.weight(1f))
                    if (state.aiConfigured) {
                        FilledTonalButton(onClick = onWriteAltText, enabled = state.canWriteAltText) {
                            if (state.isWritingAltText) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Describing")
                            } else {
                                Text(if (state.altText.isBlank()) "Describe with AI" else "Describe again")
                            }
                        }
                    }
                }
                if (state.aiConfigured && state.altTextModels.size > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.altTextModels.forEach { model ->
                            FilterChip(
                                selected = model == state.altTextModel,
                                onClick = { onSelectAltTextModel(model) },
                                enabled = !state.isWritingAltText,
                                label = { Text(model.substringAfterLast('/')) },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = state.altText,
                    onValueChange = onAltTextChange,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.isLoaded && !state.isWritingAltText,
                    minLines = 2,
                    placeholder = { Text("What the photo shows, for people who cannot see it") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    shape = MaterialTheme.shapes.medium,
                )
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (state.aiConfigured) {
                        Text(
                            "${state.altText.length} characters",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        TextButton(onClick = onOpenSettings, modifier = Modifier.weight(1f, fill = false)) {
                            Text("Set up AI to write this for you")
                        }
                        Spacer(Modifier.weight(1f))
                    }
                    TextButton(onClick = onCopyAltText, enabled = state.altText.isNotBlank()) { Text("Copy") }
                }
            }

            if (state.hasBluesky) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel("Bluesky text")
                    OutlinedTextField(
                        value = state.blueskyText,
                        onValueChange = onBlueskyTextChange,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.isLoaded && !state.isPublishing,
                        minLines = 2,
                        placeholder = { Text("Leave empty to post the caption as it is") },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        shape = MaterialTheme.shapes.medium,
                    )
                    MentionSuggestions(suggestionsFor(MentionField.BlueskyText)) {
                        onApplySuggestion(MentionField.BlueskyText, it)
                    }
                    OutlinedTextField(
                        value = state.tagsFor(PublishTarget.Bluesky),
                        onValueChange = { onTagsChange(PublishTarget.Bluesky, it) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.isLoaded && !state.isPublishing,
                        label = { Text("Bluesky hashtags") },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                        shape = MaterialTheme.shapes.medium,
                    )
                    MentionSuggestions(suggestionsFor(MentionField.BlueskyTags)) {
                        onApplySuggestion(MentionField.BlueskyTags, it)
                    }
                    Text(
                        text = "${state.blueskyLength} / $BlueskyTextLimit" +
                            if (state.blueskyText.isBlank()) "  ·  using the caption" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.blueskyTooLong) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    MentionLines(blueskyMentions, onOpenLink)
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onShare,
                enabled = state.canShare,
                modifier = Modifier.weight(1f).height(54.dp),
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
            state.targets.forEach { target ->
                FilledTonalButton(
                    onClick = { confirmPublish = target.target },
                    enabled = state.canShare,
                    modifier = Modifier.weight(1f).height(54.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    if (target.isPublishing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Posting", style = MaterialTheme.typography.titleMedium)
                    } else {
                        if (target.postedUrl != null) {
                            Icon(ShotQuillIcons.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(target.target.label, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }

    confirmPublish?.let { target ->
        val targetState = state.targets.firstOrNull { it.target == target }
        AlertDialog(
            onDismissRequest = { confirmPublish = null },
            title = { Text("Post to ${target.label}?") },
            text = {
                Text(
                    buildString {
                        append("This goes out publicly as ${targetState?.account.orEmpty()}.")
                        if (targetState?.postedUrl != null) {
                            append(" It has already been posted there once, so this would make a second post.")
                        }
                        if (state.altText.isBlank()) append(" There is no alt text yet.")
                        val doubtful = (if (target == PublishTarget.Bluesky) blueskyMentions else instagramMentions)
                            .filter { it.needsAttention }
                        if (doubtful.isNotEmpty()) {
                            append(" Look at ")
                            append(doubtful.joinToString(" and ") { "@${it.name}" })
                            append(" first: as written it will not link to the account you mean.")
                        }
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmPublish = null
                        onPublish(target)
                    },
                ) { Text("Post") }
            },
            dismissButton = {
                TextButton(onClick = { confirmPublish = null }) { Text("Not yet") }
            },
        )
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove this post?") },
            text = { Text("It disappears from your list. Its original photo stays on the phone until you clear " +
                        "removed posts in Settings.") },
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

/** Accounts offered while a mention is being typed. Tapping one finishes the mention with it. */
@Composable
private fun MentionSuggestions(suggestions: List<AccountSuggestion>, onPick: (AccountSuggestion) -> Unit) {
    if (suggestions.isEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        suggestions.forEach { suggestion ->
            AssistChip(
                onClick = { onPick(suggestion) },
                label = {
                    Text(
                        "@${suggestion.handle}" +
                            suggestion.displayName.trim().let { if (it.isEmpty()) "" else "  \u00B7  $it" },
                    )
                },
            )
        }
    }
}

/**
 * One line for each account mentioned: whether it was found, or what is wrong
 * with it. A line with a profile behind it opens that profile when tapped.
 */
@Composable
private fun MentionLines(checks: List<MentionCheck>, onOpenLink: (String) -> Unit) {
    if (checks.isEmpty()) return
    Column {
        checks.forEach { check ->
            val nameColor = when {
                check.needsAttention -> MaterialTheme.colorScheme.error
                check.status == MentionStatus.Found -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            }
            val address = check.profileUrl
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = nameColor, fontWeight = FontWeight.SemiBold)) {
                        if (check.status == MentionStatus.Found) append("\u2713 ")
                        append("@${check.name}")
                    }
                    append("  \u00B7  ${check.note}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (check.needsAttention) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .then(
                        if (address != null) {
                            Modifier.clickable(role = Role.Button, onClickLabel = "Open the profile") {
                                onOpenLink(address)
                            }
                        } else {
                            Modifier
                        },
                    )
                    .padding(vertical = 6.dp),
            )
        }
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
