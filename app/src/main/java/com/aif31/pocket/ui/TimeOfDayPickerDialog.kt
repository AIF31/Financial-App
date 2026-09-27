package com.aif31.pocket.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import java.time.LocalTime

/**
 * 24-hour Material time picker used by the Movement form and the daily reminder. `TimePicker` is still
 * `@ExperimentalMaterial3Api` in Material3 1.4.0; it replaces error-prone typed "HH:mm" entry, and the typed
 * field stays available as a fallback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeOfDayPickerDialog(
    initial: LocalTime,
    onPicked: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val pickerState = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Elegir hora") },
        text = { TimePicker(state = pickerState) },
        confirmButton = {
            TextButton(onClick = { onPicked(LocalTime.of(pickerState.hour, pickerState.minute)) }) { Text("Aceptar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
