package com.aif31.pocket

import androidx.activity.compose.BackHandler
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.aif31.pocket.ui.ChoiceOption
import com.aif31.pocket.ui.SegmentedChoice
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import com.aif31.pocket.data.LedgerCommand
import com.aif31.pocket.data.LedgerResult
import com.aif31.pocket.data.LedgerState
import com.aif31.pocket.data.Movement
import com.aif31.pocket.data.PeriodComparison
import com.aif31.pocket.data.PocketLedger
import com.aif31.pocket.domain.Money
import com.aif31.pocket.domain.SupportedCurrency
import com.aif31.pocket.settings.AppPreferences
import com.aif31.pocket.settings.PreferencesStore
import com.aif31.pocket.settings.ReminderScheduler
import com.aif31.pocket.ui.ActionableDashboardContent
import com.aif31.pocket.ui.SettingsSection
import com.aif31.pocket.ui.counted
import java.util.UUID
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import com.aif31.pocket.fx.ExchangeRateRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.unit.sp

@Serializable
private enum class RootScreen(val label: String, val icon: ImageVector) {
    DASHBOARD("Inicio", Icons.Default.Home),
    MOVEMENTS("Movimientos", Icons.AutoMirrored.Filled.ReceiptLong),
    POCKETS("Pockets", Icons.Default.AccountBalanceWallet),
    SETTINGS("Ajustes", Icons.Default.Settings),
}

private enum class SafetyExportStatus { UNRESOLVED, REQUESTED, CREATED, SKIPPED, FAILED }

private sealed interface PocketRoute : NavKey

@Serializable
private data class RootRoute(val screen: RootScreen) : PocketRoute

@Serializable
private data class MovementRoute(
    val movementId: String? = null,
    val suggestionId: String? = null,
    val pocketId: String? = null,
    /**
     * Identifies this form instance so its draft survives while another form is stacked above it. Required
     * rather than defaulted: serialization omits defaults, so a default would be regenerated on restore.
     */
    val instanceId: String,
) : PocketRoute {
    val isNewExpense: Boolean get() = movementId == null && suggestionId == null
}

private fun movementForm(movementId: String? = null, suggestionId: String? = null, pocketId: String? = null) =
    MovementRoute(movementId, suggestionId, pocketId, instanceId = UUID.randomUUID().toString())

@Serializable
private data class ComparisonRoute(val periodId: String, val baselinePeriodId: String?) : PocketRoute

@Serializable
private data class SettingsDetailRoute(val section: SettingsSection) : PocketRoute

