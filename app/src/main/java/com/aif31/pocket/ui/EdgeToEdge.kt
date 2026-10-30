package com.aif31.pocket.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp

/**
 * These system bar insets plus a fixed margin on each side. Passed as a list's `contentPadding`, the list scrolls
 * behind the system bars while its first and last items still clear them.
 */
@Composable
internal fun PaddingValues.plusMargin(start: Dp, top: Dp, end: Dp, bottom: Dp): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(direction) + start,
        top = calculateTopPadding() + top,
        end = calculateEndPadding(direction) + end,
        bottom = calculateBottomPadding() + bottom,
    )
}

/**
 * These insets with the top one set to zero. A root list sits above an opaque navigation bar, so it pads the bottom with
 * this and takes only the top inset as `contentPadding`, scrolling behind the status bar alone.
 */
@Composable
internal fun PaddingValues.exceptTop(): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(direction),
        end = calculateEndPadding(direction),
        bottom = calculateBottomPadding(),
    )
}

/**
 * Fades the content scrolling behind the transparent status bar into the background, so the bar's light icons stay
 * legible over cards and text. Drawn above the whole app; it handles no touches, and over a top app bar, which shares
 * the background color, it is invisible.
 */
@Composable
internal fun StatusBarProtection(color: Color = MaterialTheme.colorScheme.background) {
    val height = with(LocalDensity.current) { (WindowInsets.statusBars.getTop(this) * 1.2f).toDp() }
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(height)
            .background(Brush.verticalGradient(listOf(color, color.copy(alpha = 0.8f), Color.Transparent))),
    )
}
