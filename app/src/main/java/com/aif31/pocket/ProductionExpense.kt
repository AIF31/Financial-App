package com.aif31.pocket

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.aif31.pocket.ui.ChoiceOption
import com.aif31.pocket.ui.SegmentedChoice
import com.aif31.pocket.ui.SingleChoiceChips
import com.aif31.pocket.ui.TimeOfDayPickerDialog
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aif31.pocket.data.LedgerCommand
import com.aif31.pocket.data.LedgerResult
import com.aif31.pocket.data.LedgerState
import com.aif31.pocket.data.Movement
import com.aif31.pocket.data.MovementDefaults
import com.aif31.pocket.data.MovementType
import com.aif31.pocket.data.MovementSuggestion
import com.aif31.pocket.data.PocketLedger
import com.aif31.pocket.domain.Money
import com.aif31.pocket.domain.SupportedCurrency
import com.aif31.pocket.fx.ExchangeRateRepository
import com.aif31.pocket.fx.QuoteFailure
import com.aif31.pocket.expense.ExpenseEntryStateHolder
import com.aif31.pocket.expense.ExpenseRequest
import com.aif31.pocket.expense.QuoteUiState
import com.aif31.pocket.ui.PocketArtworkPlate
import com.aif31.pocket.ui.PocketTopAppBar
import com.aif31.pocket.ui.availableText
import androidx.compose.ui.text.AnnotatedString
import com.aif31.pocket.ui.MoneyText
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