@Composable
fun PocketApp(
    ledger: PocketLedger,
    preferences: PreferencesStore? = null,
    exchangeRates: ExchangeRateRepository? = null,
    reminderScheduler: ReminderScheduler? = null,
    openNewExpense: Boolean = false,
    /** Increments once per "new expense" launch (shortcut or onNewIntent); each value opens quick entry once. */
    newExpenseRequest: Int = if (openNewExpense) 1 else 0,
    restoreCandidate: ByteArray? = null,
    onRestoreCandidateHandled: () -> Unit = {},
    operationMessage: String? = null,
    backupExportSucceeded: Boolean? = null,
    onBackupExportResultHandled: () -> Unit = {},
    operationRetryLabel: String? = null,
    onOperationMessageHandled: () -> Unit = {},
    onRetryOperation: () -> Unit = {},
    onCreateBackup: () -> Unit = {},
    onShareBackup: () -> Unit = {},
    onCreateCsv: () -> Unit = {},
    onPickBackup: () -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {},
    notificationPermissionRevision: Int = 0,
    openMovementsRevision: Int = 0,
    onSuccessfulRestore: () -> Unit = {},
    onRestoreCompleted: (String) -> Unit = {},
    undoWindowMillis: Long = 5_000,
) {
    val observedState by ledger.state.collectAsStateWithLifecycle(initialValue = null)
    // Startup ends when the first real screen can show: onboarding or a ledger with its current period.
    ReportDrawnWhen { observedState?.let { it.needsOnboarding || it.currentPeriod != null } == true }
    val preferencesFlow = remember(preferences) { preferences?.state ?: flowOf(AppPreferences()) }
    val preferenceState by preferencesFlow.collectAsStateWithLifecycle(initialValue = AppPreferences())
    val backupScope = rememberCoroutineScope()
    var pendingBackupOperation by rememberSaveable { mutableStateOf<String?>(null) }
    var safetyStatus by rememberSaveable { mutableStateOf(SafetyExportStatus.UNRESOLVED) }
    var safetyCandidateHash by rememberSaveable { mutableStateOf<Int?>(null) }
    fun cancelBackupDisclosure() {
        pendingBackupOperation = null
        if (safetyStatus == SafetyExportStatus.REQUESTED) safetyStatus = SafetyExportStatus.FAILED
    }
    fun requestBackup(operation: String) {
        if (preferences == null || preferenceState.plaintextBackupAcknowledged) {
            if (operation == "create") onCreateBackup() else onShareBackup()
        } else {
            pendingBackupOperation = operation
        }
    }
    pendingBackupOperation?.let { operation ->
        AlertDialog(
            onDismissRequest = ::cancelBackupDisclosure,
            title = { Text("Backup en texto claro") },
            text = { Text("Cualquiera que tenga el archivo puede leer tus datos financieros. Guárdalo en un lugar seguro antes de continuar.") },
            confirmButton = {
                Button(onClick = {
                    backupScope.launch {
                        preferences?.acknowledgePlaintextBackup()
                        pendingBackupOperation = null
                        if (operation == "create") onCreateBackup() else onShareBackup()
                    }
                }) { Text("Entendido, continuar") }
            },
            dismissButton = { TextButton(onClick = ::cancelBackupDisclosure) { Text("Cancelar") } },
        )
    }
    var backupPreview by remember { mutableStateOf<com.aif31.pocket.data.BackupPreview?>(null) }
    var restoreError by rememberSaveable { mutableStateOf<String?>(null) }
    var restoreInProgress by remember { mutableStateOf(false) }
    LaunchedEffect(backupExportSucceeded) {
        if (safetyStatus == SafetyExportStatus.REQUESTED && backupExportSucceeded != null) {
            safetyStatus = if (backupExportSucceeded) SafetyExportStatus.CREATED else SafetyExportStatus.FAILED
            onBackupExportResultHandled()
        }
    }
    LaunchedEffect(restoreCandidate) {
        restoreCandidate?.contentHashCode()?.let { hash ->
            if (safetyCandidateHash != hash) {
                safetyCandidateHash = hash
                safetyStatus = SafetyExportStatus.UNRESOLVED
            }
        }
        restoreError = null
        backupPreview = restoreCandidate?.let { ledger.previewBackup(it) }
    }
    operationMessage?.takeIf { observedState?.needsOnboarding == false }?.let { message ->
        AlertDialog(
            onDismissRequest = onOperationMessageHandled,
            title = { Text(if (operationRetryLabel == null) "Operación de documentos" else "La operación falló") },
            text = { Text(message) },
            confirmButton = {
                if (operationRetryLabel == null) {
                    TextButton(onClick = onOperationMessageHandled) { Text("Aceptar") }
                } else {
                    Button(onClick = onRetryOperation) { Text(operationRetryLabel) }
                }
            },
            dismissButton = if (operationRetryLabel == null) null else {
                { TextButton(onClick = onOperationMessageHandled) { Text("Cerrar") } }
            },
        )
    }
    if (restoreCandidate != null && backupPreview != null && pendingBackupOperation == null) {
        val preview = backupPreview!!
        val scope = rememberCoroutineScope()
        AlertDialog(
            onDismissRequest = {
                if (!restoreInProgress) {
                    onRestoreCandidateHandled(); backupPreview = null; restoreError = null
                }
            },
            title = { Text(if (restoreError != null) "No se pudo restaurar" else if (preview.valid) "Confirmar restauración" else "Backup inválido") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        restoreError ?: if (preview.valid) "Versión ${preview.version}: ${preview.contentSummary()}."
                        else preview.message ?: "No se puede leer el archivo.",
                    )
                    if (restoreError == null && preview.valid && observedState?.needsOnboarding == false) {
                        Text(
                            "Esta acción reemplazará los datos actuales y puede eliminar información anterior. No se puede deshacer.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        when (safetyStatus) {
                            SafetyExportStatus.UNRESOLVED, SafetyExportStatus.FAILED -> {
                                if (safetyStatus == SafetyExportStatus.FAILED) Text("El backup de seguridad no se completó.")
                                TextButton(onClick = { safetyStatus = SafetyExportStatus.REQUESTED; requestBackup("create") }) {
                                    Text("Crear backup de seguridad")
                                }
                                TextButton(onClick = { safetyStatus = SafetyExportStatus.SKIPPED }) { Text("Continuar sin backup") }
                            }
                            SafetyExportStatus.REQUESTED -> Text("Esperando el resultado del backup de seguridad…")
                            SafetyExportStatus.CREATED -> Text("Backup de seguridad creado.")
                            SafetyExportStatus.SKIPPED -> Text("Continuarás sin backup de seguridad.")
                        }
                    }
                }
            },
            confirmButton = {
                if (preview.valid) Button(
                    enabled = !restoreInProgress && (observedState?.needsOnboarding == true || safetyStatus == SafetyExportStatus.CREATED || safetyStatus == SafetyExportStatus.SKIPPED),
                    onClick = {
                    restoreInProgress = true
                    scope.launch {
                        try {
                            when (val result = ledger.restoreBackup(restoreCandidate)) {
                                LedgerResult.Success -> withContext(NonCancellable) {
                                    val restored = ledger.state.first { it.currentPeriod != null }
                                    val restoredSettings = preview.portableSettings ?: com.aif31.pocket.data.PortableSettings(
                                        restored.periods.maxBy { it.start }.configuredStartDay,
                                    )
                                    val preferenceWarning = try {
                                        preferences?.applyRestoredPortableSettings(restoredSettings)
                                        reminderScheduler?.apply(false, restoredSettings.reminderTime)
                                        null
                                    } catch (_: Exception) {
                                        " No se pudieron aplicar todos los ajustes restaurados; revísalos en Ajustes."
                                    }
                                    // Source apps are package names on this device, so backups leave them out (see
                                    // Info/verification/2026-09-29-1.0.6-hardware-test.md, finding 5). Without one,
                                    // capture stays off after restoring onto a new install until they are chosen again.
                                    val noCaptureSources = runCatching {
                                        preferences?.state?.first()?.notificationSourcePackages?.isEmpty() == true
                                    }.getOrDefault(false)
                                    val captureReminder = if (noCaptureSources) {
                                        " Si usabas la captura desde notificaciones, vuelve a elegir tus apps en " +
                                            "Ajustes > Captura desde notificaciones; el backup no las incluye."
                                    } else {
                                        null
                                    }
                                    runCatching { onSuccessfulRestore() }
                                    onRestoreCompleted(
                                        "Backup restaurado: ${preview.contentSummary()}." +
                                            preferenceWarning.orEmpty() + captureReminder.orEmpty(),
                                    )
                                    onRestoreCandidateHandled()
                                    backupPreview = null
                                    restoreError = null
                                }
                                is LedgerResult.Rejected -> restoreError = result.message
                                is LedgerResult.Deleted -> Unit
                            }
                        } catch (error: CancellationException) {
                            restoreError = "Restauración cancelada. No se modificaron los datos."
                            throw error
                        } finally {
                            restoreInProgress = false
                        }
                    }
                }) { Text(if (restoreInProgress) "Restaurando…" else if (observedState?.needsOnboarding == false) "Restaurar y reemplazar" else "Restaurar") }
            },
            dismissButton = {
                TextButton(
                    enabled = !restoreInProgress,
                    onClick = { onRestoreCandidateHandled(); backupPreview = null; restoreError = null },
                ) { Text("Cancelar") }
            },
        )
    }
    val state = observedState
    if (state == null) {
        CenteredProgress("Cargando…")
        return
    }
    if (state.needsOnboarding) {
        OnboardingScreen(
            ledger = ledger,
            preferences = preferences,
            onPickBackup = onPickBackup,
            operationMessage = operationMessage,
            operationRetryLabel = operationRetryLabel,
            onOperationMessageHandled = onOperationMessageHandled,
            onRetryOperation = onRetryOperation,
        )
        return
    }
    if (state.currentPeriod == null) {
        CenteredProgress("Actualizando periodo…")
        return
    }

    val backStack = rememberNavBackStack(RootRoute(RootScreen.DASHBOARD))
    val currentRoute = backStack.last()
    val screen = backStack.filterIsInstance<RootRoute>().lastOrNull()?.screen ?: RootScreen.DASHBOARD
    val movementRoute = currentRoute as? MovementRoute
    val comparisonRoute = currentRoute as? ComparisonRoute
    val settingsSection = (currentRoute as? SettingsDetailRoute)?.section
    val snackbar = remember { SnackbarHostState() }
    val appScope = rememberCoroutineScope()
    // Keeps each root destination's saveable state (scroll, search, filters) while other routes are shown,
    // and each open Movement form's draft while another form is stacked above it.
    val rootStateHolder = rememberSaveableStateHolder()
    val openFormIds = backStack.mapNotNull { (it as? MovementRoute)?.instanceId }
    var knownFormIds by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(openFormIds) {
        (knownFormIds - openFormIds.toSet()).forEach(rootStateHolder::removeState)
        knownFormIds = openFormIds
    }

    fun navigateRoot(destination: RootScreen) {
        backStack[0] = RootRoute(destination)
        while (backStack.size > 1) backStack.removeLastOrNull()
    }

    fun openComparison(periodId: String) {
        backStack.add(ComparisonRoute(periodId, PeriodComparison.previousPeriodId(state, periodId)))
    }

    LaunchedEffect(openMovementsRevision) {
        if (openMovementsRevision > 0) navigateRoot(RootScreen.MOVEMENTS)
    }

    var handledNewExpenseRequest by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(newExpenseRequest, state.currentPeriod.id) {
        if (newExpenseRequest > handledNewExpenseRequest) {
            // An edit or suggestion form stays underneath; only an already open new-expense form is reused.
            if ((backStack.lastOrNull() as? MovementRoute)?.isNewExpense != true) backStack.add(movementForm())
            handledNewExpenseRequest = newExpenseRequest
        }
    }

    if (movementRoute != null) {
        val movementBeingEdited = state.movements.firstOrNull { it.id == movementRoute.movementId }
        val suggestion = state.movementSuggestions.firstOrNull { it.id == movementRoute.suggestionId }
        BackHandler { backStack.removeLastOrNull() }
        if (movementRoute.suggestionId != null && suggestion == null) {
            AlertDialog(
                onDismissRequest = { backStack.removeLastOrNull() },
                title = { Text("Sugerencia no disponible") },
                text = { Text("Esta sugerencia ya fue revisada o expiró.") },
                confirmButton = {
                    TextButton(onClick = { backStack.removeLastOrNull() }) { Text("Cerrar") }
                },
            )
            return
        }
        rootStateHolder.SaveableStateProvider(movementRoute.instanceId) {
            MovementDialog(
                state = state,
                ledger = ledger,
                defaultExpenseCurrency = preferenceState.defaultExpenseCurrency,
                onlineFxEnabled = preferenceState.onlineFxEnabled,
                exchangeRates = exchangeRates,
                onDismiss = { backStack.removeLastOrNull() },
                onSaved = {
                    // A form stacked over another Movement form (the launcher shortcut over an edit) returns to it
                    // with its draft; only a form opened from a root screen goes back to that screen.
                    if (backStack.getOrNull(backStack.lastIndex - 1) is MovementRoute) {
                        backStack.removeLastOrNull()
                    } else {
                        navigateRoot(if (movementRoute.movementId == null) RootScreen.DASHBOARD else RootScreen.MOVEMENTS)
                    }
                    appScope.launch {
                        snackbar.showSnackbar(
                            if (movementRoute.movementId == null) "Gasto guardado" else "Movimiento actualizado",
                        )
                    }
                },
                initialMovement = movementBeingEdited,
                suggestion = suggestion,
                initialPocketId = movementRoute.pocketId,
                snackbarHostState = snackbar,
            )
        }
        return
    }

    if (comparisonRoute != null) {
        BackHandler { backStack.removeLastOrNull() }
        ComparisonScreen(
            state = state,
            periodId = comparisonRoute.periodId,
            baselinePeriodId = comparisonRoute.baselinePeriodId,
            onPeriodsChange = { periodId, baselineId -> backStack[backStack.lastIndex] = ComparisonRoute(periodId, baselineId) },
            onBack = { backStack.removeLastOrNull() },
        )
        return
    }

    BackHandler(enabled = backStack.size > 1) { backStack.removeLastOrNull() }
    // Platform convention: back from another root tab returns to Inicio before leaving the app.
    BackHandler(enabled = backStack.size == 1 && screen != RootScreen.DASHBOARD) { navigateRoot(RootScreen.DASHBOARD) }
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
                            label = { RootDestinationLabel(destination.label) },
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
                                onClick = { backStack.add(movementForm()) },
                                icon = { Icon(Icons.Default.Add, contentDescription = "Registrar gasto") },
                                text = { Text("Registrar gasto") },
                                modifier = Modifier.testTag("contextual_add"),
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onTertiary,
                            )
                            RootScreen.MOVEMENTS -> FloatingActionButton(
                                onClick = { backStack.add(movementForm()) },
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
                                    label = { RootDestinationLabel(destination.label) },
                                )
                            }
                        }
                    }
                },
            ) { padding ->
                Crossfade(targetState = screen, animationSpec = tween(durationMillis = 150), label = "root") { shown ->
                rootStateHolder.SaveableStateProvider(shown.name) {
                when (shown) {
                    RootScreen.DASHBOARD -> DashboardScreen(
                        state = state,
                        padding = padding,
                        onManagePockets = { navigateRoot(RootScreen.POCKETS) },
                        onRecordExpenseIn = { backStack.add(movementForm(pocketId = it)) },
                        onComparePeriods = { openComparison(state.currentPeriod.id) },
                    )
                    RootScreen.MOVEMENTS -> MovementsScreen(
                        state = state,
                        ledger = ledger,
                        snackbar = snackbar,
                        padding = padding,
                        undoWindowMillis = undoWindowMillis,
                        onRecordExpense = { backStack.add(movementForm()) },
                        onEditMovement = { backStack.add(movementForm(movementId = it.id)) },
                        onReviewSuggestion = { backStack.add(movementForm(suggestionId = it)) },
                    )
                    RootScreen.POCKETS -> PocketsScreen(state, ledger, padding, onComparePeriod = ::openComparison)
                    RootScreen.SETTINGS -> SettingsScreen(
                        state = state,
                        ledger = ledger,
                        preferences = preferenceState,
                        preferencesStore = preferences,
                        exchangeRates = exchangeRates,
                        reminderScheduler = reminderScheduler,
                        onCreateBackup = { requestBackup("create") },
                        onShareBackup = { requestBackup("share") },
                        onCreateCsv = onCreateCsv,
                        onPickBackup = onPickBackup,
                        onRequestNotificationPermission = onRequestNotificationPermission,
                        notificationPermissionRevision = notificationPermissionRevision,
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
    }
}

/** "1 periodo, 10 Pockets y 1 movimiento" */
private fun com.aif31.pocket.data.BackupPreview.contentSummary(): String =
    "${counted(periods, "periodo", "periodos")}, ${counted(pockets, "Pocket", "Pockets")} y " +
        counted(movements, "movimiento", "movimientos")

@Composable
private fun CenteredProgress(label: String) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun OnboardingScreen(
    ledger: PocketLedger,
    preferences: PreferencesStore?,
    onPickBackup: () -> Unit,
    operationMessage: String?,
    operationRetryLabel: String?,
    onOperationMessageHandled: () -> Unit,
    onRetryOperation: () -> Unit,
) {
    var funds by rememberSaveable { mutableStateOf("") }
    var startDay by rememberSaveable { mutableStateOf("25") }
    var accountingCurrency by rememberSaveable { mutableStateOf(SupportedCurrency.SAR) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    // Drawn edge to edge outside the app scaffold, so the list keeps clear of the system bars, cutout, and keyboard.
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = WindowInsets.safeDrawing.add(WindowInsets(24.dp, 24.dp, 24.dp, 24.dp)).asPaddingValues(),
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
                operationMessage?.let { message ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                if (operationRetryLabel == null) "Operación de documentos" else "La operación falló",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(message)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (operationRetryLabel != null) {
                                    Button(onClick = onRetryOperation) { Text(operationRetryLabel) }
                                }
                                TextButton(onClick = onOperationMessageHandled) {
                                    Text(if (operationRetryLabel == null) "Aceptar" else "Cerrar")
                                }
                            }
                        }
                    }
                }
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Moneda contable", style = MaterialTheme.typography.titleMedium)
                        SegmentedChoice(
                            options = SupportedCurrency.entries.map { ChoiceOption(it, it.name) },
                            selected = accountingCurrency,
                            onSelect = { accountingCurrency = it },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = funds,
                            onValueChange = { funds = it; error = null },
                            label = { Text("Fondos nuevos (${accountingCurrency.name})") },
                            prefix = { Text(accountingCurrency.name) },
                            singleLine = true,
                            isError = error != null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth().testTag("new_funds"),
                        )
                        OutlinedTextField(
                            value = startDay,
                            onValueChange = { startDay = it.filter(Char::isDigit).take(2); error = null },
                            label = { Text("Día de inicio") },
                            supportingText = { Text("El periodo se renovará cada mes en este día.") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            modifier = Modifier.fillMaxWidth().testTag("start_day"),
                        )
                        error?.let {
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                        }
                    }
                }
                Button(
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    onClick = {
                        scope.launch {
                            runCatching {
                                LedgerCommand.Initialize(
                                    newFundsMinor = Money.parse(funds, accountingCurrency.name).minor,
                                    startDay = startDay.toInt(),
                                    accountingCurrency = accountingCurrency,
                                )
                            }.onSuccess {
                                when (val result = ledger.execute(it)) {
                                    LedgerResult.Success -> {
                                        preferences?.setFuturePeriodStartDay(it.startDay)
                                        preferences?.setDefaultExpenseCurrency(it.accountingCurrency)
                                    }
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
    onRecordExpenseIn: (String) -> Unit,
    onComparePeriods: () -> Unit,
) {
    ActionableDashboardContent(
        state = state,
        contentPadding = padding,
        onManagePockets = onManagePockets,
        onRecordExpenseIn = onRecordExpenseIn,
        onComparePeriods = onComparePeriods,
    )
}
@Composable
private fun MovementDialog(
    state: LedgerState,
    ledger: PocketLedger,
    defaultExpenseCurrency: SupportedCurrency,
    onlineFxEnabled: Boolean,
    exchangeRates: ExchangeRateRepository?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    initialMovement: Movement? = null,
    suggestion: com.aif31.pocket.data.MovementSuggestion? = null,
    initialPocketId: String? = null,
    snackbarHostState: SnackbarHostState? = null,
) {
    ProductionMovementScreen(
        state = state,
        ledger = ledger,
        onDismiss = onDismiss,
        onSaved = onSaved,
        movementDefaults = ledger.movementDefaults(),
        initialMovement = initialMovement,
        suggestion = suggestion,
        defaultExpenseCurrency = defaultExpenseCurrency,
        onlineFxEnabled = onlineFxEnabled,
        exchangeRates = exchangeRates,
        initialPocketId = initialPocketId,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * A root destination's name on one line at every font size. It shrinks to fit instead of ending in an ellipsis or
 * breaking mid-word, so "Movimientos" stays recognizable at large font scales.
 */
@Composable
private fun RootDestinationLabel(label: String) {
    val style = LocalTextStyle.current
    BasicText(
        label,
        style = style.copy(color = style.color.takeOrElse { LocalContentColor.current }),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = style.fontSize, stepSize = 0.5.sp),
    )
}
