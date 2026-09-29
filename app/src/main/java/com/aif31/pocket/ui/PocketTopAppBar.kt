package com.aif31.pocket.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * App bar for subordinate routes (philosophy §8: subordinate routes get a title and back navigation).
 *
 * Material3 1.4.0 still marks `TopAppBar` as `@ExperimentalMaterial3Api`; there is no stable replacement, so the
 * opt-in is isolated here instead of being repeated in every screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PocketTopAppBar(
    title: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    navigationLabel: String = "Atrás",
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = navigationLabel)
            }
        },
        windowInsets = windowInsets,
        modifier = modifier,
    )
}
