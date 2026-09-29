package com.aif31.pocket.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.aif31.pocket.data.PocketBudgetStatus

/** Presentation of a [PocketBudgetStatus]; the status itself is decided in the data layer. */
internal data class StatusPresentation(
    val label: String,
    val icon: ImageVector,
    val container: Color,
    val content: Color,
    val indicator: Color,
)

@Composable
internal fun PocketBudgetStatus.presentation(): StatusPresentation = with(MaterialTheme.colorScheme) {
    when (this@presentation) {
        PocketBudgetStatus.EXHAUSTED -> StatusPresentation("Agotado", Icons.Default.Error, errorContainer, onErrorContainer, error)
        PocketBudgetStatus.AT_RISK -> StatusPresentation("En riesgo", Icons.Default.Warning, tertiaryContainer, onTertiaryContainer, tertiary)
        PocketBudgetStatus.UNBUDGETED -> StatusPresentation("Sin presupuesto", Icons.Default.RemoveCircleOutline, surfaceContainerHighest, onSurfaceVariant, outline)
        PocketBudgetStatus.ON_TRACK -> StatusPresentation("En buen ritmo", Icons.Default.CheckCircle, primaryContainer, onPrimaryContainer, primary)
    }
}

/** Status pill that pairs an icon and a word with color so state never depends on color alone. */
@Composable
internal fun PocketStatusBadge(status: PocketBudgetStatus, modifier: Modifier = Modifier) {
    val presentation = status.presentation()
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = presentation.container,
        contentColor = presentation.content,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(presentation.icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(presentation.label, style = MaterialTheme.typography.labelMedium)
        }
    }
}
