package com.aif31.pocket

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aif31.pocket.data.LedgerCommand
import com.aif31.pocket.data.LedgerResult
import com.aif31.pocket.data.LedgerState
import com.aif31.pocket.data.MovementType
import com.aif31.pocket.data.Movement
import com.aif31.pocket.data.ConversionStatus
import com.aif31.pocket.data.PocketLedger
import com.aif31.pocket.data.PocketPeriodSummary
import com.aif31.pocket.domain.Money
import com.aif31.pocket.settings.AppPreferences
import com.aif31.pocket.settings.PreferencesStore
import com.aif31.pocket.settings.ReminderScheduler
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private enum class RootScreen(val label: String) {
    DASHBOARD("Inicio"), MOVEMENTS("Movimientos"), POCKETS("Pockets"), SETTINGS("Ajustes")
}

@Composable
fun PocketApp(
    ledger: PocketLedger,
    preferences: PreferencesStore? = null,
    reminderScheduler: ReminderScheduler? = null,
    openNewExpense: Boolean = false,
    restoreCandidate: ByteArray? = null,
    onRestoreCandidateHandled: () -> Unit = {},
    onCreateBackup: () -> Unit = {},
    onCreateCsv: () -> Unit = {},
    onPickBackup: () -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {},
    undoWindowMillis: Long = 5_000,
) {
    val observedState by ledger.state.collectAsStateWithLifecycle(initialValue = null)
    val preferencesFlow = remember(preferences) { preferences?.state ?: flowOf(AppPreferences()) }
    val preferenceState by preferencesFlow.collectAsStateWithLifecycle(initialValue = AppPreferences())
    var backupPreview by remember { mutableStateOf<com.aif31.pocket.data.BackupPreview?>(null) }
    var restoreError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(restoreCandidate) {
        restoreError = null
        backupPreview = restoreCandidate?.let { ledger.previewBackup(it) }
    }
    if (restoreCandidate != null && backupPreview != null) {
        val preview = backupPreview!!
        val scope = rememberCoroutineScope()
        AlertDialog(
            onDismissRequest = { onRestoreCandidateHandled(); backupPreview = null; restoreError = null },
            title = { Text(if (restoreError != null) "No se pudo restaurar" else if (preview.valid) "Confirmar restauración" else "Backup inválido") },
            text = {
                Text(
                    restoreError ?: if (preview.valid) "Versión ${preview.version}: ${preview.periods} periodos, ${preview.pockets} Pockets y ${preview.movements} movimientos."
                    else preview.message ?: "No se puede leer el archivo.",
                )
            },
            confirmButton = {
                if (preview.valid) Button(onClick = {
                    scope.launch {
                        when (val result = ledger.restoreBackup(restoreCandidate)) {
                            LedgerResult.Success -> {
                                val restored = ledger.state.first { !it.needsOnboarding }
                                restored.periods.maxByOrNull { it.start }?.let { preferences?.setFuturePeriodStartDay(it.configuredStartDay) }
                                onRestoreCandidateHandled()
                                backupPreview = null
                                restoreError = null
                            }
                            is LedgerResult.Rejected -> restoreError = result.message
                            is LedgerResult.Deleted -> Unit
                        }
                    }
                }) { Text("Restaurar") }
            },
            dismissButton = { TextButton(onClick = { onRestoreCandidateHandled(); backupPreview = null; restoreError = null }) { Text("Cancelar") } },
        )
    }
    val state = observedState
    if (state == null) {
        Text("Cargando…", modifier = Modifier.padding(24.dp))
        return
    }
    if (state.needsOnboarding) {
        OnboardingScreen(ledger, preferences, onPickBackup)
        return
    }

    var screen by remember { mutableStateOf(RootScreen.DASHBOARD) }
    var movementOpen by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(openNewExpense, state.currentPeriod?.id) {
        if (openNewExpense && state.currentPeriod != null) movementOpen = true
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                RootScreen.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = screen == destination,
                        onClick = { screen = destination },
                        icon = {},
                        label = { Text(destination.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { movementOpen = true }) {
                androidx.compose.material3.Icon(Icons.Default.Add, contentDescription = "Añadir movimiento")
            }
        },
    ) { padding ->
        when (screen) {
            RootScreen.DASHBOARD -> DashboardScreen(state, padding)
            RootScreen.MOVEMENTS -> MovementsScreen(state, ledger, snackbar, padding, undoWindowMillis)
            RootScreen.POCKETS -> PocketsScreen(state, ledger, padding)
            RootScreen.SETTINGS -> SettingsScreen(
                state = state,
                ledger = ledger,
                preferences = preferenceState,
                preferencesStore = preferences,
                reminderScheduler = reminderScheduler,
                onCreateBackup = onCreateBackup,
                onCreateCsv = onCreateCsv,
                onPickBackup = onPickBackup,
                onRequestNotificationPermission = onRequestNotificationPermission,
                padding = padding,
            )
        }
    }

    if (movementOpen) {
        MovementDialog(
            state = state,
            ledger = ledger,
            onDismiss = { movementOpen = false },
            onSaved = {
                movementOpen = false
                screen = RootScreen.DASHBOARD
            },
        )
    }
}

