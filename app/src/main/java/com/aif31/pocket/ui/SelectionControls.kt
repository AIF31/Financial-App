package com.aif31.pocket.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** One selectable option; [key] identifies it and [testTag] is optional. */
internal data class ChoiceOption<T>(val key: T, val label: String, val testTag: String? = null)

/**
 * Wrapping row of Material filter chips for a single choice. Selection is exposed through chip semantics
 * rather than a text marker, and every option stays visible instead of hiding behind horizontal scroll.
 */
@Composable
internal fun <T> SingleChoiceChips(
    options: List<ChoiceOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        options.forEach { option ->
            val isSelected = option.key == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(option.key) },
                label = { Text(option.label) },
                leadingIcon = if (isSelected) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                } else {
                    null
                },
                modifier = option.testTag?.let { Modifier.testTag(it) } ?: Modifier,
            )
        }
    }
}

/** Connected segmented control for two or three mutually exclusive, equally weighted options. */
@Composable
internal fun <T> SegmentedChoice(
    options: List<ChoiceOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option.key == selected,
                onClick = { onSelect(option.key) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = { Text(option.label) },
                modifier = option.testTag?.let { Modifier.testTag(it) } ?: Modifier,
            )
        }
    }
}
