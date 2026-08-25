package com.aif31.pocket

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton


import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import com.aif31.pocket.data.LedgerCommand
import com.aif31.pocket.data.LedgerResult
import com.aif31.pocket.data.LedgerState
import com.aif31.pocket.data.MovementType
import com.aif31.pocket.data.Movement
import com.aif31.pocket.data.ConversionStatus
import com.aif31.pocket.data.PocketIconKey
import com.aif31.pocket.data.PocketLedger
import com.aif31.pocket.data.PocketPeriodSummary
import com.aif31.pocket.domain.Money
import com.aif31.pocket.settings.AppPreferences
import com.aif31.pocket.settings.PreferencesStore
import com.aif31.pocket.settings.ReminderScheduler
import com.aif31.pocket.ui.ActionableDashboardContent
import com.aif31.pocket.ui.PocketArtwork
import com.aif31.pocket.ui.pocketIconOptions
import com.aif31.pocket.ui.ProductionSettingsHub
import com.aif31.pocket.ui.SettingsSection
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.format.DateTimeFormatter

import java.time.LocalTime

import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

@Serializable
private enum class RootScreen(val label: String, val icon: ImageVector) {
    DASHBOARD("Inicio", Icons.Default.Home),
    MOVEMENTS("Movimientos", Icons.AutoMirrored.Filled.ReceiptLong),
    POCKETS("Pockets", Icons.Default.AccountBalanceWallet),
    SETTINGS("Ajustes", Icons.Default.Settings),
}

private sealed interface PocketRoute : NavKey

@Serializable
private data class RootRoute(val screen: RootScreen) : PocketRoute

@Serializable
private data class MovementRoute(val movementId: String? = null) : PocketRoute

