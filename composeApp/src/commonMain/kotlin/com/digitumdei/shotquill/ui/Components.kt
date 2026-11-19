package com.digitumdei.shotquill.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import coil3.compose.AsyncImage
import com.digitumdei.shotquill.shared.domain.CropShape
import com.digitumdei.shotquill.shared.domain.LogoCorner
import com.digitumdei.shotquill.shared.domain.LogoSettings
import com.digitumdei.shotquill.shared.domain.LookSettings
import com.digitumdei.shotquill.shared.domain.PhotoTreatment
import com.digitumdei.shotquill.shared.domain.PixelSize
import com.digitumdei.shotquill.shared.domain.cropRect
import com.digitumdei.shotquill.shared.domain.logoPlacement
import com.digitumdei.shotquill.shared.domain.toColorMatrix
import kotlin.math.roundToInt

@Composable
fun ScreenHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(ShotQuillIcons.Back, contentDescription = "Back")
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
        actions()
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = SectionLabelStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
fun ShapeChips(
    selected: CropShape,
    onSelect: (CropShape) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CropShape.entries.forEach { shape ->
            FilterChip(
                selected = shape == selected,
                onClick = { onSelect(shape) },
                label = { Text(shape.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

/** Four tappable corners of a small frame: the choice is shown the way it will look. */
@Composable
fun CornerPicker(
    selected: LogoCorner,
    onSelect: (LogoCorner) -> Unit,
    modifier: Modifier = Modifier,
) {
    val frameShape = RoundedCornerShape(10.dp)
    Column(
        modifier = modifier
            .size(width = 76.dp, height = 60.dp)
            .clip(frameShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, frameShape)
            .padding(5.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        listOf(
            listOf(LogoCorner.TopStart, LogoCorner.TopEnd),
            listOf(LogoCorner.BottomStart, LogoCorner.BottomEnd),
        ).forEach { rowCorners ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                rowCorners.forEach { corner ->
                    val isSelected = corner == selected
                    Box(
                        modifier = Modifier
                            .size(width = 28.dp, height = 22.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                },
                            )
                            .clickable(role = Role.RadioButton) { onSelect(corner) }
                            .semantics { contentDescription = corner.spokenName() },
                    )
                }
            }
        }
    }
}

private fun LogoCorner.spokenName(): String = when (this) {
    LogoCorner.TopStart -> "Logo top left"
    LogoCorner.TopEnd -> "Logo top right"
    LogoCorner.BottomStart -> "Logo bottom left"
    LogoCorner.BottomEnd -> "Logo bottom right"
}

@Composable
fun LabeledSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueText: String = "${(value * 100).roundToInt()}",
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                valueText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange)
    }
}

@Composable
fun IconLabel(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text)
    }
}

/**
 * Shows the photo exactly as it will be shared: cropped to the chosen shape,
 * graded with the look and stamped with the logo. The arithmetic is the same
 * as the exporter's, so what is seen here is what is written to the file.
 *
 * Pass [onPanZoom] to let the photo be dragged and pinched inside the frame.
 */
