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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.digitumdei.shotquill.shared.domain.BrandProfileId
import com.digitumdei.shotquill.shared.domain.QualityTier
import com.digitumdei.shotquill.shared.domain.RealismLevel
import com.digitumdei.shotquill.shared.domain.TargetPlatform
import com.digitumdei.shotquill.shared.settings.InMemoryLocalSettingsRepository
import com.digitumdei.shotquill.shared.settings.LocalSettingsRepository
import com.digitumdei.shotquill.shared.settings.SecretRedactor
import kotlinx.coroutines.delay

@Composable
fun App(settingsRepository: LocalSettingsRepository? = null) {
    val repository = settingsRepository ?: remember { InMemoryLocalSettingsRepository() }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            SettingsScreen(repository = repository)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(repository: LocalSettingsRepository) {
    var settings by remember(repository) { mutableStateOf(repository.readSettings()) }
    val latestSettings by rememberUpdatedState(settings)
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
        Text(text = "ShotQuill Settings", style = MaterialTheme.typography.headlineSmall)
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

        var brandProfileIdInput by remember(settings.activeBrandProfileId) {
            mutableStateOf(settings.activeBrandProfileId?.value.orEmpty())
        }
        LaunchedEffect(repository, brandProfileIdInput) {
            delay(500)
            val activeBrandProfileId = brandProfileIdInput.trim()
                .takeIf { it.isNotEmpty() }
                ?.let(::BrandProfileId)
            if (activeBrandProfileId != latestSettings.activeBrandProfileId) {
                settings = latestSettings.copy(activeBrandProfileId = activeBrandProfileId)
                    .also(repository::saveSettings)
            }
        }
        OutlinedTextField(
            value = brandProfileIdInput,
            onValueChange = { value -> brandProfileIdInput = value },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Active brand profile ID") },
            singleLine = true,
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
