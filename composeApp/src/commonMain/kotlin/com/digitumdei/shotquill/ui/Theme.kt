package com.digitumdei.shotquill.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/*
 * The palette is ink, parchment and amber: a dark room for looking at
 * photographs, with one warm accent. Light mode swaps to parchment paper with
 * ink text and a deeper amber so the accent keeps its contrast.
 */
private val Ink = Color(0xFF12100E)
private val InkRaised = Color(0xFF1B1815)
private val InkPanel = Color(0xFF24201C)
private val InkPanelHigh = Color(0xFF2E2924)
private val InkLine = Color(0xFF3A342E)
private val InkLineStrong = Color(0xFF5E5448)
private val Parchment = Color(0xFFF1E9DC)
private val ParchmentMuted = Color(0xFFB5AA9A)
private val Amber = Color(0xFFE9B44C)
private val AmberDeep = Color(0xFF8A5A00)
private val Hop = Color(0xFFA9C58E)
private val HopDeep = Color(0xFF4C6A34)

private val DarkColors = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF2A1D00),
    primaryContainer = Color(0xFF4A3610),
    onPrimaryContainer = Color(0xFFFFDF9E),
    secondary = Color(0xFFD3C7B0),
    onSecondary = Color(0xFF2B2519),
    secondaryContainer = InkPanelHigh,
    onSecondaryContainer = Parchment,
    tertiary = Hop,
    onTertiary = Color(0xFF1C2C10),
    tertiaryContainer = Color(0xFF2F4220),
    onTertiaryContainer = Color(0xFFD3E8BC),
    background = Ink,
    onBackground = Parchment,
    surface = Ink,
    onSurface = Parchment,
    surfaceVariant = InkPanel,
    onSurfaceVariant = ParchmentMuted,
    surfaceContainerLowest = Color(0xFF0C0B09),
    surfaceContainerLow = InkRaised,
    surfaceContainer = InkPanel,
    surfaceContainerHigh = InkPanelHigh,
    surfaceContainerHighest = Color(0xFF38322C),
    outline = InkLineStrong,
    outlineVariant = InkLine,
    error = Color(0xFFFFB4A9),
    onError = Color(0xFF5C1109),
    errorContainer = Color(0xFF7A2318),
    onErrorContainer = Color(0xFFFFDAD4),
)

private val LightColors = lightColorScheme(
    primary = AmberDeep,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDEA6),
    onPrimaryContainer = Color(0xFF2B1A00),
    secondary = Color(0xFF6B5D45),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEADFC8),
    onSecondaryContainer = Color(0xFF241B0A),
    tertiary = HopDeep,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD3E8BC),
    onTertiaryContainer = Color(0xFF10200A),
    background = Color(0xFFF7F1E5),
    onBackground = Color(0xFF1D1A16),
    surface = Color(0xFFF7F1E5),
    onSurface = Color(0xFF1D1A16),
    surfaceVariant = Color(0xFFEDE4D3),
    onSurfaceVariant = Color(0xFF5C5346),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBF7EE),
    surfaceContainer = Color(0xFFF1E9DA),
    surfaceContainerHigh = Color(0xFFEAE1D0),
    surfaceContainerHighest = Color(0xFFE2D8C5),
    outline = Color(0xFF8A7F70),
    outlineVariant = Color(0xFFD9CFBD),
    error = Color(0xFFA4372A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD4),
    onErrorContainer = Color(0xFF410001),
)

/*
 * Headings use the platform serif, which reads as print rather than as an app,
 * and costs no bundled font. Everything meant to be read at length stays in
 * the default sans.
 */
private val Display = FontFamily.Serif

private val ShotQuillTypography = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.copy(fontFamily = Display, fontWeight = FontWeight.SemiBold),
        headlineLarge = base.headlineLarge.copy(fontFamily = Display, fontWeight = FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.copy(fontFamily = Display, fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontFamily = Display, fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontFamily = Display, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(lineHeight = 26.sp),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Small capitals used above each group of controls. */
val SectionLabelStyle: TextStyle
    @Composable get() = MaterialTheme.typography.labelMedium.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.12.em,
    )

private val ShotQuillShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun ShotQuillTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = ShotQuillTypography,
        shapes = ShotQuillShapes,
        content = content,
    )
}
