package com.aif31.pocket

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aif31.pocket.notifications.NotificationBetaMetricsSnapshot
import com.aif31.pocket.settings.AppPreferences
import com.aif31.pocket.settings.PreferencesStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun NotificationAssistanceSettings(
    preferences: AppPreferences,
    preferencesStore: PreferencesStore?,
    padding: PaddingValues,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var accessGranted by remember { mutableStateOf(false) }
    var selectedPackages by remember { mutableStateOf(preferences.notificationSourcePackages) }
    var diagnostics by remember { mutableStateOf<NotificationBetaMetricsSnapshot?>(null) }
    var savingPackage by remember { mutableStateOf<String?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(preferences.notificationSourcePackages) {
        selectedPackages = preferences.notificationSourcePackages
    }
    DisposableEffect(context, lifecycleOwner) {
        fun refresh() {
            accessGranted = context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
            diagnostics = if (BuildConfig.DEBUG) {
                runCatching {
                    (context.applicationContext as PocketApplication).notificationBetaMetrics.snapshot()
                }.getOrNull()
            } else {
                null
            }
        }
        refresh()
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val apps = remember(context) {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        context.packageManager.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.packageName to it.loadLabel(context.packageManager).toString() }
            .filter { it.first != context.packageName }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TextButton(onClick = onBack) { Text("Atrás") }
            Text("Captura desde notificaciones", style = MaterialTheme.typography.headlineMedium)
            Text("Experimental · inglés y español. Pocket solo crea sugerencias para que las revises.")
            diagnostics?.let { metrics ->
                Text(
                    "Diagnóstico beta local: ${metrics.parserSuccesses}/${metrics.parserAttempts} detectadas, " +
                        "${metrics.parserFailures} fallidas. ${metrics.correctedConfirmations}/${metrics.confirmations} " +
                        "confirmaciones corregidas (${(metrics.correctionRate * 100).toInt()}%); " +
                        "importe ${metrics.amountCorrections}, moneda ${metrics.currencyCorrections}. " +
                        "El tamaño mínimo de la muestra sigue pendiente.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(if (accessGranted) "Acceso a notificaciones concedido" else "Acceso a notificaciones no concedido")
            Button(onClick = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }) { Text(if (accessGranted) "Administrar acceso" else "Conceder acceso") }
            saveError?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error)
            }
        }
        items(apps, key = { it.first }) { (packageName, label) ->
            val selected = packageName in selectedPackages
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(label)
                        Text(packageName, style = MaterialTheme.typography.bodySmall)
                    }
                    Checkbox(
                        checked = selected,
                        enabled = preferencesStore != null && savingPackage == null,
                        modifier = Modifier.semantics {
                            contentDescription = "Permitir notificaciones de $label ($packageName)"
                        },
                        onCheckedChange = { checked ->
                            val next = selectedPackages.toMutableSet()
                            if (checked) next += packageName else next -= packageName
                            selectedPackages = next
                            savingPackage = packageName
                            saveError = null
                            scope.launch {
                                try {
                                    preferencesStore?.setNotificationSourcePackage(packageName, checked)
                                } catch (error: Exception) {
                                    if (error is CancellationException) throw error
                                    selectedPackages = preferences.notificationSourcePackages
                                    saveError = "No se pudo guardar la selección. Inténtalo de nuevo."
                                } finally {
                                    savingPackage = null
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}
