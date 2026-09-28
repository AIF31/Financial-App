package com.aif31.pocket

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aif31.pocket.notifications.DETECTED_MOVEMENT_CHANNEL_ID
import com.aif31.pocket.notifications.NotificationBetaMetricsSnapshot
import com.aif31.pocket.notifications.appLabel
import com.aif31.pocket.settings.AppPreferences
import com.aif31.pocket.settings.PreferencesStore
import com.aif31.pocket.ui.PocketTopAppBar
import androidx.compose.foundation.layout.WindowInsets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal data class SourceApp(val packageName: String, val label: String)

private val likelyFinanceApp = Regex(
    "(?i)\\b(bank|banco|banca|sab|rajhi|alinma|snb|ahli|riyad|stc ?pay|urpay|bbva|banorte|santander|nu|hsbc|" +
        "citi|mercado ?pago|paypal|wallet|cartera|pay|messages|mensajes|sms)\\b"
)

/** Filters and orders the app list: search by name or package; suggested finance and SMS apps first. */
internal fun sourceAppSections(
    apps: List<SourceApp>,
    selected: Set<String>,
    query: String,
): Triple<List<SourceApp>, List<SourceApp>, List<SourceApp>> {
    val needle = query.trim()
    val matching = apps.filter {
        needle.isEmpty() || it.label.contains(needle, ignoreCase = true) || it.packageName.contains(needle, ignoreCase = true)
    }
    val chosen = matching.filter { it.packageName in selected }
    val rest = matching.filterNot { it.packageName in selected }
    val suggested = if (needle.isEmpty()) rest.filter { likelyFinanceApp.containsMatchIn(it.label) } else emptyList()
    return Triple(chosen, suggested, rest - suggested.toSet())
}

