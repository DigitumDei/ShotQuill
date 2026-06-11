package com.digitumdei.shotquill

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.digitumdei.shotquill.model.MediaCaptureResult
import com.digitumdei.shotquill.clipboard.ClipboardWriter
import com.digitumdei.shotquill.screen.FinalPostComposerScreen
import com.digitumdei.shotquill.screen.ManualPostDraftWorkspaceScreen
import com.digitumdei.shotquill.screen.NewPostScreen
import com.digitumdei.shotquill.share.PostShareLauncher
import com.digitumdei.shotquill.shared.domain.BrandProfile
import com.digitumdei.shotquill.shared.domain.BrandProfileId
import com.digitumdei.shotquill.shared.domain.EpochClock
import com.digitumdei.shotquill.shared.domain.MediaAssetId
import com.digitumdei.shotquill.shared.domain.PostDraftId
import com.digitumdei.shotquill.shared.domain.PostFormat
import com.digitumdei.shotquill.shared.domain.QualityTier
import com.digitumdei.shotquill.shared.domain.RealismLevel
import com.digitumdei.shotquill.shared.domain.TargetPlatform
import com.digitumdei.shotquill.shared.settings.ActiveBrandProfileStore
import com.digitumdei.shotquill.shared.settings.InMemoryLocalSettingsRepository
import com.digitumdei.shotquill.shared.settings.LocalSettingsRepository
import com.digitumdei.shotquill.shared.settings.SecretRedactor
import com.digitumdei.shotquill.shared.storage.BrandProfileRepository
import com.digitumdei.shotquill.shared.storage.InMemoryBrandProfileRepository
import com.digitumdei.shotquill.shared.storage.ManualWorkflowRepository
import com.digitumdei.shotquill.shared.workflow.AnalyzeVision
import com.digitumdei.shotquill.shared.workflow.NewPostCreator
import com.digitumdei.shotquill.shared.workflow.PhotoEditExecutor
import com.digitumdei.shotquill.shared.workflow.PostTextGenerator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

internal enum class AppScreen {
    NewPost,
    DraftWorkspace,
    Settings,
    FinalComposer,
}

internal fun appScreenFromSaveable(value: String): AppScreen =
    AppScreen.entries.firstOrNull { it.name == value } ?: AppScreen.NewPost

internal fun finalComposerUnavailableMessage(
    hasManualWorkflowRepository: Boolean,
    hasDraftId: Boolean,
    hasClipboardWriter: Boolean,
    hasPostShareLauncher: Boolean,
): String = when {
    !hasManualWorkflowRepository -> "Draft repository not available"
    !hasDraftId -> "Draft ID not available"
    !hasClipboardWriter -> "Clipboard writer not available"
    !hasPostShareLauncher -> "Share launcher not available"
    else -> "Final composer dependencies not available"
}

