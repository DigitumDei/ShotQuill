package com.digitumdei.shotquill.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.digitumdei.shotquill.shared.domain.LogoSettings
import com.digitumdei.shotquill.shared.domain.PhotoTreatment
import com.digitumdei.shotquill.shared.domain.PixelSize
import com.digitumdei.shotquill.ui.CornerPicker
import com.digitumdei.shotquill.ui.IconLabel
import com.digitumdei.shotquill.ui.LabeledSlider
import com.digitumdei.shotquill.ui.Panel
import com.digitumdei.shotquill.ui.PhotoStage
import com.digitumdei.shotquill.ui.ScreenHeader
import com.digitumdei.shotquill.ui.SectionLabel
import com.digitumdei.shotquill.ui.ShapeChips
import com.digitumdei.shotquill.ui.ShotQuillIcons
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    /** A recent photo to try the look and logo on, when there is one. */
    previewUri: String?,
    previewSize: PixelSize?,
    onPickLogo: (() -> Unit)?,
    onBack: () -> Unit,
) {
    val state = viewModel.state
    val settings = state.settings
    val look = settings.look
    val logo = settings.logo

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        ScreenHeader(title = "Settings", onBack = onBack)

        // The preview stays put while the controls scroll beneath it, so every
        // slider can be judged against the photo.
        if (previewUri != null && previewSize != null) {
            PhotoStage(
                photoUri = previewUri,
                imageSize = previewSize,
                treatment = PhotoTreatment(cropShape = settings.defaultCropShape),
                look = look,
                logo = logo,
                logoSize = state.logoSize,
                heightLimit = 250.dp,
                modifier = Modifier.padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 12.dp),
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            if (previewUri == null || previewSize == null) {
                Text(
                    "Add a photo first and it will appear here, so you can see the look and logo as you set them.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SettingsGroup(
                label = "Look",
                detail = "One set of light and colour changes, applied the same way to every photo.",
            ) {
                LabeledSlider(
                    label = "Warmth",
                    value = look.warmth,
                    valueRange = -1f..1f,
                    onValueChange = { viewModel.setLook(look.copy(warmth = it.snapped())) },
                    valueText = look.warmth.signedPercent(),
                )
                LabeledSlider(
                    label = "Contrast",
                    value = look.contrast,
                    valueRange = -1f..1f,
                    onValueChange = { viewModel.setLook(look.copy(contrast = it.snapped())) },
                    valueText = look.contrast.signedPercent(),
                )
                LabeledSlider(
                    label = "Colour",
                    value = look.saturation,
                    valueRange = -1f..1f,
                    onValueChange = { viewModel.setLook(look.copy(saturation = it.snapped())) },
                    valueText = look.saturation.signedPercent(),
                )
                LabeledSlider(
                    label = "Fade",
                    value = look.fade,
                    valueRange = 0f..1f,
                    onValueChange = { viewModel.setLook(look.copy(fade = it.snapped())) },
                    valueText = "${(look.fade * 100).roundToInt()}",
                )
                TextButton(onClick = viewModel::resetLook) { Text("Back to the starting look") }
            }

            SettingsGroup(
                label = "Logo",
                detail = "A PNG with a transparent background works best.",
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                            .padding(10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (logo.uri != null) {
                            AsyncImage(
                                model = logo.uri,
                                contentDescription = "Your logo",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Text(
                                "None",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(onClick = { onPickLogo?.invoke() }, enabled = onPickLogo != null) {
                            Text(if (logo.uri == null) "Choose logo" else "Replace logo")
                        }
                        if (logo.uri != null) {
                            TextButton(onClick = { viewModel.setLogoUri(null) }) { Text("Remove") }
                        }
                    }
                }
                if (logo.uri != null) {
                    LabeledSlider(
                        label = "Size",
                        value = logo.widthFraction,
                        valueRange = LogoSettings.MinWidthFraction..LogoSettings.MaxWidthFraction,
                        onValueChange = { viewModel.setLogoWidth(it.snapped()) },
                        valueText = "${(logo.widthFraction * 100).roundToInt()}% of width",
                    )
                    LabeledSlider(
                        label = "Strength",
                        value = logo.opacity,
                        valueRange = LogoSettings.MinOpacity..1f,
                        onValueChange = { viewModel.setLogoOpacity(it.snapped()) },
                        valueText = "${(logo.opacity * 100).roundToInt()}%",
                    )
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Usual corner",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        CornerPicker(selected = logo.corner, onSelect = viewModel::setLogoCorner)
                    }
                }
            }

            SettingsGroup(
                label = "Usual shape",
                detail = "New posts start with this crop. You can change it on any post.",
            ) {
                ShapeChips(selected = settings.defaultCropShape, onSelect = viewModel::setDefaultShape)
            }

            SettingsGroup(
                label = "Caption snippets",
                detail = "Lines you reuse, such as hashtags or the under-18 notice. They appear under the caption box, one tap to add.",
            ) {
                settings.snippets.forEachIndexed { index, snippet ->
                    Panel {
                        Column(
                            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = snippet.name,
                                    onValueChange = { viewModel.updateSnippet(index, snippet.copy(name = it)) },
                                    label = { Text("Name") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { viewModel.removeSnippet(index) }) {
                                    Icon(ShotQuillIcons.Delete, contentDescription = "Remove snippet")
                                }
                            }
                            OutlinedTextField(
                                value = snippet.text,
                                onValueChange = { viewModel.updateSnippet(index, snippet.copy(text = it)) },
                                label = { Text("Text") },
                                minLines = 2,
                                modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                            )
                        }
                    }
                }
                OutlinedButton(onClick = viewModel::addSnippet) {
                    IconLabel(ShotQuillIcons.Add, "Add a snippet")
                }
            }

            if (state.hasLegacyApiKey) {
                SettingsGroup(
                    label = "Left over",
                    detail = "An OpenAI key saved by an earlier version is still stored on this phone. This version never uses it.",
                ) {
                    OutlinedButton(onClick = viewModel::clearLegacyApiKey) { Text("Delete the stored key") }
                }
            }

            Text(
                "ShotQuill prepares photos on this phone. Nothing is generated and nothing is uploaded.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SettingsGroup(
    label: String,
    detail: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionLabel(label)
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        content()
    }
}

/** Sliders report long fractions; two decimals is as fine as the eye can tell. */
private fun Float.snapped(): Float = (this * 100).roundToInt() / 100f

private fun Float.signedPercent(): String {
    val percent = (this * 100).roundToInt()
    return if (percent > 0) "+$percent" else "$percent"
}