@Composable
internal fun NotificationAssistanceSettings(
    preferences: AppPreferences,
    preferencesStore: PreferencesStore?,
    padding: PaddingValues,
    onBack: () -> Unit,
    onRequestNotificationPermission: () -> Unit = {},
    notificationPermissionRevision: Int = 0,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var accessGranted by remember { mutableStateOf(false) }
    var alertsAllowed by remember { mutableStateOf(false) }
    var selectedPackages by remember { mutableStateOf(preferences.notificationSourcePackages) }
    var diagnostics by remember { mutableStateOf<NotificationBetaMetricsSnapshot?>(null) }
    var savingPackage by remember { mutableStateOf<String?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(preferences.notificationSourcePackages) {
        selectedPackages = preferences.notificationSourcePackages
    }
    DisposableEffect(context, lifecycleOwner, notificationPermissionRevision) {
        fun refresh() {
            accessGranted = context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
            alertsAllowed = detectionAlertsAllowed(context)
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
    val installedApps = remember(context) {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        context.packageManager.queryIntentActivities(launcher, 0)
            .map { SourceApp(it.activityInfo.packageName, it.loadLabel(context.packageManager).toString()) }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
    // A selected app without a launcher entry (for example a system SMS app) must stay visible to be removed.
    val apps = remember(installedApps, selectedPackages) {
        val known = installedApps.mapTo(mutableSetOf()) { it.packageName }
        installedApps + selectedPackages.filterNot { it in known }.map { SourceApp(it, appLabel(context, it)) }
    }
    val (chosenApps, suggestedApps, otherApps) = sourceAppSections(apps, selectedPackages, query)

    fun toggle(app: SourceApp, checked: Boolean) {
        val next = selectedPackages.toMutableSet()
        if (checked) next += app.packageName else next -= app.packageName
        selectedPackages = next
        savingPackage = app.packageName
        saveError = null
        scope.launch {
            try {
                preferencesStore?.setNotificationSourcePackage(app.packageName, checked)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                selectedPackages = preferences.notificationSourcePackages
                saveError = "No se pudo guardar la selección. Inténtalo de nuevo."
            } finally {
                savingPackage = null
            }
        }
    }

    fun savePreference(write: suspend PreferencesStore.() -> Unit) {
        saveError = null
        scope.launch {
            try {
                preferencesStore?.write()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                saveError = "No se pudo guardar el ajuste. Inténtalo de nuevo."
            }
        }
    }

    val listState = rememberLazyListState()
    val searchFocus = remember { FocusRequester() }
    // Items before the search field: introduction, setup, behaviour and, when shown, diagnostics.
    val searchIndex = if (diagnostics == null) 3 else 4

    Column(Modifier.fillMaxSize().padding(padding)) {
        PocketTopAppBar("Captura desde notificaciones", onBack, windowInsets = WindowInsets(0))
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Pocket lee los avisos de pago de las apps que elijas y registra el gasto por ti. " +
                        "Experimental · inglés y español · SAR, USD y MXN.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Configuración", style = MaterialTheme.typography.titleMedium)
                        SetupStep(
                            done = accessGranted,
                            title = if (accessGranted) "Acceso a notificaciones concedido" else "Acceso a notificaciones no concedido",
                            detail = "Android te pedirá activar Pocket en la lista de acceso a notificaciones.",
                            action = if (accessGranted) "Administrar acceso" else "Conceder acceso",
                            onAction = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                        )
                        HorizontalDivider()
                        SetupStep(
                            done = selectedPackages.isNotEmpty(),
                            title = when (selectedPackages.size) {
                                0 -> "Ninguna app seleccionada"
                                1 -> "1 app seleccionada"
                                else -> "${selectedPackages.size} apps seleccionadas"
                            },
                            detail = "Elige tu banco o, si recibes SMS del banco, tu app de mensajes.",
                            action = if (selectedPackages.isEmpty()) "Elegir apps" else null,
                            onAction = {
                                scope.launch {
                                    listState.animateScrollToItem(searchIndex)
                                    searchFocus.requestFocus()
                                }
                            },
                        )
                        HorizontalDivider()
                        SetupStep(
                            done = alertsAllowed || !preferences.notificationDetectionAlerts,
                            title = if (alertsAllowed) "Avisos de Pocket permitidos" else "Avisos de Pocket bloqueados",
                            detail = "Necesarios para ver el aviso emergente cuando se detecta un gasto.",
                            action = if (alertsAllowed) null else "Permitir avisos",
                            onAction = {
                                if (android.os.Build.VERSION.SDK_INT >= 33 && !NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                                    onRequestNotificationPermission()
                                } else {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    )
                                }
                            },
                        )
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Comportamiento", style = MaterialTheme.typography.titleMedium)
                        SettingSwitch(
                            title = "Registrar automáticamente",
                            detail = "Si ya registraste ese comercio, Pocket usa el mismo Pocket. " +
                                "Comercios nuevos o monedas extranjeras quedan en Movimientos para revisar.",
                            checked = preferences.notificationAutoRecord,
                            enabled = preferencesStore != null,
                            testTag = "notification_auto_record",
                            onCheckedChange = { savePreference { setNotificationAutoRecord(it) } },
                        )
                        SettingSwitch(
                            title = "Aviso al detectar un gasto",
                            detail = "Muestra un aviso breve con el importe, el comercio y el Pocket.",
                            checked = preferences.notificationDetectionAlerts,
                            enabled = preferencesStore != null,
                            testTag = "notification_detection_alerts",
                            onCheckedChange = { savePreference { setNotificationDetectionAlerts(it) } },
                        )
                    }
                }
                saveError?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
            }
            diagnostics?.let { metrics ->
                item {
                    Text(
                        "Diagnóstico beta local: ${metrics.parserSuccesses}/${metrics.parserAttempts} detectadas, " +
                            "${metrics.parserFailures} fallidas. ${metrics.correctedConfirmations}/${metrics.confirmations} " +
                            "confirmaciones corregidas (${(metrics.correctionRate * 100).toInt()}%); " +
                            "importe ${metrics.amountCorrections}, moneda ${metrics.currencyCorrections}. " +
                            "El tamaño mínimo de la muestra sigue pendiente.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            item {
                Text("Apps de origen", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Buscar app") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) { Icon(Icons.Default.Clear, contentDescription = "Borrar búsqueda") }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .focusRequester(searchFocus)
                        .testTag("notification_app_search"),
                )
            }
            fun section(key: String, title: String, list: List<SourceApp>) {
                if (list.isEmpty()) return
                item(key = "header-$key") {
                    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                // Keyed by package alone, so a row keeps its identity and focus when it moves between sections.
                items(list, key = { it.packageName }) { app ->
                    SourceAppRow(
                        app = app,
                        selected = app.packageName in selectedPackages,
                        enabled = preferencesStore != null && savingPackage == null,
                        onCheckedChange = { toggle(app, it) },
                    )
                }
            }
            section("selected", "Seleccionadas", chosenApps)
            section("suggested", "Sugeridas: bancos, pagos y mensajes", suggestedApps)
            section("all", if (query.isBlank()) "Todas las apps" else "Resultados", otherApps)
            if (chosenApps.isEmpty() && suggestedApps.isEmpty() && otherApps.isEmpty()) {
                item { Text("Ninguna app coincide con “${query.trim()}”.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "Pocket no guarda el texto de las notificaciones. Revisa Movimientos de vez en cuando: " +
                            "algunos avisos pueden no reconocerse.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupStep(
    done: Boolean,
    title: String,
    detail: String,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Icon(
            if (done) Icons.Default.CheckCircle else Icons.Default.Info,
            contentDescription = null,
            tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (action != null) {
                if (done) OutlinedButton(onClick = onAction) { Text(action) } else Button(onClick = onAction) { Text(action) }
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    detail: String,
    checked: Boolean,
    enabled: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag).semantics { contentDescription = title },
        )
    }
}

@Composable
private fun SourceAppRow(
    app: SourceApp,
    selected: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val icon = remember(app.packageName) { appIcon(context, app.packageName) }
    // One toggleable row per app: TalkBack reads the name and checked state as a single checkbox.
    Card(
        modifier = Modifier.fillMaxWidth().toggleable(
            value = selected,
            enabled = enabled,
            role = Role.Checkbox,
            onValueChange = onCheckedChange,
        ),
        colors = if (selected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    if (icon != null) {
                        Image(icon, contentDescription = null, modifier = Modifier.size(32.dp))
                    } else {
                        Text(app.label.take(1).uppercase(), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            Text(
                app.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Checkbox(checked = selected, enabled = enabled, onCheckedChange = null)
        }
    }
}

private fun appIcon(context: Context, packageName: String): ImageBitmap? = runCatching {
    context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap()
}.getOrNull()

private fun detectionAlertsAllowed(context: Context): Boolean {
    val manager = NotificationManagerCompat.from(context)
    if (!manager.areNotificationsEnabled()) return false
    val channel = context.getSystemService(NotificationManager::class.java)?.getNotificationChannel(DETECTED_MOVEMENT_CHANNEL_ID)
    return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
}