@Composable
fun App(
    settingsRepository: LocalSettingsRepository? = null,
    brandProfileRepository: BrandProfileRepository? = null,
    manualWorkflowRepository: ManualWorkflowRepository? = null,
    postTextGenerator: PostTextGenerator? = null,
    photoEditExecutor: PhotoEditExecutor? = null,
    analyzeVision: AnalyzeVision? = null,
    onCaptureFromCamera: (() -> Unit)? = null,
    onPickFromGallery: (() -> Unit)? = null,
    captureResult: MediaCaptureResult? = null,
    captureError: String? = null,
    onClearCaptureResult: (() -> Unit)? = null,
    onClearCaptureError: (() -> Unit)? = null,
    onCleanupCapture: ((MediaCaptureResult) -> Unit)? = null,
    clipboardWriter: ClipboardWriter? = null,
    postShareLauncher: PostShareLauncher? = null,
) {
    val repository = settingsRepository ?: remember { InMemoryLocalSettingsRepository() }
    val profileRepository = brandProfileRepository ?: remember { InMemoryBrandProfileRepository() }
    var currentScreen by rememberSaveable { mutableStateOf(AppScreen.NewPost.name) }
    var currentDraftId by rememberSaveable { mutableStateOf<String?>(null) }

    val newPostCreator = remember(manualWorkflowRepository) {
        manualWorkflowRepository?.let { NewPostCreator(it) }
    }

    var draftCreatedMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var saveError by rememberSaveable { mutableStateOf<String?>(null) }
    var lastProcessedUri by rememberSaveable { mutableStateOf<String?>(null) }
    val captureResultValue = captureResult
    LaunchedEffect(captureResultValue) {
        if (captureResultValue == null) {
            draftCreatedMessage = null
            saveError = null
            return@LaunchedEffect
        }
        if (captureResultValue.uri == lastProcessedUri) return@LaunchedEffect
        draftCreatedMessage = null
        saveError = null
        if (newPostCreator != null) {
            val suffix = Random.nextInt(0, Int.MAX_VALUE)
            val draftId = PostDraftId("draft-${captureResultValue.createdAtEpochMillis}-${suffix}")
            val mediaAssetId = MediaAssetId("media-${captureResultValue.createdAtEpochMillis}-${suffix}")
            try {
                val draft = withContext(Dispatchers.IO) {
                    newPostCreator.createDraftFromMedia(
                        draftId = draftId,
                        mediaAssetId = mediaAssetId,
                        format = PostFormat.SingleImage,
                        uri = captureResultValue.uri,
                        mimeType = captureResultValue.mimeType,
                        widthPx = captureResultValue.widthPx,
                        heightPx = captureResultValue.heightPx,
                        createdAtEpochMillis = captureResultValue.createdAtEpochMillis,
                    )
                }
                draftCreatedMessage = "Draft ${draft.id.value} created"
                currentDraftId = draft.id.value
                currentScreen = AppScreen.DraftWorkspace.name
                lastProcessedUri = captureResultValue.uri
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                saveError = "Failed to create draft: ${e.message}"
            }
        } else {
            saveError = "Draft repository not available"
        }
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            when (appScreenFromSaveable(currentScreen)) {
                AppScreen.NewPost -> {
                    NewPostScreen(
                        onCaptureFromCamera = onCaptureFromCamera ?: {},
                        onPickFromGallery = onPickFromGallery ?: {},
                        onNavigateToSettings = { currentScreen = AppScreen.Settings.name },
                        captureResult = captureResult,
                        errorMessage = captureError ?: saveError,
                        draftCreatedMessage = draftCreatedMessage,
                        onDismissResult = {
                            onClearCaptureResult?.invoke()
                            draftCreatedMessage = null
                            saveError = null
                            lastProcessedUri = null
                        },
                        onDismissError = {
                            if (captureResult != null) {
                                onCleanupCapture?.invoke(captureResult)
                            }
                            onClearCaptureResult?.invoke()
                            onClearCaptureError?.invoke()
                            saveError = null
                            lastProcessedUri = null
                        },
                    )
                }

                AppScreen.DraftWorkspace -> {
                    val draftId = currentDraftId
                    if (manualWorkflowRepository != null && draftId != null) {
                        val settings = remember(repository, draftId) {
                            repository.readSettings()
                        }
                        ManualPostDraftWorkspaceScreen(
                            draftId = PostDraftId(draftId),
                            postDraftRepository = manualWorkflowRepository,
                            defaultTargetPlatform = settings.defaultTargetPlatform,
                            defaultRealismLevel = settings.defaultRealismLevel,
                            defaultQualityTier = settings.defaultQualityTier,
                            postTextGenerator = postTextGenerator,
                            photoEditExecutor = photoEditExecutor,
                            analyzeVision = analyzeVision,
                            onNavigateToNewPost = {
                                currentScreen = AppScreen.NewPost.name
                                currentDraftId = null
                                onClearCaptureResult?.invoke()
                                draftCreatedMessage = null
                                saveError = null
                                lastProcessedUri = null
                            },
                            onNavigateToFinalComposer = {
                                currentScreen = AppScreen.FinalComposer.name
                            },
                        )
                    } else {
                        NewPostScreen(
                            onCaptureFromCamera = onCaptureFromCamera ?: {},
                            onPickFromGallery = onPickFromGallery ?: {},
                            onNavigateToSettings = { currentScreen = AppScreen.Settings.name },
                            captureResult = null,
                            errorMessage = "Draft repository not available",
                            onDismissResult = {},
                            onDismissError = {
                                currentScreen = AppScreen.NewPost.name
                                currentDraftId = null
                            },
                        )
                    }
                }

                AppScreen.Settings -> {
                    SettingsScreen(
                        repository = repository,
                        brandProfileRepository = profileRepository,
                        onNavigateToNewPost = { currentScreen = AppScreen.NewPost.name },
                    )
                }

                AppScreen.FinalComposer -> {
                    val draftId = currentDraftId
                    if (manualWorkflowRepository != null && draftId != null && clipboardWriter != null && postShareLauncher != null) {
                        FinalPostComposerScreen(
                            draftId = PostDraftId(draftId),
                            repository = manualWorkflowRepository,
                            clipboardWriter = clipboardWriter,
                            postShareLauncher = postShareLauncher,
                            onBack = { currentScreen = AppScreen.DraftWorkspace.name },
                        )
                    } else {
                        NewPostScreen(
                            onCaptureFromCamera = onCaptureFromCamera ?: {},
                            onPickFromGallery = onPickFromGallery ?: {},
                            onNavigateToSettings = { currentScreen = AppScreen.Settings.name },
                            captureResult = null,
                            errorMessage = finalComposerUnavailableMessage(
                                hasManualWorkflowRepository = manualWorkflowRepository != null,
                                hasDraftId = draftId != null,
                                hasClipboardWriter = clipboardWriter != null,
                                hasPostShareLauncher = postShareLauncher != null,
                            ),
                            onDismissResult = {},
                            onDismissError = {
                                currentScreen = AppScreen.NewPost.name
                                currentDraftId = null
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    repository: LocalSettingsRepository,
    brandProfileRepository: BrandProfileRepository,
    onNavigateToNewPost: () -> Unit,
) {
    var settings by remember(repository) { mutableStateOf(repository.readSettings()) }
    val activeBrandProfileStore = remember(repository, brandProfileRepository) {
        ActiveBrandProfileStore(repository, brandProfileRepository)
    }
    var activeBrandProfile by remember(repository, brandProfileRepository) {
        mutableStateOf<BrandProfile?>(null)
    }
    LaunchedEffect(activeBrandProfileStore) {
        activeBrandProfile = activeBrandProfileStore.readActiveBrandProfile()
    }
    var apiKeyInput by remember(repository) { mutableStateOf("") }
    var hasApiKey by remember(repository) { mutableStateOf(repository.hasOpenAiApiKey()) }
    var status by remember(repository) {
        mutableStateOf(
            if (hasApiKey) {
                "OpenAI key configured"
            } else {
                "OpenAI key missing"
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "ShotQuill Settings", style = MaterialTheme.typography.headlineSmall)
            OutlinedButton(onClick = onNavigateToNewPost) {
                Text("New Post")
            }
        }

        Text(text = status, style = MaterialTheme.typography.bodyMedium)

        OutlinedTextField(
            value = apiKeyInput,
            onValueChange = { apiKeyInput = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("OpenAI API key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    runCatching {
                        repository.saveOpenAiApiKey(apiKeyInput)
                    }.onSuccess {
                        hasApiKey = true
                        status = "OpenAI key saved (${SecretRedactor.maskOpenAiApiKey(apiKeyInput.trim())})"
                        apiKeyInput = ""
                    }.onFailure {
                        status = SecretRedactor.redactKnownSecrets(it.message ?: "Unable to save OpenAI key", listOf(apiKeyInput))
                    }
                },
                enabled = apiKeyInput.isNotBlank(),
            ) {
                Text("Save key")
            }
            OutlinedButton(
                onClick = {
                    repository.clearOpenAiApiKey()
                    apiKeyInput = ""
                    hasApiKey = false
                    status = "OpenAI key cleared"
                },
                enabled = hasApiKey,
            ) {
                Text("Clear key")
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        EnumDropdown(
            label = "Default platform",
            selected = settings.defaultTargetPlatform,
            values = TargetPlatform.entries,
            display = { it.wireValue },
            onSelected = { selected ->
                settings = settings.copy(defaultTargetPlatform = selected).also(repository::saveSettings)
            },
        )
        EnumDropdown(
            label = "Quality tier",
            selected = settings.defaultQualityTier,
            values = QualityTier.entries,
            display = { it.wireValue },
            onSelected = { selected ->
                settings = settings.copy(defaultQualityTier = selected).also(repository::saveSettings)
            },
        )
        EnumDropdown(
            label = "Realism level",
            selected = settings.defaultRealismLevel,
            values = RealismLevel.entries,
            display = { it.wireValue },
            onSelected = { selected ->
                settings = settings.copy(defaultRealismLevel = selected).also(repository::saveSettings)
            },
        )

        BrandProfileEditor(
            activeBrandProfile = activeBrandProfile,
            onSave = { profile ->
                activeBrandProfileStore.saveActiveBrandProfile(profile)
                activeBrandProfile = profile
                settings = repository.readSettings()
                status = "Active brand profile saved"
            },
            onClear = {
                activeBrandProfileStore.clearActiveBrandProfile()
                activeBrandProfile = null
                settings = repository.readSettings()
                status = "No active brand profile"
            },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Prompt history")
            Switch(
                checked = settings.promptHistoryEnabled,
                onCheckedChange = { enabled ->
                    settings = settings.copy(promptHistoryEnabled = enabled).also(repository::saveSettings)
                },
            )
        }
    }
}

@Composable
private fun BrandProfileEditor(
    activeBrandProfile: BrandProfile?,
    onSave: (BrandProfile) -> Unit,
    onClear: () -> Unit,
) {
    var brandName by remember(activeBrandProfile) { mutableStateOf(activeBrandProfile?.displayName.orEmpty()) }
    var shortDescription by remember(activeBrandProfile) { mutableStateOf(activeBrandProfile?.audience.orEmpty()) }
    var defaultTone by remember(activeBrandProfile) { mutableStateOf(activeBrandProfile?.voice.orEmpty()) }
    var defaultHashtags by remember(activeBrandProfile) {
        mutableStateOf(activeBrandProfile?.defaultHashtags.orEmpty().joinToString(" "))
    }
    var websiteOrSocialLinks by remember(activeBrandProfile) {
        mutableStateOf(activeBrandProfile?.websiteOrSocialLinks.orEmpty().joinToString("\n"))
    }
    var visualStyleNotes by remember(activeBrandProfile) { mutableStateOf(activeBrandProfile?.visualStyleNotes.orEmpty()) }
    var productNamingNotes by remember(activeBrandProfile) { mutableStateOf(activeBrandProfile?.productNamingNotes.orEmpty()) }
    var validationMessage by remember(activeBrandProfile) {
        mutableStateOf(
            if (activeBrandProfile == null) {
                "No active brand profile configured"
            } else {
                "Active brand profile: ${activeBrandProfile.displayName}"
            },
        )
    }

    Text(text = "Brand Profile", style = MaterialTheme.typography.titleMedium)
    Text(text = validationMessage, style = MaterialTheme.typography.bodyMedium)
    OutlinedTextField(
        value = brandName,
        onValueChange = { brandName = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Brand name") },
        singleLine = true,
    )
    OutlinedTextField(
        value = shortDescription,
        onValueChange = { shortDescription = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Short description") },
        minLines = 2,
    )
    OutlinedTextField(
        value = defaultTone,
        onValueChange = { defaultTone = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Default tone") },
        singleLine = true,
    )
    OutlinedTextField(
        value = defaultHashtags,
        onValueChange = { defaultHashtags = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Default hashtags") },
        singleLine = true,
    )
    OutlinedTextField(
        value = websiteOrSocialLinks,
        onValueChange = { websiteOrSocialLinks = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Website/social links") },
        minLines = 2,
    )
    OutlinedTextField(
        value = visualStyleNotes,
        onValueChange = { visualStyleNotes = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Visual style notes") },
        minLines = 2,
    )
    OutlinedTextField(
        value = productNamingNotes,
        onValueChange = { productNamingNotes = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Product or beer naming notes") },
        minLines = 2,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = {
                val now = EpochClock.Default.nowMillis()
                runCatching {
                    BrandProfile(
                        id = activeBrandProfile?.id ?: BrandProfileId("active-brand-profile"),
                        displayName = brandName.trim(),
                        voice = defaultTone.trim(),
                        audience = shortDescription.trim().takeIf { it.isNotEmpty() },
                        defaultHashtags = defaultHashtags.splitProfileTokens(),
                        websiteOrSocialLinks = websiteOrSocialLinks.splitProfileLines(),
                        visualStyleNotes = visualStyleNotes.trim().takeIf { it.isNotEmpty() },
                        productNamingNotes = productNamingNotes.trim().takeIf { it.isNotEmpty() },
                        imageAssets = activeBrandProfile?.imageAssets.orEmpty(),
                        createdAtEpochMillis = activeBrandProfile?.createdAtEpochMillis ?: now,
                        updatedAtEpochMillis = now,
                    )
                }.onSuccess { profile ->
                    onSave(profile)
                    validationMessage = "Active brand profile: ${profile.displayName}"
                }.onFailure {
                    validationMessage = it.message ?: "Unable to save brand profile"
                }
            },
            enabled = brandName.isNotBlank() && defaultTone.isNotBlank(),
        ) {
            Text("Save profile")
        }
        OutlinedButton(
            onClick = {
                onClear()
                validationMessage = "No active brand profile configured"
            },
            enabled = activeBrandProfile != null,
        ) {
            Text("Clear active")
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

private fun String.splitProfileTokens(): List<String> =
    split(",", " ", "\n")
        .map { it.trim() }
        .filter { it.isNotEmpty() }

private fun String.splitProfileLines(): List<String> =
    lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