@Composable
fun PhotoStage(
    photoUri: String,
    imageSize: PixelSize,
    treatment: PhotoTreatment,
    look: LookSettings,
    logo: LogoSettings,
    logoSize: PixelSize?,
    modifier: Modifier = Modifier,
    heightLimit: Dp = 440.dp,
    onPanZoom: ((panXFraction: Float, panYFraction: Float, zoomChange: Float) -> Unit)? = null,
    /** Labels drawn over the photo, such as people tagged on it. */
    markers: List<PhotoMarker> = emptyList(),
    /** A tap on the photo, as fractions of the visible frame from its top-left corner. */
    onTap: ((xFraction: Float, yFraction: Float) -> Unit)? = null,
) {
    val crop = remember(treatment.cropShape, treatment.zoom, treatment.centerX, treatment.centerY, imageSize) {
        treatment.cropRect(imageSize.width, imageSize.height)
    }
    val aspect = crop.width / crop.height
    val colorFilter = remember(look, treatment.lookEnabled) {
        if (treatment.lookEnabled && !look.isNeutral) {
            ColorFilter.colorMatrix(ColorMatrix(look.toColorMatrix()))
        } else {
            null
        }
    }
    val currentOnPanZoom by rememberUpdatedState(onPanZoom)
    val currentOnTap by rememberUpdatedState(onTap)

    BoxWithConstraints(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val stageWidth = min(maxWidth, heightLimit * aspect)
        val stageHeight = stageWidth / aspect
        val density = LocalDensity.current
        val stageWidthPx = with(density) { stageWidth.toPx() }
        val stageHeightPx = with(density) { stageHeight.toPx() }
        val scale = stageWidthPx / crop.width

        val gesture = if (onPanZoom != null) {
            Modifier.pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    currentOnPanZoom?.invoke(pan.x / size.width, pan.y / size.height, zoom)
                }
            }
        } else {
            Modifier
        }

        Box(
            modifier = Modifier
                .size(stageWidth, stageHeight)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .then(gesture)
                .then(
                    if (onTap != null) {
                        Modifier.testTag(PhotoStageTag).pointerInput(Unit) {
                            detectTapGestures { at -> currentOnTap?.invoke(at.x / size.width, at.y / size.height) }
                        }
                    } else {
                        Modifier
                    },
                ),
        ) {
            Layout(
                content = {
                    AsyncImage(
                        model = photoUri,
                        contentDescription = "Photo preview",
                        contentScale = ContentScale.FillBounds,
                        colorFilter = colorFilter,
                    )
                },
                modifier = Modifier.fillMaxSize().clipToBounds(),
            ) { measurables, constraints ->
                val width = (imageSize.width * scale).roundToInt().coerceAtLeast(1)
                val height = (imageSize.height * scale).roundToInt().coerceAtLeast(1)
                val placeable = measurables.first().measure(Constraints.fixed(width, height))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(
                        x = (-crop.left * scale).roundToInt(),
                        y = (-crop.top * scale).roundToInt(),
                    )
                }
            }

            val logoUri = logo.uri
            if (treatment.logoEnabled && logoUri != null && logoSize != null && logoSize.height > 0) {
                val placement = logoPlacement(
                    canvasWidth = stageWidthPx,
                    canvasHeight = stageHeightPx,
                    logoAspect = logoSize.width.toFloat() / logoSize.height,
                    logo = logo,
                    corner = treatment.logoCorner ?: logo.corner,
                )
                AsyncImage(
                    model = logoUri,
                    contentDescription = "Logo",
                    contentScale = ContentScale.FillBounds,
                    alpha = logo.opacity,
                    modifier = Modifier
                        .offset { IntOffset(placement.left.roundToInt(), placement.top.roundToInt()) }
                        .size(
                            width = with(density) { placement.width.toDp() },
                            height = with(density) { placement.height.toDp() },
                        ),
                )
            }
            markers.forEach { marker ->
                Text(
                    text = marker.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (marker.selected) MaterialTheme.colorScheme.onPrimary else Color.White,
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (marker.x.coerceIn(0f, 1f) * stageWidthPx).roundToInt(),
                                (marker.y.coerceIn(0f, 1f) * stageHeightPx).roundToInt(),
                            )
                        }
                        .background(
                            color = if (marker.selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.6f),
                            shape = MaterialTheme.shapes.small,
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/** A label over the photo at [x], [y], fractions of the frame from its top-left corner. */
data class PhotoMarker(val label: String, val x: Float, val y: Float, val selected: Boolean = false)

/** Finds the photo in tests. */
const val PhotoStageTag = "photo-stage"

/** A quiet panel used to group related controls. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large),
    ) {
        content()
    }
}

@Composable
fun PanelDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}