@Composable
internal fun ProductionMovementScreen(
    state: LedgerState,
    ledger: PocketLedger,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    movementDefaults: MovementDefaults,
    initialMovement: Movement? = null,
    suggestion: MovementSuggestion? = null,
    defaultExpenseCurrency: SupportedCurrency = SupportedCurrency.SAR,
    onlineFxEnabled: Boolean = false,
    exchangeRates: ExchangeRateRepository? = null,
    initialPocketId: String? = null,
) {
    val stateKey = initialMovement?.id ?: suggestion?.id
    val initialAccountingCurrency = state.periods.firstOrNull { it.id == initialMovement?.periodId }?.accountingCurrency
        ?: state.currentPeriod?.accountingCurrency
        ?: SupportedCurrency.SAR
    var localDate by rememberSaveable(stateKey) {
        mutableStateOf((initialMovement?.localDate ?: suggestion?.effectiveAtUtcMillis?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.of("Asia/Riyadh")).toLocalDate()
        } ?: movementDefaults.localDate).toString())
    }
    val accountingCurrency = runCatching { LocalDate.parse(localDate) }.getOrNull()?.let { enteredDate ->
        state.periods.firstOrNull { enteredDate >= it.start && enteredDate < it.endExclusive }?.accountingCurrency
    } ?: initialAccountingCurrency
    var amount by rememberSaveable(stateKey) {
        mutableStateOf(
            initialMovement?.let { minorNumberForForm(it.originalAmountMinor ?: it.accountingAmountMinor) }
                ?: suggestion?.let { minorNumberForForm(it.amountMinor) }.orEmpty()
        )
    }
    var amountEdited by rememberSaveable(stateKey) { mutableStateOf(false) }
    var selectedPocket by rememberSaveable(stateKey) { mutableStateOf(initialMovement?.pocketId ?: initialPocketId) }
    var refund by rememberSaveable(stateKey) { mutableStateOf(initialMovement?.type == MovementType.REFUND) }
    var merchant by rememberSaveable(stateKey) { mutableStateOf(initialMovement?.merchant ?: suggestion?.merchant.orEmpty()) }
    var note by rememberSaveable(stateKey) { mutableStateOf(initialMovement?.note.orEmpty()) }
    var paymentMethod by rememberSaveable(stateKey) {
        mutableStateOf(if (initialMovement != null) initialMovement.paymentMethodId else state.defaultPaymentMethodId)
    }
    var currency by rememberSaveable(stateKey) {
        mutableStateOf(initialMovement?.originalCurrencyCode ?: suggestion?.currency?.name ?: defaultExpenseCurrency.name)
    }
    var localTime by rememberSaveable(stateKey) {
        val instant = initialMovement?.occurredAtUtcMillis ?: suggestion?.effectiveAtUtcMillis ?: movementDefaults.instantMillis
        val zone = ZoneId.of(initialMovement?.zoneId ?: "Asia/Riyadh")
        mutableStateOf(
            Instant.ofEpochMilli(instant)
                .atZone(zone)
                .toLocalTime()
                .withSecond(0)
                .withNano(0)
                .toString(),
        )
    }
    var detailsExpanded by rememberSaveable(stateKey) { mutableStateOf(initialMovement != null) }
    var error by rememberSaveable(stateKey) { mutableStateOf<String?>(null) }
    val draftId = rememberSaveable(stateKey) { UUID.randomUUID().toString() }
    var saving by remember { mutableStateOf(false) }
    var saveInterrupted by rememberSaveable(stateKey) { mutableStateOf(false) }
    var templateGeneration by rememberSaveable(stateKey) { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val haptics = LocalHapticFeedback.current
    var datePickerVisible by rememberSaveable(stateKey) { mutableStateOf(false) }
    var timePickerVisible by rememberSaveable(stateKey) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(stateKey) {
        focusRequester.requestFocus()
    }

    val inputCurrency = SupportedCurrency.fromCode(currency)
    val parsedDateForQuote = runCatching { LocalDate.parse(localDate) }.getOrNull()
    val parsedInputMinor = runCatching { Money.parse(amount, inputCurrency.name).minor }.getOrNull()
    val amountIsInvalid = amountEdited && (parsedInputMinor == null || parsedInputMinor <= 0)
    val holder = remember(exchangeRates, initialMovement, initialAccountingCurrency, templateGeneration) {
        ExpenseEntryStateHolder(exchangeRates, initialMovement.takeIf { templateGeneration == 0 }, initialAccountingCurrency)
    }
    var quoteState by remember(parsedDateForQuote, inputCurrency, accountingCurrency, amount, onlineFxEnabled, holder) {
        mutableStateOf<QuoteUiState>(QuoteUiState.Idle)
    }

    LaunchedEffect(
        parsedDateForQuote,
        inputCurrency,
        accountingCurrency,
        amount,
        onlineFxEnabled,
        holder,
    ) {
        val requestDate = parsedDateForQuote
        if (requestDate == null || parsedInputMinor == null || parsedInputMinor <= 0) {
            quoteState = QuoteUiState.Idle
            return@LaunchedEffect
        }
        quoteState = QuoteUiState.Loading
        quoteState = try {
            QuoteUiState.Ready(holder.resolve(
                ExpenseRequest(requestDate, inputCurrency, accountingCurrency, parsedInputMinor), onlineFxEnabled,
            ))
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: QuoteFailure) {
            QuoteUiState.Error(failure.message.orEmpty())
        } catch (_: Exception) {
            QuoteUiState.Error(QuoteFailure.Unavailable().message.orEmpty())
        }
    }

    val readyConversion = (quoteState as? QuoteUiState.Ready)?.conversion
    val accountingAmountMinor = readyConversion?.accountingAmountMinor

    fun saveMovement() {
        if (saving) return
        saving = true
        scope.launch {
            try {
            val parsedDate = runCatching { LocalDate.parse(localDate) }.getOrNull() ?: run {
                error = "Escribe una fecha válida"
                return@launch
            }
            val savingAccountingCurrency = state.periods.firstOrNull {
                parsedDate >= it.start && parsedDate < it.endExclusive
            }?.accountingCurrency ?: run {
                error = "La fecha no pertenece a un periodo existente"
                return@launch
            }
            val parsedAmount = accountingAmountMinor ?: run {
                error = "Escribe un importe válido"
                return@launch
            }
            val pocketId = selectedPocket ?: run {
                error = "Selecciona un Pocket"
                return@launch
            }
            val parsedTime = runCatching { LocalTime.parse(localTime) }.getOrNull() ?: run {
                error = "Escribe una hora válida"
                return@launch
            }
            val conversion = readyConversion
            val parsedOriginal = parsedInputMinor?.takeIf { inputCurrency != savingAccountingCurrency }
            val movementZone = ZoneId.of(initialMovement?.zoneId ?: "Asia/Riyadh")
            val movement = LedgerCommand.AddMovement(
                pocketId = pocketId,
                id = initialMovement?.id ?: draftId,
                createOnly = initialMovement == null,
                type = if (refund) MovementType.REFUND else MovementType.EXPENSE,
                accountingAmountMinor = parsedAmount,
                accountingCurrency = savingAccountingCurrency,
                occurredAtUtcMillis = parsedDate.atTime(parsedTime).atZone(movementZone).toInstant().toEpochMilli(),
                localDate = parsedDate,
                merchant = merchant,
                note = note,
                paymentMethodId = paymentMethod,
                originalAmountMinor = parsedOriginal,
                originalCurrencyCode = currency,
                conversionStatus = conversion.status,
                rate = conversion.rate,
                conversionEffectiveDate = conversion.effectiveDate,
                conversionSource = conversion.source,
            )
            saveInterrupted = true
            when (val result = ledger.execute(
                suggestion?.let { LedgerCommand.ConfirmSuggestion(it.id, movement, draftId) } ?: movement,
            )) {
                LedgerResult.Success -> {
                    saveInterrupted = false
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onSaved()
                }
                is LedgerResult.Rejected -> {
                    saveInterrupted = false
                    error = result.message
                }
                is LedgerResult.Deleted -> Unit
            }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                saveInterrupted = false
                error = "No se pudo guardar. Revisa el borrador y reintenta."
            } finally {
                saving = false
            }
        }
    }

    Scaffold(
        topBar = {
            PocketTopAppBar(
                title = when {
                    initialMovement != null -> "Editar movimiento"
                    refund -> "Nueva devolución"
                    else -> "Nuevo gasto"
                },
                onNavigateBack = onDismiss,
                navigationLabel = "Cerrar",
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = ::saveMovement,
                    enabled = !saving && selectedPocket != null && accountingAmountMinor != null && quoteState is QuoteUiState.Ready,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .heightIn(min = 52.dp)
                        .testTag("movement_save"),
                ) {
                    Text(
                        when {
                            saving -> "Guardando…"
                            initialMovement != null -> "Guardar cambios"
                            refund -> "Guardar devolución"
                            else -> "Guardar gasto · ${accountingCurrency.name} ${accountingAmountMinor?.let(::minorNumberForForm) ?: "0.00"}"
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .testTag("movement_form"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.templates.any { !it.archived }) {
                item {
                    Text("Plantillas", style = MaterialTheme.typography.titleMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.templates.filterNot { it.archived }, key = { it.id }) { template ->
                            AssistChip(
                                onClick = {
                                    selectedPocket = template.pocketId
                                    paymentMethod = template.paymentMethodId
                                    currency = template.inputCurrency.name
                                    amount = minorNumberForForm(template.amountMinor)
                                    templateGeneration++
                                },
                                label = { Text(template.name) },
                                leadingIcon = { Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
                            )
                        }
                    }
                }
            }
            item {
                Text("Importe", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                SegmentedChoice(
                    options = SupportedCurrency.entries.map { ChoiceOption(it, it.name, "movement_currency_${it.name}") },
                    selected = inputCurrency,
                    onSelect = { currency = it.name },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = {
                        amount = it
                        amountEdited = true
                        if (error == "Escribe un importe válido") error = null
                    },
                    prefix = { Text(inputCurrency.name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary) },
                    supportingText = if (amountIsInvalid || error == "Escribe un importe válido") {
                        { Text("Escribe un importe válido") }
                    } else {
                        null
                    },
                    isError = amountIsInvalid || error == "Escribe un importe válido",
                    singleLine = true,
                    textStyle = MaterialTheme.typography.displaySmall.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .testTag("movement_amount"),
                )
                when (val currentQuote = quoteState) {
                    QuoteUiState.Idle -> Unit
                    QuoteUiState.Loading -> Text("Consultando tipo de cambio…")
                    is QuoteUiState.Error -> Text(currentQuote.message, color = MaterialTheme.colorScheme.error)
                    is QuoteUiState.Ready -> if (inputCurrency != accountingCurrency && accountingAmountMinor != null) {
                        Text(MoneyText.format(accountingAmountMinor, accountingCurrency), style = MaterialTheme.typography.titleLarge)
                        Text("Efectiva: ${currentQuote.conversion.effectiveDate ?: "No registrada"}")
                        Text("Fuente: ${currentQuote.conversion.source ?: "Conversión manual histórica"}")
                    }
                }
            }
            item {
                Text("¿De qué Pocket?", style = MaterialTheme.typography.titleLarge)
                val availableByPocket = state.pockets.associate { it.pocket.id to it.availabilityMinor }
                val fontScale = LocalDensity.current.fontScale
                BoxWithConstraints {
                // Column count follows the width available per unit of text size, so names never split mid-word.
                val columns = ((maxWidth.value / fontScale) / 170f).toInt().coerceIn(1, 3)
                Column(
                    modifier = Modifier.selectableGroup().padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.pockets.filterNot { it.pocket.archived || it.retiredThisPeriod }.chunked(columns).forEach { rowPockets ->
                        Row(
                            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            rowPockets.forEach { pocket ->
                                PocketChoice(
                                    name = pocket.pocket.name,
                                    iconKey = pocket.pocket.iconKey,
                                    available = availableByPocket[pocket.pocket.id]?.let {
                                        availableText(it, state.currentPeriod?.accountingCurrency ?: accountingCurrency)
                                    },
                                    selected = selectedPocket == pocket.pocket.id,
                                    onSelect = {
                                        selectedPocket = pocket.pocket.id
                                        if (error == "Selecciona un Pocket") error = null
                                    },
                                    modifier = Modifier.weight(1f).fillMaxHeight().testTag("movement_pocket_${pocket.pocket.name}"),
                                )
                            }
                            repeat(columns - rowPockets.size) { Box(Modifier.weight(1f)) }
                        }
                    }
                }
                }
                if (error == "Selecciona un Pocket") {
                    Text(
                        error.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text("Comercio (opcional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text("Método de pago (opcional)", style = MaterialTheme.typography.titleMedium)
                SingleChoiceChips(
                    options = listOf(ChoiceOption<String?>(null, "Ninguno")) +
                        state.paymentMethods.filterNot { it.archived }.map { ChoiceOption<String?>(it.id, it.name) },
                    selected = paymentMethod,
                    onSelect = { paymentMethod = it },
                )
            }
            (error?.takeUnless {
                it == "Escribe un importe válido" || it == "Selecciona un Pocket"
            } ?: if (saveInterrupted && !saving) "No se confirmó el guardado. Reintenta con este borrador." else null)?.let { message ->
                item {
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                    )
                }
            }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CardDefaults.shape)
                        .toggleable(value = detailsExpanded, role = Role.Button, onValueChange = { detailsExpanded = it })
                        .semantics { stateDescription = if (detailsExpanded) "Expandido" else "Contraído" },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                                Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(if (detailsExpanded) "Ocultar detalles" else "Más detalles", style = MaterialTheme.typography.titleMedium)
                            Text("Fecha, nota y devolución", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(if (detailsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                    }
                }
            }
            if (detailsExpanded) {
                item {
                    Text("Tipo", style = MaterialTheme.typography.titleMedium)
                    SegmentedChoice(
                        options = listOf(ChoiceOption(false, "Gasto"), ChoiceOption(true, "Devolución")),
                        selected = refund,
                        onSelect = { refund = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
                item {
                    Text("Fecha y hora", style = MaterialTheme.typography.titleMedium)
                    val today = state.currentLocalDate
                    val parsedDate = runCatching { LocalDate.parse(localDate) }.getOrNull()
                    val parsedTime = runCatching { LocalTime.parse(localTime) }.getOrNull()
                    // The field is typed as HH:mm; on a 12-hour phone, the time is echoed in the phone's format.
                    val twelveHourTime = parsedTime?.takeUnless { DateFormat.is24HourFormat(LocalContext.current) }
                    SingleChoiceChips(
                        options = listOf(ChoiceOption(today, "Hoy"), ChoiceOption(today.minusDays(1), "Ayer")),
                        selected = parsedDate,
                        onSelect = { localDate = it.toString() },
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                    Spacer(Modifier.size(4.dp))
                    OutlinedTextField(
                        value = localDate,
                        onValueChange = { localDate = it },
                        label = { Text("Fecha (AAAA-MM-DD)") },
                        supportingText = parsedDate?.let { { Text(it.format(longDate)) } },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { datePickerVisible = true }) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = "Elegir fecha")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.size(8.dp))
                    OutlinedTextField(
                        value = localTime,
                        onValueChange = { localTime = it },
                        label = { Text("Hora (HH:mm)") },
                        supportingText = twelveHourTime?.let { { Text(it.format(twelveHourClock)) } },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { timePickerVisible = true }) {
                                Icon(Icons.Default.Schedule, contentDescription = "Elegir hora")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Nota (opcional)") },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )

                }
            }
        }
    }
    if (datePickerVisible) {
        MovementDatePicker(
            initial = runCatching { LocalDate.parse(localDate) }.getOrNull() ?: state.currentLocalDate,
            periods = state.periods,
            onPicked = { localDate = it.toString(); datePickerVisible = false },
            onDismiss = { datePickerVisible = false },
        )
    }
    if (timePickerVisible) {
        TimeOfDayPickerDialog(
            initial = runCatching { LocalTime.parse(localTime) }.getOrNull() ?: LocalTime.NOON,
            onPicked = { localTime = it.toString(); timePickerVisible = false },
            onDismiss = { timePickerVisible = false },
        )
    }
}

private val longDate = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM yyyy", Locale.forLanguageTag("es"))
private val twelveHourClock = DateTimeFormatter.ofPattern("h:mm a", Locale.forLanguageTag("es"))

/** Radio-style Pocket card: artwork, name, and current availability, with selected semantics. */
@Composable
private fun PocketChoice(
    name: String,
    iconKey: com.aif31.pocket.data.PocketIconKey,
    available: AnnotatedString?,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = modifier
            .heightIn(min = 64.dp)
            .clip(MaterialTheme.shapes.medium)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Box {
                PocketArtworkPlate(iconKey, plateSize = 40.dp, selected = selected)
                if (selected) {
                    // Non-color selection cue that does not take width from the Pocket name.
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.align(androidx.compose.ui.Alignment.BottomEnd).size(16.dp),
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.padding(2.dp))
                    }
                }
            }
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                available?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

// DatePicker/DatePickerDialog are still @ExperimentalMaterial3Api in Material3 1.4.0. They replace typed
// "AAAA-MM-DD" entry, restrict choices to existing periods, and the typed field remains as a fallback.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MovementDatePicker(
    initial: LocalDate,
    periods: List<com.aif31.pocket.data.Period>,
    onPicked: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectable = remember(periods) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val date = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                return periods.any { date >= it.start && date < it.endExclusive }
            }
        }
    }
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = selectable,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = pickerState.selectedDateMillis != null,
                onClick = {
                    pickerState.selectedDateMillis?.let {
                        onPicked(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                },
            ) { Text("Aceptar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    ) {
        DatePicker(state = pickerState)
    }
}

private fun minorNumberForForm(minor: Long): String =
    MoneyText.editable(minor)
