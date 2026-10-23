package com.aif31.pocket.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7AD9CF),
    onPrimary = Color(0xFF003734),
    primaryContainer = Color(0xFF174C49),
    onPrimaryContainer = Color(0xFFB8F3EA),
    secondary = Color(0xFF86B9C3),
    onSecondary = Color(0xFF07363E),
    secondaryContainer = Color(0xFF284B51),
    onSecondaryContainer = Color(0xFFC0E9F0),
    tertiary = Color(0xFFFF9B86),
    onTertiary = Color(0xFF5B190D),
    tertiaryContainer = Color(0xFF763023),
    onTertiaryContainer = Color(0xFFFFDAD2),
    background = Color(0xFF0E1616),
    onBackground = Color(0xFFE6F0ED),
    surface = Color(0xFF0E1616),
    onSurface = Color(0xFFE6F0ED),
    surfaceVariant = Color(0xFF3F4947),
    onSurfaceVariant = Color(0xFFBEC9C6),
    outline = Color(0xFF889390),
    outlineVariant = Color(0xFF3F4947),
    // Containers step up from the background in the same teal-grey, instead of Material's violet-tinted defaults.
    surfaceDim = Color(0xFF0E1616),
    surfaceBright = Color(0xFF333E3D),
    surfaceContainerLowest = Color(0xFF091010),
    surfaceContainerLow = Color(0xFF161F1F),
    surfaceContainer = Color(0xFF1A2423),
    surfaceContainerHigh = Color(0xFF202A29),
    surfaceContainerHighest = Color(0xFF263130),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val PocketShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val PocketTypography = Typography(
    displaySmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
).withTabularFigures()

/**
 * Every style uses tabular figures: each digit takes the same width, so amounts line up in columns and do not shift
 * as they change, while reading in the same face as the words around them.
 */
private fun Typography.withTabularFigures(): Typography {
    fun TextStyle.tabular() = copy(fontFeatureSettings = "tnum")
    return copy(
        displayLarge = displayLarge.tabular(),
        displayMedium = displayMedium.tabular(),
        displaySmall = displaySmall.tabular(),
        headlineLarge = headlineLarge.tabular(),
        headlineMedium = headlineMedium.tabular(),
        headlineSmall = headlineSmall.tabular(),
        titleLarge = titleLarge.tabular(),
        titleMedium = titleMedium.tabular(),
        titleSmall = titleSmall.tabular(),
        bodyLarge = bodyLarge.tabular(),
        bodyMedium = bodyMedium.tabular(),
        bodySmall = bodySmall.tabular(),
        labelLarge = labelLarge.tabular(),
        labelMedium = labelMedium.tabular(),
        labelSmall = labelSmall.tabular(),
    )
}

/**
 * Semantic roles for Pocket illustrations. The artwork is drawn in dark teal, so it sits on a light plate rather than
 * a dark container.
 */
@Immutable
internal data class ArtworkColors(val plate: Color, val selectedPlate: Color, val ink: Color)

private val PocketArtworkColors = ArtworkColors(
    plate = Color(0xFFD5EAE4),
    selectedPlate = Color(0xFFE6F2EE),
    ink = Color(0xFF0B5B59),
)

internal val LocalArtworkColors = staticCompositionLocalOf { PocketArtworkColors }

/** Pocket is dark only: the same palette applies whatever the system's light or dark setting. */
@Composable
fun PocketTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalArtworkColors provides PocketArtworkColors) {
        MaterialTheme(colorScheme = DarkColors, shapes = PocketShapes, typography = PocketTypography) {
            // Screens drawn straight on the window, such as onboarding, have no Surface to set a content color.
            CompositionLocalProvider(LocalContentColor provides DarkColors.onBackground, content = content)
        }
    }
}