@Serializable
private data class SettingsDetailRoute(val section: SettingsSection) : PocketRoute

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
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        restoreError ?: if (preview.valid) "Versión ${preview.version}: ${preview.periods} periodos, ${preview.pockets} Pockets y ${preview.movements} movimientos."
                        else preview.message ?: "No se puede leer el archivo.",
                    )
                    if (restoreError == null && preview.valid && observedState?.needsOnboarding == false) {
                        Text(
                            "Esta acción reemplazará los datos actuales y puede eliminar información anterior. No se puede deshacer.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
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
                }) { Text(if (observedState?.needsOnboarding == false) "Restaurar y reemplazar" else "Restaurar") }
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

    val backStack = rememberNavBackStack(RootRoute(RootScreen.DASHBOARD))
    val currentRoute = backStack.last()
    val screen = backStack.filterIsInstance<RootRoute>().lastOrNull()?.screen ?: RootScreen.DASHBOARD
    val movementRoute = currentRoute as? MovementRoute
    val settingsSection = (currentRoute as? SettingsDetailRoute)?.section
    val snackbar = remember { SnackbarHostState() }
    val appScope = rememberCoroutineScope()

    fun navigateRoot(destination: RootScreen) {
        backStack.clear()
        backStack.add(RootRoute(destination))
    }

    LaunchedEffect(openNewExpense, state.currentPeriod?.id) {
        if (openNewExpense && state.currentPeriod != null && backStack.lastOrNull() !is MovementRoute) {
            backStack.add(MovementRoute())
        }
    }

    if (movementRoute != null) {
        val movementBeingEdited = state.movements.firstOrNull { it.id == movementRoute.movementId }
        BackHandler { backStack.removeLastOrNull() }
        MovementDialog(
            state = state,
            ledger = ledger,
            onDismiss = { backStack.removeLastOrNull() },
            onSaved = {
                navigateRoot(
                    if (movementRoute.movementId == null) RootScreen.DASHBOARD else RootScreen.MOVEMENTS,
                )
                appScope.launch {
                    snackbar.showSnackbar(
                        if (movementRoute.movementId == null) "Gasto guardado" else "Movimiento actualizado",
                    )
                }
            },
            initialMovement = movementBeingEdited,
        )
        return
    }

    BackHandler(enabled = backStack.size > 1) { backStack.removeLastOrNull() }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp
        val rootNavigationVisible = currentRoute is RootRoute
        Row(modifier = Modifier.fillMaxSize()) {
            if (useNavigationRail && rootNavigationVisible) {
                NavigationRail {
                    Spacer(Modifier.height(24.dp))
                    RootScreen.entries.forEach { destination ->
                        NavigationRailItem(
                            selected = screen == destination,
                            onClick = { navigateRoot(destination) },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
            Scaffold(
                modifier = Modifier.fillMaxSize().weight(1f),
                snackbarHost = { SnackbarHost(snackbar) },
                floatingActionButton = {
                    if (rootNavigationVisible) {
                        when (screen) {
                            RootScreen.DASHBOARD -> ExtendedFloatingActionButton(
                                onClick = { backStack.add(MovementRoute()) },
                                icon = { Icon(Icons.Default.Add, contentDescription = "Registrar gasto") },
                                text = { Text("Registrar gasto") },
                                modifier = Modifier.testTag("contextual_add"),
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onTertiary,
                            )
                            RootScreen.MOVEMENTS -> FloatingActionButton(
                                onClick = { backStack.add(MovementRoute()) },
                                modifier = Modifier.testTag("contextual_add"),
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onTertiary,
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Registrar gasto")
                            }
                            else -> Unit
                        }
                    }
                },
                floatingActionButtonPosition = FabPosition.End,
                bottomBar = {
                    if (!useNavigationRail && rootNavigationVisible) {
                        NavigationBar {
                            RootScreen.entries.forEach { destination ->
                                NavigationBarItem(
                                    selected = screen == destination,
                                    onClick = { navigateRoot(destination) },
                                    icon = { Icon(destination.icon, contentDescription = destination.label) },
                                    label = { Text(destination.label) },
                                )
                            }
                        }
                    }
                },
            ) { padding ->
                when (screen) {
                    RootScreen.DASHBOARD -> DashboardScreen(
                        state = state,
                        padding = padding,
                        onManagePockets = { navigateRoot(RootScreen.POCKETS) },
                    )
                    RootScreen.MOVEMENTS -> MovementsScreen(
                        state = state,
                        ledger = ledger,
                        snackbar = snackbar,
                        padding = padding,
                        undoWindowMillis = undoWindowMillis,
                        onRecordExpense = { backStack.add(MovementRoute()) },
                        onEditMovement = { backStack.add(MovementRoute(it.id)) },
                    )
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
                        section = settingsSection,
                        onSectionChange = { section ->
                            if (section == null) {
                                if (backStack.lastOrNull() is SettingsDetailRoute) backStack.removeLastOrNull()
                            } else {
                                backStack.add(SettingsDetailRoute(section))
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingScreen(ledger: PocketLedger, preferences: PreferencesStore?, onPickBackup: () -> Unit) {
    var funds by remember { mutableStateOf("") }
    var startDay by remember { mutableStateOf("25") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Pocket",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "Configura tu primer periodo",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    "Empieza con tus fondos del periodo. Después podrás repartirlos entre Pockets.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
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
                            supportingText = { Text("El periodo se renovará cada mes en este día.") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().testTag("start_day"),
                        )
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }
                Button(
                    modifier = Modifier.fillMaxWidth().height(52.dp),
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
                            }.onFailure {
                                error = "Revisa los fondos y el día de inicio"
                            }
                        }
                    },
                ) {
                    Text("Comenzar")
                }
                TextButton(onClick = onPickBackup, modifier = Modifier.fillMaxWidth()) {
                    Text("Restaurar backup")
                }
                Text(
                    "Sin cuenta ni conexión. Tus datos permanecen en este dispositivo.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun DashboardScreen(
    state: LedgerState,
    padding: PaddingValues,
    onManagePockets: () -> Unit,
) {
    ActionableDashboardContent(
        state = state,
        contentPadding = padding,
        onManagePockets = onManagePockets,
    )
}

@Composable
private fun MovementsScreen(
    state: LedgerState,
    ledger: PocketLedger,
    snackbar: SnackbarHostState,
    padding: PaddingValues,
    undoWindowMillis: Long,
    onRecordExpense: () -> Unit,
    onEditMovement: (Movement) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var periodIndex by remember { mutableStateOf(0) }
    var pocketIndex by remember { mutableStateOf(0) }
    var currencyIndex by remember { mutableStateOf(0) }
    var methodIndex by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf<Movement?>(null) }

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
    val periodLabels = listOf("Todos los periodos") + state.periods.map { it.start.toString() }
    val pocketLabels = listOf("Todos los Pockets") + state.pockets.map { it.pocket.name }
    val currencyLabels = listOf("Todas las monedas") + currencyOptions.drop(1).map { it.orEmpty() }
    val methodLabels = listOf("Todos los métodos") + state.paymentMethods.map { it.name }
    val groupedMovements = filtered.groupBy { it.localDate }.entries.sortedByDescending { it.key }
    val filtersActive = query.isNotBlank() || periodIndex != 0 || pocketIndex != 0 ||
        currencyIndex != 0 || methodIndex != 0
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text("Movimientos", style = MaterialTheme.typography.headlineMedium)
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Buscar comercio o nota") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth().testTag("history_search"),
            )
        }
        item {
            Text("Filtros", style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { HistoryFilter("filter_period", periodLabels, periodIndex) { periodIndex = it } }
                item { HistoryFilter("filter_pocket", pocketLabels, pocketIndex) { pocketIndex = it } }
                item { HistoryFilter("filter_currency", currencyLabels, currencyIndex) { currencyIndex = it } }
                item { HistoryFilter("filter_method", methodLabels, methodIndex) { methodIndex = it } }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${filtered.size} ${if (filtered.size == 1) "movimiento" else "movimientos"}", color = MaterialTheme.colorScheme.primary)
                TextButton(
                    onClick = {
                        query = ""
                        periodIndex = 0
                        pocketIndex = 0
                        currencyIndex = 0
                        methodIndex = 0
                    },
                    enabled = filtersActive,
                    modifier = Modifier.testTag("clear_filters"),
                ) {
                    Text("Limpiar filtros")
                }
            }
        }
        if (filtered.isEmpty()) item { Text("No hay movimientos para estos filtros") }
        groupedMovements.forEach { (date, movements) ->
            item(key = "date-$date") {
                Text(
                    formatMovementDate(date, state.currentLocalDate),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            items(movements, key = { it.id }) { movement ->
                MovementCard(
                    movement = movement,
                    iconKey = state.pockets.firstOrNull { it.pocket.id == movement.pocketId }?.pocket?.iconKey
                        ?: PocketIconKey.forName(movement.pocketName),
                    onClick = { selected = movement },
                )
            }
        }
    }
    selected?.let { movement ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(movement.pocketName) },
            text = { Text("${if (movement.type == MovementType.EXPENSE) "Gasto" else "Devolución"} ${money(movement.sarAmountMinor)}\n${movement.localDate}") },
            confirmButton = {
                TextButton(onClick = { onEditMovement(movement); selected = null }) { Text("Editar") }
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

}

@Composable
private fun MovementCard(movement: Movement, iconKey: PocketIconKey, onClick: () -> Unit) {
    val isRefund = movement.type == MovementType.REFUND
    val amountColor = if (isRefund) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val time = Instant.ofEpochMilli(movement.occurredAtUtcMillis)
        .atZone(java.time.ZoneId.of(movement.zoneId))
        .format(DateTimeFormatter.ofPattern("HH:mm"))

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = if (isRefund) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(52.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isRefund) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    } else {
                        PocketArtwork(iconKey, contentDescription = null, modifier = Modifier.size(42.dp))
                    }
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    movement.merchant?.takeIf { it.isNotBlank() }
                        ?: if (isRefund) "Devolución ${movement.pocketName}" else movement.pocketName,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    listOfNotNull(movement.pocketName, movement.paymentMethodName).joinToString(" · "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                movement.note?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    (if (isRefund) "+ " else "- ") + money(movement.sarAmountMinor),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    color = amountColor,
                )
                if (movement.originalCurrencyCode != "SAR" && movement.originalAmountMinor != null) {
                    Text(
                        "${movement.originalCurrencyCode} ${minorNumber(movement.originalAmountMinor)} · " +
                            if (movement.conversionStatus == ConversionStatus.CONFIRMED) "Confirmado" else "Estimado",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(if (isRefund) "Devolución" else "Confirmado", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Text(time, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun HistoryFilter(
    testTag: String,
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.testTag(testTag),
        ) {
            Text(labels.getOrElse(selectedIndex) { labels.first() })
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            labels.forEachIndexed { index, label ->
                DropdownMenuItem(
                    text = { Text(if (index == selectedIndex) "✓ $label" else label) },
                    onClick = {
                        onSelected(index)
                        expanded = false
                    },
                    modifier = Modifier.testTag("${testTag}_option_$index"),
                )
            }
        }
    }
}

@Composable
private fun PocketsScreen(state: LedgerState, ledger: PocketLedger, padding: PaddingValues) {
    var selected by remember { mutableStateOf<PocketPeriodSummary?>(null) }
    var selectedPeriodId by remember(state.currentPeriod?.id) { mutableStateOf(state.currentPeriod?.id) }
    var editing by remember { mutableStateOf<PocketPeriodSummary?>(null) }
    var creating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val selectedPeriod = state.periods.firstOrNull { it.id == selectedPeriodId } ?: state.currentPeriod
    val shownPockets = state.pocketSummariesByPeriod[selectedPeriodId].orEmpty()
    val activePockets = shownPockets.filterNot { it.pocket.archived }
    val allocatedMinor = shownPockets.sumOf { it.budgetMinor }
    val availableMinor = shownPockets.sumOf { it.availabilityMinor }
    val periodFundsMinor = selectedPeriod?.newFundsMinor ?: state.newFundsMinor
    val unallocatedForPeriodMinor = periodFundsMinor - allocatedMinor

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding).testTag("pockets_list"),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("Pockets", style = MaterialTheme.typography.headlineMedium)
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.periods, key = { it.id }) { period ->
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = if (selectedPeriodId == period.id) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        },
                        modifier = Modifier.clickable { selectedPeriodId = period.id },
                    ) {
                        Text(
                            formatPeriodRange(period.start, period.endExclusive.minusDays(1)),
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PocketSummaryMetric("Asignado", money(allocatedMinor), Modifier.weight(1f))
                        VerticalDivider(Modifier.height(64.dp))
                        PocketSummaryMetric("Disponible", money(availableMinor), Modifier.weight(1f))
                    }
                    Text(
                        "${money(unallocatedForPeriodMinor)} sin asignar",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Button(
                onClick = { creating = true },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                ),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Crear Pocket", style = MaterialTheme.typography.titleMedium)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Pockets activos", style = MaterialTheme.typography.titleLarge)
                Text("Toca uno para ver y administrar", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(activePockets, key = { it.pocket.id }) { summary ->
            val statusText = when {
                summary.exhausted -> "Agotado"
                summary.atRisk -> "En riesgo"
                summary.budgetMinor <= 0L -> "Sin presupuesto"
                else -> "En buen ritmo"
            }
            val statusColor = when {
                summary.exhausted -> MaterialTheme.colorScheme.error
                summary.atRisk || summary.budgetMinor <= 0L -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.primary
            }
            Card(
                Modifier
                    .fillMaxWidth()
                    .testTag("pocket_${summary.pocket.name}")
                    .clickable { selected = summary },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    PocketGlyph(summary.pocket.iconKey)
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(summary.pocket.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            IconButton(
                                onClick = { selected = summary },
                                modifier = Modifier.semantics { contentDescription = "Gestionar ${summary.pocket.name}" },
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = null)
                            }
                        }
                        Text(
                            "${money(summary.availabilityMinor)} disponibles",
                            color = statusColor,
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Monospace,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LinearProgressIndicator(
                                progress = { (summary.consumedPercent / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier.weight(1f).height(6.dp),
                                color = statusColor,
                            )
                            Text("${summary.consumedPercent}%", color = statusColor)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Presupuesto ${money(summary.budgetMinor)}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Surface(
                                shape = MaterialTheme.shapes.extraLarge,
                                color = if (summary.budgetMinor <= 0L) {
                                    MaterialTheme.colorScheme.tertiaryContainer
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer
                                },
                            ) {
                                Text(
                                    statusText,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    color = statusColor,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (activePockets.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Aún no hay Pockets activos", style = MaterialTheme.typography.titleMedium)
                        Text("Crea un Pocket para asignar fondos y seguir su disponibilidad.")
                    }
                }
            }
        }
        if (shownPockets.any { it.pocket.archived }) {
            item { Text("Archivados", style = MaterialTheme.typography.titleMedium) }
            items(shownPockets.filter { it.pocket.archived }, key = { "archived-${it.pocket.id}" }) { summary ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(summary.pocket.name)
                    TextButton(onClick = { scope.launch { ledger.execute(LedgerCommand.ArchivePocket(summary.pocket.id, false)) } }) {
                        Text("Restaurar")
                    }
                }
            }
        }
    }
    selected?.let { summary ->
        PocketManagementDialog(
            state = state,
            periodId = selectedPeriodId ?: state.currentPeriod!!.id,
            summary = summary,
            ledger = ledger,
            onEdit = { selected = null; editing = summary },
            onDismiss = { selected = null },
        )
    }
    if (creating || editing != null) {
        PocketEditorDialog(editing, ledger) { creating = false; editing = null }
    }
}

@Composable
private fun PocketSummaryMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun PocketGlyph(iconKey: PocketIconKey) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(56.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            PocketArtwork(iconKey, contentDescription = null, modifier = Modifier.size(46.dp))
        }
    }
}

private fun formatPeriodRange(start: java.time.LocalDate, end: java.time.LocalDate): String {
    val formatter = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es"))
    return "${start.format(formatter)} – ${end.format(formatter)}"
}

@Composable
private fun PocketEditorDialog(
    existing: PocketPeriodSummary?,
    ledger: PocketLedger,
    onDismiss: () -> Unit,
) {
    var name by remember(existing?.pocket?.id) { mutableStateOf(existing?.pocket?.name.orEmpty()) }
    var rollover by remember(existing?.pocket?.id) { mutableStateOf(existing?.pocket?.rolloverEnabled ?: false) }
    var selectedIcon by remember(existing?.pocket?.id) { mutableStateOf(existing?.pocket?.iconKey ?: PocketIconKey.SUPERMARKET) }
    var error by remember(existing?.pocket?.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Crear Pocket" else "Editar Pocket") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("pocket_name"),
                )
                Text("Elige un icono", style = MaterialTheme.typography.titleSmall)
                pocketIconOptions.chunked(3).forEach { options ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        options.forEach { option ->
                            val isSelected = selectedIcon == option.key
                            OutlinedButton(
                                onClick = { selectedIcon = option.key },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(72.dp)
                                    .testTag("pocket_icon_${option.key.name.lowercase(Locale.ROOT)}")
                                    .semantics {
                                        contentDescription = "Icono ${option.label}${if (isSelected) ", seleccionado" else ""}"
                                    },
                                contentPadding = PaddingValues(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                ),
                            ) {
                                PocketArtwork(option.key, contentDescription = null, modifier = Modifier.size(48.dp))
                            }
                        }
                    }
                }
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
                    when (val result = ledger.execute(LedgerCommand.UpsertPocket(existing?.pocket?.id, name, rollover, selectedIcon))) {
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
private fun PocketManagementDialog(
    state: LedgerState,
    periodId: String,
    summary: PocketPeriodSummary,
    ledger: PocketLedger,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    var amount by remember(summary.pocket.id, periodId) { mutableStateOf(minorNumber(summary.budgetMinor)) }
    var error by remember(summary.pocket.id, periodId) { mutableStateOf<String?>(null) }
    var confirmingArchive by rememberSaveable(summary.pocket.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(summary.pocket.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Gestiona el presupuesto y las opciones de este Pocket sin saturar la vista general.")
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it; error = null },
                    label = { Text("Presupuesto SAR") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("allocation_amount"),
                )
                Text("Fondos del periodo: ${money(state.periods.firstOrNull { it.id == periodId }?.newFundsMinor ?: 0)}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { scope.launch { ledger.execute(LedgerCommand.MovePocket(summary.pocket.id, -1)) } },
                        modifier = Modifier.semantics { contentDescription = "Mover ${summary.pocket.name} arriba" },
                    ) { Text("Subir") }
                    TextButton(
                        onClick = { scope.launch { ledger.execute(LedgerCommand.MovePocket(summary.pocket.id, 1)) } },
                        modifier = Modifier.semantics { contentDescription = "Mover ${summary.pocket.name} abajo" },
                    ) { Text("Bajar") }
                }
                OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("Editar Pocket") }
                if (confirmingArchive) {
                    Text("¿Archivar este Pocket? Sus movimientos se conservarán.", color = MaterialTheme.colorScheme.error)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { confirmingArchive = false }) { Text("Conservar") }
                        Button(onClick = {
                            scope.launch {
                                when (val result = ledger.execute(LedgerCommand.ArchivePocket(summary.pocket.id))) {
                                    LedgerResult.Success -> onDismiss()
                                    is LedgerResult.Rejected -> error = result.message
                                    is LedgerResult.Deleted -> Unit
                                }
                            }
                        }) { Text("Confirmar archivo") }
                    }
                } else {
                    TextButton(onClick = { confirmingArchive = true }) { Text("Archivar Pocket") }
                }
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
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
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
    ProductionMovementScreen(
        state = state,
        ledger = ledger,
        onDismiss = onDismiss,
        onSaved = onSaved,
        initialMovement = initialMovement,
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
    section: SettingsSection?,
    onSectionChange: (SettingsSection?) -> Unit,
) {
    val selectedSection = section
    if (selectedSection == null) {
        ProductionSettingsHub(
            contentPadding = padding,
            onOpenSection = { onSectionChange(it) },
        )
        return
    }
    SettingsDetailScreen(
        state = state,
        ledger = ledger,
        preferences = preferences,
        preferencesStore = preferencesStore,
        reminderScheduler = reminderScheduler,
        onCreateBackup = onCreateBackup,
        onCreateCsv = onCreateCsv,
        onPickBackup = onPickBackup,
        onRequestNotificationPermission = onRequestNotificationPermission,
        padding = padding,
        section = selectedSection,
        onBack = { onSectionChange(null) },
    )
}

@Composable
private fun SettingsDetailScreen(
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
    section: SettingsSection,
    onBack: () -> Unit,
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
    var reminderPermissionRationaleVisible by rememberSaveable { mutableStateOf(false) }
    val selectedFundsPeriod = state.periods.firstOrNull { it.id == selectedFundsPeriodId }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding).testTag("settings_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onBack) { Text("Atrás") }
                Text(section.title, style = MaterialTheme.typography.headlineMedium)
            }
        }
        if (section == SettingsSection.PERIOD) {
            item {
                Text("Periodo y fondos", style = MaterialTheme.typography.titleLarge)
            Text("${selectedFundsPeriod?.start} – ${selectedFundsPeriod?.endExclusive?.minusDays(1)} · Asia/Riyadh")
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
                    val day = futureDay.toIntOrNull()
                    if (day == null || day !in 1..31) {
                        message = "Escribe un día entre 1 y 31"
                    } else {
                        scope.launch {
                            preferencesStore?.setFuturePeriodStartDay(day)
                            message = "Día de inicio guardado"
                        }
                    }
                }) { Text("Guardar día") }
                OutlinedButton(onClick = {
                    scope.launch {
                        val result = ledger.execute(LedgerCommand.CreateNextPeriod(futureDay.toIntOrNull()))
                        message = if (result is LedgerResult.Success) "Periodo siguiente creado con presupuestos y rollover." else (result as? LedgerResult.Rejected)?.message
                    }
                }) { Text("Crear periodo siguiente") }
            }
            }
        }
        if (section == SettingsSection.REMINDERS) {
            item {
                Text("Recordatorio diario", style = MaterialTheme.typography.titleLarge)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (preferences.reminderEnabled) "Activado" else "Desactivado")
                Switch(
                    checked = preferences.reminderEnabled,
                    onCheckedChange = { enabled ->
                        val time = runCatching { LocalTime.parse(reminderTime) }.getOrNull()
                        if (time == null) {
                            message = "Escribe una hora válida en formato HH:mm"
                        } else {
                            scope.launch {
                                preferencesStore?.setReminder(enabled, time)
                                reminderScheduler?.apply(enabled, time)
                                message = if (enabled) "Recordatorio activado" else "Recordatorio desactivado"
                                reminderPermissionRationaleVisible = enabled
                            }
                        }
                    },
                    modifier = Modifier.testTag("reminder_switch"),
                )
            }
            OutlinedTextField(reminderTime, { reminderTime = it }, label = { Text("Hora (HH:mm)") }, modifier = Modifier.testTag("reminder_time"))
            Button(onClick = {
                val time = runCatching { LocalTime.parse(reminderTime) }.getOrNull()
                if (time == null) {
                    message = "Escribe una hora válida en formato HH:mm"
                } else {
                    scope.launch {
                        preferencesStore?.setReminder(preferences.reminderEnabled, time)
                        reminderScheduler?.apply(preferences.reminderEnabled, time)
                        message = "Horario guardado"
                    }
                }
            }) { Text("Guardar horario") }
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            Text("El recordatorio no muestra importes en la pantalla bloqueada.")
            if (reminderPermissionRationaleVisible) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Permiso de notificaciones", style = MaterialTheme.typography.titleMedium)
                        Text("Pocket usa este permiso solo para enviar el recordatorio diario que acabas de activar. No muestra importes ni comparte tus datos.")
                        Button(onClick = {
                            reminderPermissionRationaleVisible = false
                            onRequestNotificationPermission()
                        }) { Text("Permitir notificaciones") }
                    }
                }
            }
            }
        }
        if (section == SettingsSection.PAYMENT_METHODS) {
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
        }
        if (section == SettingsSection.TEMPLATES) {
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
        }
        if (section == SettingsSection.DATA) {
            item {
                Text("Portabilidad", style = MaterialTheme.typography.titleLarge)
            Button(onClick = onCreateBackup) { Text("Crear backup completo") }
            OutlinedButton(onClick = onPickBackup) { Text("Restaurar backup") }
            OutlinedButton(onClick = onCreateCsv) { Text("Exportar CSV") }
                Text("El CSV no está cifrado y no sirve para restaurar.")
            }
        }
    }
}

private val moneyFormat = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))
private fun formatMovementDate(date: java.time.LocalDate, today: java.time.LocalDate): String {
    val formatter = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es"))
    val formatted = date.format(formatter)
    return if (date == today) "Hoy, $formatted" else formatted
}

private fun money(minor: Long): String = "SAR ${minorNumber(minor)}"
private fun minorNumber(minor: Long): String =
    moneyFormat.format(java.math.BigDecimal.valueOf(minor).movePointLeft(2))
