package com.aif31.pocket.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable

@Serializable
internal enum class SettingsSection(
    val title: String,
    val supportingText: String,
    val icon: ImageVector,
) {
    PERIOD(
        "Periodo y fondos",
        "Fondos, fecha de inicio y creación del siguiente periodo",
        Icons.Default.AccountBalanceWallet,
    ),
    REMINDERS(
        "Recordatorios",
        "Horario diario y privacidad en la pantalla bloqueada",
        Icons.Default.Notifications,
    ),
    PAYMENT_METHODS(
        "Métodos de pago",
        "Añade, edita, archiva y restaura métodos",
        Icons.Default.CreditCard,
    ),
    TEMPLATES(
        "Plantillas recurrentes",
        "Atajos que precargan el formulario sin crear gastos",
        Icons.Default.Repeat,
    ),
    DATA(
        "Datos y privacidad",
        "Backup, restauración y exportación CSV",
        Icons.Default.Backup,
    ),
}

@Composable
internal fun ProductionSettingsHub(
    contentPadding: PaddingValues,
    onOpenSection: (SettingsSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = contentPadding.calculateTopPadding() + 16.dp,
            end = 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("Ajustes", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Administra tu Pocket sin mezclar tareas.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(SettingsSection.entries, key = { it.name }) { section ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
                    .clickable { onOpenSection(section) },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(section.icon, contentDescription = null, modifier = Modifier.size(24.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(section.title, style = MaterialTheme.typography.titleMedium)
                        Text(section.supportingText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Abrir ${section.title}",
                    )
                }
            }
        }
    }
}