@Composable
private fun OnboardingScreen(ledger: PocketLedger, preferences: PreferencesStore?, onPickBackup: () -> Unit) {
    var funds by remember { mutableStateOf("") }
    var startDay by remember { mutableStateOf("25") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Configura tu primer periodo", style = MaterialTheme.typography.headlineMedium)
        Text("Sin cuenta ni conexión. Tus datos permanecen en este dispositivo.")
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = funds,
            onValueChange = { funds = it },
            label = { Text("Fondos nuevos (SAR)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().testTag("new_funds"),
        )
        OutlinedTextField(
            value = startDay,
            onValueChange = { startDay = it.filter(Char::isDigit).take(2) },
            label = { Text("Día de inicio") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().testTag("start_day"),
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            onClick = {
                scope.launch {
                    runCatching {
                        LedgerCommand.Initialize(
                            newFundsMinor = Money.parse(funds, "SAR").minor,
                            startDay = startDay.toInt(),
                        )
                    }.onSuccess {
                        when (val result = ledger.execute(it)) {
                            LedgerResult.Success -> preferences?.setFuturePeriodStartDay(it.startDay)
                            is LedgerResult.Rejected -> error = result.message
                            is LedgerResult.Deleted -> Unit
                        }
                    }
                        .onFailure { error = "Revisa los fondos y el día de inicio" }
                }
            },
        ) { Text("Comenzar") }
        TextButton(onClick = onPickBackup, modifier = Modifier.fillMaxWidth()) { Text("Restaurar backup") }
    }
}

@Composable
private fun DashboardScreen(state: LedgerState, padding: PaddingValues) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding).testTag("dashboard_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Tu periodo", style = MaterialTheme.typography.headlineMedium) }
        item { MetricCard("Fondos nuevos", money(state.newFundsMinor), "Fondos nuevos: ${money(state.newFundsMinor)}") }
        item { MetricCard("Rollover", money(state.rolloverTotalMinor)) }
        item { MetricCard("Sin asignar", money(state.unallocatedMinor)) }
        item { MetricCard("Gasto neto confirmado", money(state.netSpendMinor)) }
        item { MetricCard("Gasto diario promedio", money(state.netSpendMinor / state.elapsedDays.coerceAtLeast(1))) }
        item { MetricCard("Disponibilidad rastreada", money(state.trackedAvailabilityMinor)) }
        item { Text("Proyección estimada: ${money(state.projectionMinor)}") }
        item {
            Text(
                state.previousPeriodNetSpendMinor?.let {
                    val difference = state.netSpendMinor - it
                    "Periodo anterior: ${money(it)} · Diferencia: ${if (difference >= 0) "+" else ""}${money(difference)}"
                }
                    ?: "Aún no existe un periodo anterior",
            )
        }
        items(state.pockets.filterNot { it.pocket.archived }) { summary -> PocketSummaryCard(summary) }
    }
}

@Composable
private fun MetricCard(title: String, value: String, semanticsText: String = "$title: $value") {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title)
            Text(value, style = MaterialTheme.typography.titleLarge)
            if (semanticsText != "$title: $value") Text(semanticsText)
        }
    }
}

