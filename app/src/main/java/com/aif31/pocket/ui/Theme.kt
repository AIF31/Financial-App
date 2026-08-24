package com.aif31.pocket.ui

import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B5B59),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC8E6DD),
    onPrimaryContainer = Color(0xFF123A38),
    secondary = Color(0xFF5C9EAB),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD8EBEE),
    onSecondaryContainer = Color(0xFF183B42),
    tertiary = Color(0xFFF07F69),
    onTertiary = Color(0xFF4A130B),
    tertiaryContainer = Color(0xFFFFDAD2),
    onTertiaryContainer = Color(0xFF57150C),
    background = Color(0xFFF7F4EC),
    onBackground = Color(0xFF1C2A2A),
    surface = Color(0xFFF7F4EC),
    onSurface = Color(0xFF1C2A2A),
    surfaceVariant = Color(0xFFE4E9E5),
    onSurfaceVariant = Color(0xFF465452),
    outline = Color(0xFF73827F),
    outlineVariant = Color(0xFFC2CECA),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

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
        fontFamily = FontFamily.Monospace,
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
)

@Composable
fun PocketTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = PocketShapes,
        typography = PocketTypography,
        content = content,
    )
}
