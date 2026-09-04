package com.aif31.pocket

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aif31.pocket.data.*
import com.aif31.pocket.domain.Money
import com.aif31.pocket.domain.SupportedCurrency
import com.aif31.pocket.fx.ExchangeRateRepository
import com.aif31.pocket.fx.QuoteFailure
import com.aif31.pocket.settings.*
import com.aif31.pocket.ui.*
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
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
        TextButton(onClick = onBack) { Text("Atrás") }
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
    onCreateCsv: () -> Unit,
    onPickBackup: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
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
    var templateInputCurrency by rememberSaveable { mutableStateOf(SupportedCurrency.SAR) }
    var editingTemplate by rememberSaveable { mutableStateOf<String?>(null) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var reminderPermissionRationaleVisible by rememberSaveable { mutableStateOf(false) }
    val selectedFundsPeriod = state.periods.firstOrNull { it.id == selectedFundsPeriodId }
    val selectedFundsCurrency = selectedFundsPeriod?.accountingCurrency ?: SupportedCurrency.SAR

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
            OutlinedTextField(funds, { funds = it }, label = { Text("Fondos nuevos ${selectedFundsCurrency.name}") })
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
            Text("Método predeterminado", style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    OutlinedButton(
                        onClick = { scope.launch { ledger.execute(LedgerCommand.SetDefaultPaymentMethod(null)) } },
                        modifier = Modifier
                            .testTag("default_payment_none")
                            .semantics { selected = state.defaultPaymentMethodId == null },
                    ) {
                        Text(if (state.defaultPaymentMethodId == null) "✓ Ninguno" else "Ninguno")
                    }
                }
                items(state.paymentMethods.filterNot { it.archived }, key = { "default_${it.id}" }) { method ->
                    OutlinedButton(
                        onClick = { scope.launch { ledger.execute(LedgerCommand.SetDefaultPaymentMethod(method.id)) } },
                        modifier = Modifier
                            .testTag("default_payment_${method.name}")
                            .semantics { selected = state.defaultPaymentMethodId == method.id },
                    ) {
                        Text(if (state.defaultPaymentMethodId == method.id) "✓ ${method.name}" else method.name)
                    }
                }
            }
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
            OutlinedTextField(
                templateAmount,
                { templateAmount = it },
                label = { Text("Importe ${templateInputCurrency.name}") },
            )
            Text("Pocket")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.pockets.filterNot { it.pocket.archived || it.retiredThisPeriod }, key = { it.pocket.id }) { pocket ->
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
                            templateName = ""; templateAmount = ""; templatePocketId = null; templateMethodId = null
                            templateInputCurrency = SupportedCurrency.SAR
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
                templateInputCurrency = template.inputCurrency
            }) {
                Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${template.name}: ${MoneyText.format(template.amountMinor, template.inputCurrency)}${if (template.archived) " (archivada)" else ""}")
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

private fun minorNumber(minor: Long): String = MoneyText.grouped(minor)