@Composable
private fun PocketSummaryCard(summary: PocketPeriodSummary) {
    val status = when {
        summary.exhausted -> "Agotado o sobregirado"
        summary.atRisk -> "Alerta: 80% consumido"
        else -> null
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(summary.pocket.name, style = MaterialTheme.typography.titleMedium)
            Text("Presupuesto: ${money(summary.budgetMinor)}")
            Text("Rollover: ${money(summary.rolloverMinor)}", modifier = Modifier.testTag("rollover_${summary.pocket.name}"))
            Text("Gastado: ${money(summary.netSpendMinor)}")
            Text("Disponible: ${money(summary.availabilityMinor)}")
            Text("${summary.consumedPercent}% consumido")
            status?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun MovementsScreen(
    state: LedgerState,
    ledger: PocketLedger,
    snackbar: SnackbarHostState,
    padding: PaddingValues,
    undoWindowMillis: Long,
) {
    var query by remember { mutableStateOf("") }
    var periodIndex by remember { mutableStateOf(0) }
    var pocketIndex by remember { mutableStateOf(0) }
    var currencyIndex by remember { mutableStateOf(0) }
    var methodIndex by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf<Movement?>(null) }
    var editing by remember { mutableStateOf<Movement?>(null) }
    val scope = rememberCoroutineScope()
    val periodOptions = listOf<String?>(null) + state.periods.map { it.id }
    val pocketOptions = listOf<String?>(null) + state.pockets.map { it.pocket.id }
    val currencyOptions = listOf<String?>(null) + state.movements.map { it.originalCurrencyCode }.distinct()
    val methodOptions = listOf<String?>(null) + state.paymentMethods.map { it.id }
    val filtered = state.movements.filter { movement ->
        val text = listOfNotNull(movement.merchant, movement.note).joinToString(" ")
        (query.isBlank() || text.contains(query, ignoreCase = true)) &&
            (periodOptions.getOrNull(periodIndex) == null || movement.periodId == periodOptions[periodIndex]) &&
            (pocketOptions.getOrNull(pocketIndex) == null || movement.pocketId == pocketOptions[pocketIndex]) &&
            (currencyOptions.getOrNull(currencyIndex) == null || movement.originalCurrencyCode == currencyOptions[currencyIndex]) &&
            (methodOptions.getOrNull(methodIndex) == null || movement.paymentMethodId == methodOptions[methodIndex])
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Text("Movimientos", style = MaterialTheme.typography.headlineMedium) }
        item {
            OutlinedTextField(
                query,
                { query = it },
                label = { Text("Buscar comercio o nota") },
                modifier = Modifier.fillMaxWidth().testTag("history_search"),
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { periodIndex = (periodIndex + 1) % periodOptions.size }, modifier = Modifier.testTag("filter_period")) {
                    Text(if (periodIndex == 0) "Todos los periodos" else state.periods.first { it.id == periodOptions[periodIndex] }.start.toString())
                }
                OutlinedButton(onClick = { pocketIndex = (pocketIndex + 1) % pocketOptions.size }, modifier = Modifier.testTag("filter_pocket")) {
                    Text(if (pocketIndex == 0) "Todos los Pockets" else state.pockets.first { it.pocket.id == pocketOptions[pocketIndex] }.pocket.name)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { currencyIndex = (currencyIndex + 1) % currencyOptions.size }, modifier = Modifier.testTag("filter_currency")) {
                    Text(currencyOptions.getOrNull(currencyIndex) ?: "Todas las monedas")
                }
                OutlinedButton(onClick = { methodIndex = (methodIndex + 1) % methodOptions.size }, modifier = Modifier.testTag("filter_method")) {
                    Text(if (methodIndex == 0) "Todos los métodos" else state.paymentMethods.first { it.id == methodOptions[methodIndex] }.name)
                }
            }
        }
        if (filtered.isEmpty()) item { Text("No hay movimientos para estos filtros") }
        items(filtered, key = { it.id }) { movement ->
            Card(Modifier.fillMaxWidth().clickable { selected = movement }) {
                Column(Modifier.padding(16.dp)) {
                    Text(movement.pocketName, style = MaterialTheme.typography.titleMedium)
                    Text((if (movement.type == MovementType.EXPENSE) "-" else "+") + money(movement.sarAmountMinor))
                    if (movement.originalCurrencyCode != "SAR" && movement.originalAmountMinor != null) {
                        Text("${movement.originalCurrencyCode} ${minorNumber(movement.originalAmountMinor)} · ${if (movement.conversionStatus == ConversionStatus.CONFIRMED) "Confirmado" else "Estimado"}")
                    }
                    movement.merchant?.let { Text(it) }
                    movement.note?.let { Text(it) }
                }
            }
        }
    }
    selected?.let { movement ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(movement.pocketName) },
            text = { Text("${if (movement.type == MovementType.EXPENSE) "Gasto" else "Devolución"} ${money(movement.sarAmountMinor)}\n${movement.localDate}") },
            confirmButton = {
                TextButton(onClick = { editing = movement; selected = null }) { Text("Editar") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        selected = null
                        scope.launch {
                            val result = ledger.execute(LedgerCommand.DeleteMovement(movement.id))
                            if (result is LedgerResult.Deleted) {
                                val action = withTimeoutOrNull(undoWindowMillis) {
                                    snackbar.showSnackbar("Movimiento eliminado", "Deshacer", duration = SnackbarDuration.Indefinite)
                                }
                                if (action == SnackbarResult.ActionPerformed) ledger.execute(LedgerCommand.RestoreMovement(result.movement))
                                else snackbar.currentSnackbarData?.dismiss()
                            }
                        }
                    }) { Text("Eliminar") }
                    TextButton(onClick = { selected = null }) { Text("Cerrar") }
                }
            },
        )
    }
    editing?.let { movement ->
        MovementDialog(state, ledger, onDismiss = { editing = null }, onSaved = { editing = null }, initialMovement = movement)
    }
}

