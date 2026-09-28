package com.aif31.pocket

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aif31.pocket.data.*
import com.aif31.pocket.domain.Money
import com.aif31.pocket.domain.SupportedCurrency
import com.aif31.pocket.fx.ExchangeRateRepository
import com.aif31.pocket.fx.QuoteFailure
import com.aif31.pocket.settings.*
import com.aif31.pocket.ui.*
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

@Composable
internal fun SettingsScreen(
    state: LedgerState,
    ledger: PocketLedger,
    preferences: AppPreferences,
    preferencesStore: PreferencesStore?,
    exchangeRates: ExchangeRateRepository? = null,
    reminderScheduler: ReminderScheduler?,
    onCreateBackup: () -> Unit,
    onShareBackup: () -> Unit = {},
    onCreateCsv: () -> Unit,
    onPickBackup: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    notificationPermissionRevision: Int = 0,
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
    if (selectedSection == SettingsSection.CURRENCY) {
        CurrencySettingsRoute(
            state = state,
            ledger = ledger,
            preferences = preferences,
            preferencesStore = preferencesStore,
            exchangeRates = exchangeRates,
            padding = padding,
            onBack = { onSectionChange(null) },
        )
        return
    }
    if (selectedSection == SettingsSection.NOTIFICATION_ASSISTANCE) {
        NotificationAssistanceSettings(
            preferences = preferences,
            preferencesStore = preferencesStore,
            padding = padding,
            onBack = { onSectionChange(null) },
            onRequestNotificationPermission = onRequestNotificationPermission,
            notificationPermissionRevision = notificationPermissionRevision,
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
        onShareBackup = onShareBackup,
        onCreateCsv = onCreateCsv,
        onPickBackup = onPickBackup,
        onRequestNotificationPermission = onRequestNotificationPermission,
        notificationPermissionRevision = notificationPermissionRevision,
        padding = padding,
        section = selectedSection,
        onBack = { onSectionChange(null) },
    )
}

@Composable
private fun CurrencySettingsRoute(
    state: LedgerState,
    ledger: PocketLedger,
    preferences: AppPreferences,
    preferencesStore: PreferencesStore?,
    exchangeRates: ExchangeRateRepository?,
    padding: PaddingValues,
    onBack: () -> Unit,
) {
    val currentPeriod = state.currentPeriod ?: return
    val currentCurrency = currentPeriod.accountingCurrency
    val activationDate = currentPeriod.endExclusive
    val requestedDate = state.currentLocalDate
    val scope = rememberCoroutineScope()
    var targetCurrency by rememberSaveable(currentCurrency) {
        mutableStateOf(SupportedCurrency.entries.first { it != currentCurrency })
    }
    var quoteState by remember { mutableStateOf<CurrencyQuoteState>(CurrencyQuoteState.Idle) }
    var refreshGeneration by rememberSaveable { mutableIntStateOf(0) }
    var quoteCancelled by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(
        currentCurrency,
        targetCurrency,
        requestedDate,
        preferences.onlineFxEnabled,
        state.pendingCurrencyChange,
        exchangeRates,
        refreshGeneration,
        quoteCancelled,
    ) {
        if (!preferences.onlineFxEnabled || state.pendingCurrencyChange != null || quoteCancelled) {
            quoteState = CurrencyQuoteState.Idle
            return@LaunchedEffect
        }
        val repository = exchangeRates
        if (repository == null) {
            quoteState = CurrencyQuoteState.Error(QuoteFailure.ConfigurationUnavailable().message.orEmpty())
            return@LaunchedEffect
        }
        quoteState = CurrencyQuoteState.Loading
        quoteState = try {
            CurrencyQuoteState.Ready(
                repository.quote(
                    requestedDate = requestedDate,
                    base = currentCurrency,
                    quote = targetCurrency,
                    forceRefresh = refreshGeneration > 0,
                )
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: QuoteFailure) {
            CurrencyQuoteState.Error(error.message.orEmpty())
        } catch (_: Exception) {
            CurrencyQuoteState.Error(QuoteFailure.Unavailable().message.orEmpty())
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        PocketTopAppBar(SettingsSection.CURRENCY.title, onBack, windowInsets = WindowInsets(0))
        CurrencySettingsContent(
            state = CurrencySettingsUiState(
                currentCurrency = currentCurrency,
                onlineFxEnabled = preferences.onlineFxEnabled,
                defaultExpenseCurrency = preferences.defaultExpenseCurrency,
                targetCurrency = targetCurrency,
                quoteState = quoteState,
                pendingChange = state.pendingCurrencyChange?.boundary,
            ),
            contentPadding = PaddingValues(16.dp),
            onOnlineFxEnabledChange = { enabled ->
                scope.launch { preferencesStore?.setOnlineFxEnabled(enabled) }
            },
            onDefaultExpenseCurrencyChange = { currency ->
                scope.launch { preferencesStore?.setDefaultExpenseCurrency(currency) }
            },
            onTargetCurrencyChange = { currency ->
                targetCurrency = currency
                quoteCancelled = false
            },
            onRefreshQuote = {
                quoteCancelled = false
                refreshGeneration++
            },
            onCancelQuote = { quoteCancelled = true },
            onConfirmTransition = {
                val quote = (quoteState as? CurrencyQuoteState.Ready)?.quote ?: return@CurrencySettingsContent
                scope.launch {
                    ledger.execute(
                        LedgerCommand.ScheduleCurrencyChange(
                            targetCurrency = quote.quote,
                            rate = quote.rate,
                            effectiveDate = activationDate,
                            source = quote.source,
                            quoteEffectiveDate = quote.effectiveDate,
                        )
                    )
                }
            },
            onCancelPendingTransition = {
                scope.launch { ledger.execute(LedgerCommand.CancelCurrencyChange) }
            },
        )
    }
}

@Composable
private fun SettingsDetailScreen(
    state: LedgerState,
    ledger: PocketLedger,
    preferences: AppPreferences,
    preferencesStore: PreferencesStore?,
    reminderScheduler: ReminderScheduler?,
    onCreateBackup: () -> Unit,
    onShareBackup: () -> Unit,
    onCreateCsv: () -> Unit,
    onPickBackup: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    notificationPermissionRevision: Int,
    padding: PaddingValues,
    section: SettingsSection,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var selectedFundsPeriodId by rememberSaveable(state.currentPeriod?.id) { mutableStateOf(state.currentPeriod?.id) }
    var funds by rememberSaveable(state.currentPeriod?.id) { mutableStateOf(minorNumber(state.newFundsMinor)) }
    var futureDay by rememberSaveable(preferences.futurePeriodStartDay) { mutableStateOf(preferences.futurePeriodStartDay.toString()) }
    var reminderTime by rememberSaveable(preferences.reminderTime) { mutableStateOf(preferences.reminderTime.toString()) }
    var methodName by rememberSaveable { mutableStateOf("") }
    var editingMethod by rememberSaveable { mutableStateOf<String?>(null) }
    var templateName by rememberSaveable { mutableStateOf("") }
    var templateAmount by rememberSaveable { mutableStateOf("") }
    var templatePocketId by rememberSaveable { mutableStateOf<String?>(null) }
    var templateMethodId by rememberSaveable { mutableStateOf<String?>(null) }
    var templateInputCurrency by rememberSaveable { mutableStateOf(preferences.defaultExpenseCurrency) }
    var editingTemplate by rememberSaveable { mutableStateOf<String?>(null) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var reminderPermissionRationaleVisible by rememberSaveable { mutableStateOf(false) }
    var reminderStatus by remember { mutableStateOf<ReminderStatus>(ReminderStatus.Off) }
    val lifecycleOwner = LocalLifecycleOwner.current
    fun refreshReminderStatus() {
        scope.launch {
            reminderStatus = try {
                reminderScheduler?.status(preferences.reminderEnabled)
                    ?: if (preferences.reminderEnabled) ReminderStatus.Failed else ReminderStatus.Off
            } catch (_: Exception) {
                ReminderStatus.Failed
            }
        }
    }
    LaunchedEffect(section, preferences.reminderEnabled, preferences.reminderTime, notificationPermissionRevision) {
        if (section == SettingsSection.REMINDERS) refreshReminderStatus()
    }
    DisposableEffect(lifecycleOwner, section, preferences.reminderEnabled) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && section == SettingsSection.REMINDERS) refreshReminderStatus()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val selectedFundsPeriod = state.periods.firstOrNull { it.id == selectedFundsPeriodId }
    val selectedFundsCurrency = selectedFundsPeriod?.accountingCurrency ?: SupportedCurrency.SAR
    val focusManager = LocalFocusManager.current
    fun resetTemplateForm() {
        editingTemplate = null
        templateName = ""
        templateAmount = ""
        templatePocketId = null
        templateMethodId = null
        templateInputCurrency = preferences.defaultExpenseCurrency
    }
    var reminderPickerVisible by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(padding)) {
        PocketTopAppBar(section.title, onBack, windowInsets = WindowInsets(0))
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("settings_list"),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (section == SettingsSection.PERIOD) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader("Fondos del periodo")
                        SingleChoiceChips(
                            options = state.periods.sortedByDescending { it.start }.map { period ->
                                ChoiceOption(period.id, formatPeriodRange(period.start, period.endExclusive))
                            },
                            selected = selectedFundsPeriodId,
                            onSelect = { id ->
                                selectedFundsPeriodId = id
                                state.periods.firstOrNull { it.id == id }?.let { funds = minorNumber(it.newFundsMinor) }
                            },
                        )
                        Text(
                            "Zona horaria de los periodos: Asia/Riyadh",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            funds,
                            { funds = it },
                            label = { Text("Fondos nuevos ${selectedFundsCurrency.name}") },
                            prefix = { Text(selectedFundsCurrency.name) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            modifier = Modifier.fillMaxWidth().testTag("period_funds"),
                        )
                        Button(onClick = {
                            scope.launch {
                                val value = runCatching { Money.parse(funds, selectedFundsCurrency.name).minor }.getOrNull() ?: run {
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
                        message?.let { StatusMessage(it) }
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        SectionHeader("Próximos periodos")
                        OutlinedTextField(
                            futureDay,
                            { futureDay = it.filter(Char::isDigit).take(2) },
                            label = { Text("Día de inicio para periodos futuros") },
                            supportingText = { Text("Solo afecta a los periodos que aún no existen.") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            }
            if (section == SettingsSection.REMINDERS) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Recordatorio", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    when (reminderStatus) {
                                        ReminderStatus.Off -> if (preferences.reminderAwaitingConfirmation) "Desactivado · confirma en este dispositivo" else "Desactivado"
                                        ReminderStatus.PermissionRequired -> "Permiso necesario"
                                        ReminderStatus.Scheduled -> "Programado"
                                        ReminderStatus.Failed -> "No se pudo programar"
                                    },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = preferences.reminderEnabled,
                                onCheckedChange = { enabled ->
                                    val time = runCatching { LocalTime.parse(reminderTime) }.getOrNull()
                                    if (time == null) {
                                        message = "Escribe una hora válida en formato HH:mm"
                                    } else {
                                        scope.launch {
                                            try {
                                                preferencesStore?.setReminder(enabled, time)
                                                reminderStatus = reminderScheduler?.applyAndCheck(enabled, time)
                                                    ?: if (enabled) ReminderStatus.Failed else ReminderStatus.Off
                                                reminderPermissionRationaleVisible = reminderStatus == ReminderStatus.PermissionRequired
                                                message = if (enabled) null else "Recordatorio desactivado"
                                            } catch (_: Exception) {
                                                reminderStatus = ReminderStatus.Failed
                                                message = "No se pudo programar el recordatorio"
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.testTag("reminder_switch"),
                            )
                        }
                        OutlinedTextField(
                            reminderTime,
                            { reminderTime = it },
                            label = { Text("Hora (HH:mm)") },
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = { reminderPickerVisible = true }) {
                                    Icon(Icons.Default.Schedule, contentDescription = "Elegir hora")
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("reminder_time"),
                        )
                        Button(onClick = {
                            val time = runCatching { LocalTime.parse(reminderTime) }.getOrNull()
                            if (time == null) {
                                message = "Escribe una hora válida en formato HH:mm"
                            } else {
                                scope.launch {
                                    try {
                                        preferencesStore?.setReminder(preferences.reminderEnabled, time)
                                        reminderStatus = reminderScheduler?.applyAndCheck(preferences.reminderEnabled, time)
                                            ?: if (preferences.reminderEnabled) ReminderStatus.Failed else ReminderStatus.Off
                                        message = "Horario guardado"
                                    } catch (_: Exception) {
                                        reminderStatus = ReminderStatus.Failed
                                        message = "No se pudo programar el recordatorio"
                                    }
                                }
                            }
                        }) { Text("Guardar horario") }
                        message?.let { StatusMessage(it) }
                        if (reminderStatus == ReminderStatus.Failed && preferences.reminderEnabled) {
                            Text("Comprueba los ajustes de notificaciones y vuelve a intentarlo.")
                            TextButton(onClick = {
                                scope.launch {
                                    reminderStatus = try {
                                        reminderScheduler?.applyAndCheck(true, preferences.reminderTime) ?: ReminderStatus.Failed
                                    } catch (_: Exception) { ReminderStatus.Failed }
                                }
                            }) { Text("Reintentar") }
                        }
                        if (reminderStatus == ReminderStatus.PermissionRequired) {
                            Text("Permite las notificaciones para recibir el recordatorio.")
                            TextButton(onClick = { reminderPermissionRationaleVisible = true }) { Text("Revisar permiso") }
                        }
                        Text("La entrega es aproximada y puede retrasarse según el dispositivo.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("El recordatorio no muestra importes en la pantalla bloqueada.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            }
            if (section == SettingsSection.PAYMENT_METHODS) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader("Método predeterminado")
                        SingleChoiceChips(
                            options = listOf(ChoiceOption<String?>(null, "Ninguno", "default_payment_none")) +
                                state.paymentMethods.filterNot { it.archived }.map { ChoiceOption<String?>(it.id, it.name, "default_payment_${it.name}") },
                            selected = state.defaultPaymentMethodId,
                            onSelect = { id -> scope.launch { ledger.execute(LedgerCommand.SetDefaultPaymentMethod(id)) } },
                        )
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        SectionHeader(if (editingMethod == null) "Añadir método" else "Editar método")
                        OutlinedTextField(
                            methodName,
                            { methodName = it },
                            label = { Text("Nombre") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                scope.launch {
                                    when (val result = ledger.execute(LedgerCommand.UpsertPaymentMethod(editingMethod, methodName))) {
                                        LedgerResult.Success -> { methodName = ""; editingMethod = null; message = "Método guardado" }
                                        is LedgerResult.Rejected -> message = result.message
                                        is LedgerResult.Deleted -> Unit
                                    }
                                }
                            }) { Text(if (editingMethod == null) "Añadir método" else "Guardar método") }
                            if (editingMethod != null) {
                                TextButton(onClick = { editingMethod = null; methodName = "" }) { Text("Cancelar edición") }
                            }
                        }
                        message?.let { StatusMessage(it) }
                        SectionHeader("Tus métodos")
                        Text("Toca un método para editarlo.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
                items(state.paymentMethods, key = { it.id }) { method ->
                    EditableRow(
                        title = method.name + if (method.archived) " (archivado)" else "",
                        editing = editingMethod == method.id,
                        archived = method.archived,
                        onEdit = { editingMethod = method.id; methodName = method.name },
                        onToggleArchive = { scope.launch { ledger.execute(LedgerCommand.ArchivePaymentMethod(method.id, !method.archived)) } },
                        modifier = Modifier.testTag("payment_method_${method.name}"),
                    )
                }
            }
            if (section == SettingsSection.TEMPLATES) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Solo precargan el formulario; nunca crean gastos automáticamente.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SectionHeader(if (editingTemplate == null) "Nueva plantilla" else "Editar plantilla")
                        OutlinedTextField(
                            templateName,
                            { templateName = it },
                            label = { Text("Nombre de plantilla") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        SegmentedChoice(
                            options = SupportedCurrency.entries.map { ChoiceOption(it, it.name) },
                            selected = templateInputCurrency,
                            onSelect = { templateInputCurrency = it },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            templateAmount,
                            { templateAmount = it },
                            label = { Text("Importe ${templateInputCurrency.name}") },
                            prefix = { Text(templateInputCurrency.name) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("Pocket", style = MaterialTheme.typography.titleSmall)
                        SingleChoiceChips(
                            options = state.pockets.filterNot { it.pocket.archived || it.retiredThisPeriod }
                                .map { ChoiceOption<String?>(it.pocket.id, it.pocket.name, "template_pocket_${it.pocket.name}") },
                            selected = templatePocketId,
                            onSelect = { templatePocketId = it },
                        )
                        Text("Método de pago (opcional)", style = MaterialTheme.typography.titleSmall)
                        SingleChoiceChips(
                            options = listOf(ChoiceOption<String?>(null, "Ninguno")) +
                                state.paymentMethods.filterNot { it.archived }.map { ChoiceOption<String?>(it.id, it.name, "template_method_${it.name}") },
                            selected = templateMethodId,
                            onSelect = { templateMethodId = it },
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                scope.launch {
                                    val pocketId = templatePocketId ?: run { message = "Selecciona un Pocket"; return@launch }
                                    val amount = runCatching { Money.parse(templateAmount, templateInputCurrency.name).minor }.getOrNull()
                                        ?: run { message = "Escribe un importe válido"; return@launch }
                                    when (val result = ledger.execute(
                                        LedgerCommand.UpsertTemplate(
                                            id = editingTemplate,
                                            name = templateName,
                                            amountMinor = amount,
                                            pocketId = pocketId,
                                            paymentMethodId = templateMethodId,
                                            inputCurrency = templateInputCurrency,
                                        )
                                    )) {
                                        LedgerResult.Success -> {
                                            resetTemplateForm()
                                            message = "Plantilla guardada"
                                        }
                                        is LedgerResult.Rejected -> message = result.message
                                        is LedgerResult.Deleted -> Unit
                                    }
                                }
                            }) { Text(if (editingTemplate == null) "Añadir plantilla" else "Guardar plantilla") }
                            if (editingTemplate != null) {
                                TextButton(onClick = ::resetTemplateForm) { Text("Cancelar edición") }
                            }
                        }
                        message?.let { StatusMessage(it) }
                        if (state.templates.isNotEmpty()) SectionHeader("Tus plantillas")
                    }
                }
                items(state.templates, key = { it.id }) { template ->
                    EditableRow(
                        title = "${template.name}: ${MoneyText.format(template.amountMinor, template.inputCurrency)}${if (template.archived) " (archivada)" else ""}",
                        editing = editingTemplate == template.id,
                        archived = template.archived,
                        onEdit = {
                            editingTemplate = template.id
                            templateName = template.name
                            templateAmount = minorNumber(template.amountMinor)
                            templatePocketId = template.pocketId
                            templateMethodId = template.paymentMethodId
                            templateInputCurrency = template.inputCurrency
                        },
                        onToggleArchive = { scope.launch { ledger.execute(LedgerCommand.ArchiveTemplate(template.id, !template.archived)) } },
                    )
                }
            }
            if (section == SettingsSection.DATA) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        DataAction(Icons.Default.Backup, "Crear backup completo", "Guarda todo tu historial en un archivo que puedes restaurar.", primary = true, onClick = onCreateBackup)
                        DataAction(Icons.Default.Share, "Compartir backup", "Envía el backup a otra app o dispositivo.", onClick = onShareBackup)
                        DataAction(Icons.Default.Restore, "Restaurar backup", "Reemplaza los datos actuales tras una vista previa.", onClick = onPickBackup)
                        DataAction(Icons.Default.TableChart, "Exportar CSV", "Movimientos para hojas de cálculo.", onClick = onCreateCsv)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                            Text(
                                "El backup y el CSV no están cifrados. El CSV no sirve para restaurar.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
    if (reminderPickerVisible) {
        TimeOfDayPickerDialog(
            initial = runCatching { LocalTime.parse(reminderTime) }.getOrNull() ?: preferences.reminderTime,
            onPicked = { reminderTime = it.toString(); reminderPickerVisible = false },
            onDismiss = { reminderPickerVisible = false },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}

@Composable
private fun StatusMessage(text: String) {
    Text(text, color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}

@Composable
private fun EditableRow(
    title: String,
    editing: Boolean,
    archived: Boolean,
    onEdit: () -> Unit,
    onToggleArchive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onEdit,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (editing) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (editing) Icons.Default.Edit else Icons.Default.EditNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Text(
                title,
                modifier = Modifier.weight(1f),
                color = if (archived) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            TextButton(onClick = onToggleArchive) { Text(if (archived) "Restaurar" else "Archivar") }
        }
    }
}

@Composable
private fun DataAction(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    primary: Boolean = false,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (primary) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun minorNumber(minor: Long): String = MoneyText.editable(minor)
