package com.aif31.pocket

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton


import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import com.aif31.pocket.data.LedgerCommand
import com.aif31.pocket.data.LedgerResult
import com.aif31.pocket.data.LedgerState
import com.aif31.pocket.data.Movement
import com.aif31.pocket.data.PocketLedger
import com.aif31.pocket.domain.Money
import com.aif31.pocket.settings.AppPreferences
import com.aif31.pocket.settings.PreferencesStore
import com.aif31.pocket.settings.ReminderScheduler
import com.aif31.pocket.ui.ActionableDashboardContent
import com.aif31.pocket.ui.SettingsSection
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first

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
    operationMessage: String? = null,
    onOperationMessageHandled: () -> Unit = {},
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
    var restoreError by rememberSaveable { mutableStateOf<String?>(null) }
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
                                val latest = restored.periods.maxByOrNull { it.start }
                                val preferredStartDay = latest?.configuredStartDay ?: preferenceState.futurePeriodStartDay
                                preferences?.setFuturePeriodStartDay(preferredStartDay)
                                val today = ledger.movementDefaults().localDate
                                if (restored.periods.none { today >= it.start && today < it.endExclusive } &&
                                    today < restored.periods.minOf { it.start }
                                ) {
                                    restoreError = "El backup empieza después de la fecha actual"
                                } else {
                                    when (val catchUp = ledger.execute(LedgerCommand.CatchUpPeriods(preferredStartDay))) {
                                        LedgerResult.Success -> {
                                            ledger.state.first { it.currentPeriod != null }
                                            onRestoreCandidateHandled()
                                            backupPreview = null
                                            restoreError = null
                                        }
                                        is LedgerResult.Rejected -> restoreError = catchUp.message
                                        is LedgerResult.Deleted -> Unit
                                    }
                                }
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
    if (state.currentPeriod == null) {
        Text("Actualizando periodo…", modifier = Modifier.padding(24.dp))
        return
    }

    val backStack = rememberNavBackStack(RootRoute(RootScreen.DASHBOARD))
    val currentRoute = backStack.last()
    val screen = backStack.filterIsInstance<RootRoute>().lastOrNull()?.screen ?: RootScreen.DASHBOARD
    val movementRoute = currentRoute as? MovementRoute
    val settingsSection = (currentRoute as? SettingsDetailRoute)?.section
    val snackbar = remember { SnackbarHostState() }
    val appScope = rememberCoroutineScope()

    LaunchedEffect(operationMessage) {
        operationMessage?.let {
            snackbar.showSnackbar(it)
            onOperationMessageHandled()
        }
    }

    fun navigateRoot(destination: RootScreen) {
        backStack.clear()
        backStack.add(RootRoute(destination))
    }

    LaunchedEffect(openNewExpense, state.currentPeriod.id) {
        if (openNewExpense && backStack.lastOrNull() !is MovementRoute) {
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
    var funds by rememberSaveable { mutableStateOf("") }
    var startDay by rememberSaveable { mutableStateOf("25") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
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
        movementDefaults = ledger.movementDefaults(),
        initialMovement = initialMovement,
    )
}