@Composable
private fun PocketsScreen(state: LedgerState, ledger: PocketLedger, padding: PaddingValues) {
    var selected by remember { mutableStateOf<PocketPeriodSummary?>(null) }
    var selectedPeriodId by remember(state.currentPeriod?.id) { mutableStateOf(state.currentPeriod?.id) }
    var editing by remember { mutableStateOf<PocketPeriodSummary?>(null) }
    var creating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val shownPockets = state.pocketSummariesByPeriod[selectedPeriodId].orEmpty()
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Text("Pockets", style = MaterialTheme.typography.headlineMedium) }
        item {
            Text("Periodo")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.periods, key = { it.id }) { period ->
                    TextButton(onClick = { selectedPeriodId = period.id }) {
                        Text(if (selectedPeriodId == period.id) "✓ ${period.start}" else period.start.toString())
                    }
                }
            }
        }
        item { Button(onClick = { creating = true }) { Text("Crear Pocket") } }
        items(shownPockets.filterNot { it.pocket.archived }, key = { it.pocket.id }) { summary ->
            Card(Modifier.fillMaxWidth().testTag("pocket_${summary.pocket.name}").clickable { selected = summary }) {
                Column(Modifier.padding(16.dp)) {
                    Text(summary.pocket.name, style = MaterialTheme.typography.titleMedium)
                    Text("Presupuesto ${money(summary.budgetMinor)} · Disponible ${money(summary.availabilityMinor)}")
                    Text(if (summary.pocket.rolloverEnabled) "Rollover activado" else "Sin rollover")
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = { scope.launch { ledger.execute(LedgerCommand.MovePocket(summary.pocket.id, -1)) } },
                            modifier = Modifier.semantics { contentDescription = "Mover ${summary.pocket.name} arriba" },
                        ) { Text("↑") }
                        TextButton(
                            onClick = { scope.launch { ledger.execute(LedgerCommand.MovePocket(summary.pocket.id, 1)) } },
                            modifier = Modifier.semantics { contentDescription = "Mover ${summary.pocket.name} abajo" },
                        ) { Text("↓") }
                        TextButton(onClick = { editing = summary }) { Text("Editar") }
                        TextButton(onClick = { scope.launch { ledger.execute(LedgerCommand.ArchivePocket(summary.pocket.id)) } }) { Text("Archivar") }
                    }
                }
            }
        }
        if (shownPockets.any { it.pocket.archived }) {
            item { Text("Archivados", style = MaterialTheme.typography.titleMedium) }
            items(shownPockets.filter { it.pocket.archived }, key = { "archived-${it.pocket.id}" }) { summary ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(summary.pocket.name)
                    TextButton(onClick = { scope.launch { ledger.execute(LedgerCommand.ArchivePocket(summary.pocket.id, false)) } }) { Text("Restaurar") }
                }
            }
        }
    }
    selected?.let { summary ->
        AllocationDialog(state, selectedPeriodId ?: state.currentPeriod!!.id, summary, ledger) { selected = null }
    }
    if (creating || editing != null) {
        PocketEditorDialog(editing, ledger) { creating = false; editing = null }
    }
}

@Composable
private fun PocketEditorDialog(
    existing: PocketPeriodSummary?,
    ledger: PocketLedger,
    onDismiss: () -> Unit,
) {
    var name by remember(existing?.pocket?.id) { mutableStateOf(existing?.pocket?.name.orEmpty()) }
    var rollover by remember(existing?.pocket?.id) { mutableStateOf(existing?.pocket?.rolloverEnabled ?: false) }
    var error by remember(existing?.pocket?.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Crear Pocket" else "Editar Pocket") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre") })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Aplicar rollover")
                    Switch(rollover, { rollover = it })
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                scope.launch {
                    when (val result = ledger.execute(LedgerCommand.UpsertPocket(existing?.pocket?.id, name, rollover))) {
                        LedgerResult.Success -> onDismiss()
                        is LedgerResult.Rejected -> error = result.message
                        is LedgerResult.Deleted -> Unit
                    }
                }
            }) { Text("Guardar Pocket") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun AllocationDialog(
    state: LedgerState,
    periodId: String,
    summary: PocketPeriodSummary,
    ledger: PocketLedger,
    onDismiss: () -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(summary.pocket.name) },
        text = {
            Column {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Presupuesto SAR") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.testTag("allocation_amount"),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                scope.launch {
                    val parsed = runCatching { Money.parse(amount, "SAR").minor }.getOrNull() ?: run {
                        error = "Escribe un presupuesto válido"
                        return@launch
                    }
                    when (val result = ledger.execute(LedgerCommand.SetAllocation(periodId, summary.pocket.id, parsed))) {
                        LedgerResult.Success -> onDismiss()
                        is LedgerResult.Rejected -> error = result.message
                        is LedgerResult.Deleted -> Unit
                    }
                }
            }) { Text("Guardar presupuesto") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun MovementDialog(
    state: LedgerState,
    ledger: PocketLedger,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    initialMovement: Movement? = null,
) {
    var amount by remember(initialMovement?.id) { mutableStateOf(initialMovement?.let { minorNumber(it.sarAmountMinor) }.orEmpty()) }
    var selectedPocket by remember(initialMovement?.id) { mutableStateOf(initialMovement?.pocketId) }
    var refund by remember(initialMovement?.id) { mutableStateOf(initialMovement?.type == MovementType.REFUND) }
    var merchant by remember(initialMovement?.id) { mutableStateOf(initialMovement?.merchant.orEmpty()) }
    var note by remember(initialMovement?.id) { mutableStateOf(initialMovement?.note.orEmpty()) }
    var paymentMethod by remember(initialMovement?.id) { mutableStateOf(initialMovement?.paymentMethodId) }
    var currency by remember(initialMovement?.id) { mutableStateOf(initialMovement?.originalCurrencyCode ?: "SAR") }
    var originalAmount by remember(initialMovement?.id) { mutableStateOf(initialMovement?.originalAmountMinor?.let(::minorNumber).orEmpty()) }
    var confirmed by remember(initialMovement?.id) { mutableStateOf(initialMovement?.conversionStatus != ConversionStatus.ESTIMATED) }
    var localDate by remember(initialMovement?.id) { mutableStateOf((initialMovement?.localDate ?: state.currentLocalDate).toString()) }
    var localTime by remember(initialMovement?.id) {
        val instant = initialMovement?.occurredAtUtcMillis ?: state.currentInstantMillis
        val zone = ZoneId.of(initialMovement?.zoneId ?: "Asia/Riyadh")
        mutableStateOf(Instant.ofEpochMilli(instant).atZone(zone).toLocalTime().withSecond(0).withNano(0).toString())
    }
    var error by remember(initialMovement?.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (refund) "Nueva devolución" else "Nuevo gasto") },
        text = {
            LazyColumn(
                modifier = Modifier.testTag("movement_form"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    if (state.templates.any { !it.archived }) {
                        Text("Plantillas")
                        state.templates.filterNot { it.archived }.forEach { template ->
                            TextButton(onClick = {
                                amount = minorNumber(template.amountMinor)
                                selectedPocket = template.pocketId
                                paymentMethod = template.paymentMethodId
                            }) { Text(template.name) }
                        }
                    }
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("Importe SAR") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().testTag("movement_amount"),
                    )
                }
                items(state.pockets.filterNot { it.pocket.archived }) { pocket ->
                    OutlinedButton(
                        onClick = { selectedPocket = pocket.pocket.id },
                        modifier = Modifier.fillMaxWidth().testTag("movement_pocket_${pocket.pocket.name}"),
                    ) {
                        Text(if (selectedPocket == pocket.pocket.id) "✓ ${pocket.pocket.name}" else pocket.pocket.name)
                    }
                }
                item {
                    Text("Moneda original")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("SAR", "USD", "MXN").forEach { code ->
                            OutlinedButton(onClick = { currency = code }) { Text(if (currency == code) "✓ $code" else code) }
                        }
                    }
                }
                if (currency != "SAR") {
                    item {
                        OutlinedTextField(
                            originalAmount,
                            { originalAmount = it },
                            label = { Text("Importe original $currency") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { confirmed = false }) { Text(if (!confirmed) "✓ Estimado" else "Estimado") }
                            OutlinedButton(onClick = { confirmed = true }) { Text(if (confirmed) "✓ Confirmado" else "Confirmado") }
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { refund = false }) { Text("Gasto") }
                        OutlinedButton(onClick = { refund = true }) { Text("Devolución") }
                    }
                }
                item {
                    Text("Método de pago (opcional)")
                    state.paymentMethods.filterNot { it.archived }.forEach { method ->
                        TextButton(onClick = { paymentMethod = if (paymentMethod == method.id) null else method.id }) {
                            Text(if (paymentMethod == method.id) "✓ ${method.name}" else method.name)
                        }
                    }
                    OutlinedTextField(localDate, { localDate = it }, label = { Text("Fecha (AAAA-MM-DD)") })
                    OutlinedTextField(localTime, { localTime = it }, label = { Text("Hora (HH:mm)") })
                }
                item {
                    OutlinedTextField(merchant, { merchant = it }, label = { Text("Comercio (opcional)") })
                    OutlinedTextField(note, { note = it }, label = { Text("Nota (opcional)") })
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                scope.launch {
                    val parsed = runCatching { Money.parse(amount, "SAR").minor }.getOrNull() ?: run {
                        error = "Escribe un importe válido"
                        return@launch
                    }
                    val pocketId = selectedPocket ?: run {
                        error = "Selecciona un Pocket"
                        return@launch
                    }
                    val parsedDate = runCatching { java.time.LocalDate.parse(localDate) }.getOrNull() ?: run {
                        error = "Escribe una fecha válida"
                        return@launch
                    }
                    val parsedTime = runCatching { LocalTime.parse(localTime) }.getOrNull() ?: run {
                        error = "Escribe una hora válida"
                        return@launch
                    }
                    val parsedOriginal = if (currency == "SAR") {
                        null
                    } else {
                        runCatching { Money.parse(originalAmount, currency).minor }.getOrNull() ?: run {
                            error = "Escribe un importe original válido"
                            return@launch
                        }
                    }
                    val movementZone = ZoneId.of(initialMovement?.zoneId ?: "Asia/Riyadh")
                    val result = ledger.execute(
                        LedgerCommand.AddMovement(
                            pocketId = pocketId,
                            id = initialMovement?.id,
                            type = if (refund) MovementType.REFUND else MovementType.EXPENSE,
                            sarAmountMinor = parsed,
                            occurredAtUtcMillis = parsedDate.atTime(parsedTime).atZone(movementZone).toInstant().toEpochMilli(),
                            localDate = parsedDate,
                            merchant = merchant,
                            note = note,
                            paymentMethodId = paymentMethod,
                            originalAmountMinor = parsedOriginal,
                            originalCurrencyCode = currency,
                            conversionStatus = if (currency == "SAR" || confirmed) ConversionStatus.CONFIRMED else ConversionStatus.ESTIMATED,
                        )
                    )
                    when (result) {
                        LedgerResult.Success -> onSaved()
                        is LedgerResult.Rejected -> error = result.message
                        is LedgerResult.Deleted -> Unit
                    }
                }
            }) { Text(if (initialMovement != null) "Guardar cambios" else if (refund) "Guardar devolución" else "Guardar gasto") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun SettingsScreen(
    state: LedgerState,
    ledger: PocketLedger,
    preferences: AppPreferences,
    preferencesStore: PreferencesStore?,
    reminderScheduler: ReminderScheduler?,
    onCreateBackup: () -> Unit,
    onCreateCsv: () -> Unit,
    onPickBackup: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    padding: PaddingValues,
) {
    val scope = rememberCoroutineScope()
    var selectedFundsPeriodId by remember(state.currentPeriod?.id) { mutableStateOf(state.currentPeriod?.id) }
    var funds by remember(state.currentPeriod?.id) { mutableStateOf(minorNumber(state.newFundsMinor)) }
    var futureDay by remember(preferences.futurePeriodStartDay) { mutableStateOf(preferences.futurePeriodStartDay.toString()) }
    var reminderTime by remember(preferences.reminderTime) { mutableStateOf(preferences.reminderTime.toString()) }
    var methodName by remember { mutableStateOf("") }
    var editingMethod by remember { mutableStateOf<String?>(null) }
    var templateName by remember { mutableStateOf("") }
    var templateAmount by remember { mutableStateOf("") }
    var templatePocketId by remember { mutableStateOf<String?>(null) }
    var templateMethodId by remember { mutableStateOf<String?>(null) }
    var editingTemplate by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding).testTag("settings_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Ajustes", style = MaterialTheme.typography.headlineMedium) }
        item {
            Text("Periodo y fondos", style = MaterialTheme.typography.titleLarge)
            Text("${state.currentPeriod?.start} – ${state.currentPeriod?.endExclusive?.minusDays(1)} · Asia/Riyadh")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.periods, key = { it.id }) { period ->
                    TextButton(onClick = {
                        selectedFundsPeriodId = period.id
                        funds = minorNumber(period.newFundsMinor)
                    }) { Text(if (period.id == selectedFundsPeriodId) "✓ ${period.start}" else period.start.toString()) }
                }
            }
            OutlinedTextField(funds, { funds = it }, label = { Text("Fondos nuevos SAR") })
            Button(onClick = {
                scope.launch {
                    val value = runCatching { Money.parse(funds, "SAR").minor }.getOrNull() ?: run {
                        message = "Escribe fondos válidos"
                        return@launch
                    }
                    when (val result = ledger.execute(LedgerCommand.UpdatePeriodFunds(selectedFundsPeriodId ?: return@launch, value))) {
                        LedgerResult.Success -> message = "Fondos guardados"
                        is LedgerResult.Rejected -> message = result.message
                        is LedgerResult.Deleted -> Unit
                    }
                }
            }) { Text("Guardar fondos") }
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            OutlinedTextField(
                futureDay,
                { futureDay = it.filter(Char::isDigit).take(2) },
                label = { Text("Día de inicio para periodos futuros") },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    scope.launch { preferencesStore?.setFuturePeriodStartDay(futureDay.toIntOrNull() ?: 25) }
                }) { Text("Guardar día") }
                OutlinedButton(onClick = {
                    scope.launch {
                        val result = ledger.execute(LedgerCommand.CreateNextPeriod(futureDay.toIntOrNull()))
                        message = if (result is LedgerResult.Success) "Periodo siguiente creado con presupuestos y rollover." else (result as? LedgerResult.Rejected)?.message
                    }
                }) { Text("Crear periodo siguiente") }
            }
        }
        item {
            Text("Recordatorio diario", style = MaterialTheme.typography.titleLarge)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (preferences.reminderEnabled) "Activado" else "Desactivado")
                Switch(
                    checked = preferences.reminderEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            val time = runCatching { LocalTime.parse(reminderTime) }.getOrDefault(LocalTime.of(21, 0))
                            preferencesStore?.setReminder(enabled, time)
                            reminderScheduler?.apply(enabled, time)
                            if (enabled) onRequestNotificationPermission()
                        }
                    },
                    modifier = Modifier.testTag("reminder_switch"),
                )
            }
            OutlinedTextField(reminderTime, { reminderTime = it }, label = { Text("Hora (HH:mm)") }, modifier = Modifier.testTag("reminder_time"))
            Button(onClick = {
                scope.launch {
                    val time = runCatching { LocalTime.parse(reminderTime) }.getOrNull() ?: return@launch
                    preferencesStore?.setReminder(preferences.reminderEnabled, time)
                    reminderScheduler?.apply(preferences.reminderEnabled, time)
                }
            }) { Text("Guardar horario") }
            Text("El recordatorio no muestra importes en la pantalla bloqueada.")
        }
        item {
            Text("Métodos de pago", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(methodName, { methodName = it }, label = { Text("Nombre") })
            Button(onClick = {
                scope.launch {
                    when (val result = ledger.execute(LedgerCommand.UpsertPaymentMethod(editingMethod, methodName))) {
                        LedgerResult.Success -> { methodName = ""; editingMethod = null; message = "Método guardado" }
                        is LedgerResult.Rejected -> message = result.message
                        is LedgerResult.Deleted -> Unit
                    }
                }
            }) { Text(if (editingMethod == null) "Añadir método" else "Guardar método") }
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
        items(state.paymentMethods, key = { it.id }) { method ->
            Card(
                Modifier
                    .fillMaxWidth()
                    .testTag("payment_method_${method.name}")
                    .clickable { editingMethod = method.id; methodName = method.name },
            ) {
                Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(method.name + if (method.archived) " (archivado)" else "")
                    TextButton(onClick = { scope.launch { ledger.execute(LedgerCommand.ArchivePaymentMethod(method.id, !method.archived)) } }) {
                        Text(if (method.archived) "Restaurar" else "Archivar")
                    }
                }
            }
        }
        item {
            Text("Plantillas recurrentes", style = MaterialTheme.typography.titleLarge)
            Text("Solo precargan el formulario; nunca crean gastos automáticamente.")
            OutlinedTextField(templateName, { templateName = it }, label = { Text("Nombre de plantilla") })
            OutlinedTextField(templateAmount, { templateAmount = it }, label = { Text("Importe SAR") })
            Text("Pocket")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.pockets.filterNot { it.pocket.archived }, key = { it.pocket.id }) { pocket ->
                    OutlinedButton(
                        onClick = { templatePocketId = pocket.pocket.id },
                        modifier = Modifier.testTag("template_pocket_${pocket.pocket.name}"),
                    ) {
                        Text(if (templatePocketId == pocket.pocket.id) "✓ ${pocket.pocket.name}" else pocket.pocket.name)
                    }
                }
            }
            Text("Método de pago (opcional)")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { OutlinedButton(onClick = { templateMethodId = null }) { Text(if (templateMethodId == null) "✓ Ninguno" else "Ninguno") } }
                items(state.paymentMethods.filterNot { it.archived }, key = { it.id }) { method ->
                    OutlinedButton(
                        onClick = { templateMethodId = method.id },
                        modifier = Modifier.testTag("template_method_${method.name}"),
                    ) {
                        Text(if (templateMethodId == method.id) "✓ ${method.name}" else method.name)
                    }
                }
            }
            Button(onClick = {
                scope.launch {
                    val pocketId = templatePocketId ?: run { message = "Selecciona un Pocket"; return@launch }
                    val amount = runCatching { Money.parse(templateAmount, "SAR").minor }.getOrNull()
                        ?: run { message = "Escribe un importe válido"; return@launch }
                    when (val result = ledger.execute(LedgerCommand.UpsertTemplate(editingTemplate, templateName, amount, pocketId, templateMethodId))) {
                        LedgerResult.Success -> {
                            templateName = ""; templateAmount = ""; templatePocketId = null; templateMethodId = null
                            editingTemplate = null; message = "Plantilla guardada"
                        }
                        is LedgerResult.Rejected -> message = result.message
                        is LedgerResult.Deleted -> Unit
                    }
                }
            }) { Text(if (editingTemplate == null) "Añadir plantilla" else "Guardar plantilla") }
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
        items(state.templates, key = { it.id }) { template ->
            Card(Modifier.fillMaxWidth().clickable {
                editingTemplate = template.id
                templateName = template.name
                templateAmount = minorNumber(template.amountMinor)
                templatePocketId = template.pocketId
                templateMethodId = template.paymentMethodId
            }) {
                Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${template.name}: ${money(template.amountMinor)}${if (template.archived) " (archivada)" else ""}")
                    TextButton(onClick = { scope.launch { ledger.execute(LedgerCommand.ArchiveTemplate(template.id, !template.archived)) } }) {
                        Text(if (template.archived) "Restaurar" else "Archivar")
                    }
                }
            }
        }
        item {
            Text("Portabilidad", style = MaterialTheme.typography.titleLarge)
            Button(onClick = onCreateBackup) { Text("Crear backup completo") }
            OutlinedButton(onClick = onPickBackup) { Text("Restaurar backup") }
            OutlinedButton(onClick = onCreateCsv) { Text("Exportar CSV") }
            Text("El CSV no está cifrado y no sirve para restaurar.")
        }
    }
}

private val moneyFormat = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))
private fun money(minor: Long): String = "SAR ${minorNumber(minor)}"
private fun minorNumber(minor: Long): String =
    moneyFormat.format(java.math.BigDecimal.valueOf(minor).movePointLeft(2))
