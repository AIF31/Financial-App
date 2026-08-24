package com.aif31.pocket

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aif31.pocket.data.ConversionStatus
import com.aif31.pocket.data.LedgerCommand
import com.aif31.pocket.data.LedgerResult
import com.aif31.pocket.data.LedgerState
import com.aif31.pocket.data.Movement
import com.aif31.pocket.data.MovementType
import com.aif31.pocket.data.PocketLedger
import com.aif31.pocket.domain.Money
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProductionMovementScreen(
    state: LedgerState,
    ledger: PocketLedger,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    initialMovement: Movement? = null,
) {
    val stateKey = initialMovement?.id
    var amount by remember(stateKey) {
        mutableStateOf(initialMovement?.let { minorNumberForForm(it.sarAmountMinor) }.orEmpty())
    }
    var selectedPocket by remember(stateKey) { mutableStateOf(initialMovement?.pocketId) }
    var refund by remember(stateKey) { mutableStateOf(initialMovement?.type == MovementType.REFUND) }
    var merchant by remember(stateKey) { mutableStateOf(initialMovement?.merchant.orEmpty()) }
    var note by remember(stateKey) { mutableStateOf(initialMovement?.note.orEmpty()) }
    var paymentMethod by remember(stateKey) { mutableStateOf(initialMovement?.paymentMethodId) }
    var currency by remember(stateKey) { mutableStateOf(initialMovement?.originalCurrencyCode ?: "SAR") }
    var originalAmount by remember(stateKey) {
        mutableStateOf(initialMovement?.originalAmountMinor?.let(::minorNumberForForm).orEmpty())
    }
    var confirmed by remember(stateKey) {
        mutableStateOf(initialMovement?.conversionStatus != ConversionStatus.ESTIMATED)
    }
    var localDate by remember(stateKey) {
        mutableStateOf((initialMovement?.localDate ?: state.currentLocalDate).toString())
    }
    var localTime by remember(stateKey) {
        val instant = initialMovement?.occurredAtUtcMillis ?: state.currentInstantMillis
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
    var error by remember(stateKey) { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(stateKey) {
        focusRequester.requestFocus()
    }

    fun saveMovement() {
        scope.launch {
            val parsedAmount = runCatching { Money.parse(amount, "SAR").minor }.getOrNull() ?: run {
                error = "Escribe un importe válido"
                return@launch
            }
            val pocketId = selectedPocket ?: run {
                error = "Selecciona un Pocket"
                return@launch
            }
            val parsedDate = runCatching { LocalDate.parse(localDate) }.getOrNull() ?: run {
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
            when (
                val result = ledger.execute(
                    LedgerCommand.AddMovement(
                        pocketId = pocketId,
                        id = initialMovement?.id,
                        type = if (refund) MovementType.REFUND else MovementType.EXPENSE,
                        sarAmountMinor = parsedAmount,
                        occurredAtUtcMillis = parsedDate.atTime(parsedTime)
                            .atZone(movementZone)
                            .toInstant()
                            .toEpochMilli(),
                        localDate = parsedDate,
                        merchant = merchant,
                        note = note,
                        paymentMethodId = paymentMethod,
                        originalAmountMinor = parsedOriginal,
                        originalCurrencyCode = currency,
                        conversionStatus = if (currency == "SAR" || confirmed) {
                            ConversionStatus.CONFIRMED
                        } else {
                            ConversionStatus.ESTIMATED
                        },
                    ),
                )
            ) {
                LedgerResult.Success -> onSaved()
                is LedgerResult.Rejected -> error = result.message
                is LedgerResult.Deleted -> Unit
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            initialMovement != null -> "Editar movimiento"
                            refund -> "Nueva devolución"
                            else -> "Nuevo gasto"
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cerrar")
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = ::saveMovement,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .heightIn(min = 52.dp),
                ) {
                    Text(
                        when {
                            initialMovement != null -> "Guardar cambios"
                            refund -> "Guardar devolución"
                            else -> "Guardar gasto"
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
                            OutlinedButton(
                                onClick = {
                                    amount = minorNumberForForm(template.amountMinor)
                                    selectedPocket = template.pocketId
                                    paymentMethod = template.paymentMethodId
                                },
                            ) {
                                Text(template.name)
                            }
                        }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = amount,
                    onValueChange = {
                        amount = it
                        if (error == "Escribe un importe válido") error = null
                    },
                    label = { Text("Importe SAR") },
                    supportingText = if (error == "Escribe un importe válido") {
                        { Text(error.orEmpty()) }
                    } else {
                        null
                    },
                    isError = error == "Escribe un importe válido",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .testTag("movement_amount"),
                )
            }
            item {
                Text("Pocket", style = MaterialTheme.typography.titleMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.pockets.filterNot { it.pocket.archived }, key = { it.pocket.id }) { pocket ->
                        OutlinedButton(
                            onClick = {
                                selectedPocket = pocket.pocket.id
                                if (error == "Selecciona un Pocket") error = null
                            },
                            modifier = Modifier.testTag("movement_pocket_${pocket.pocket.name}"),
                        ) {
                            Text(if (selectedPocket == pocket.pocket.id) "✓ ${pocket.pocket.name}" else pocket.pocket.name)
                        }
                    }
                }
                if (error == "Selecciona un Pocket") {
                    Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                }
            }
            item {
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text("Comercio (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text("Método de pago (opcional)", style = MaterialTheme.typography.titleMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        OutlinedButton(onClick = { paymentMethod = null }) {
                            Text(if (paymentMethod == null) "✓ Ninguno" else "Ninguno")
                        }
                    }
                    items(state.paymentMethods.filterNot { it.archived }, key = { it.id }) { method ->
                        OutlinedButton(onClick = { paymentMethod = method.id }) {
                            Text(if (paymentMethod == method.id) "✓ ${method.name}" else method.name)
                        }
                    }
                }
            }
            error?.takeUnless {
                it == "Escribe un importe válido" || it == "Selecciona un Pocket"
            }?.let { message ->
                item {
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                    )
                }
            }
            item {
                TextButton(onClick = { detailsExpanded = !detailsExpanded }) {
                    Text(if (detailsExpanded) "Ocultar detalles" else "Más detalles")
                }
            }
            if (detailsExpanded) {
                item {
                    Text("Tipo", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { refund = false }) {
                            Text(if (!refund) "✓ Gasto" else "Gasto")
                        }
                        OutlinedButton(onClick = { refund = true }) {
                            Text(if (refund) "✓ Devolución" else "Devolución")
                        }
                    }
                }
                item {
                    Text("Moneda original", style = MaterialTheme.typography.titleMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf("SAR", "USD", "MXN")) { code ->
                            OutlinedButton(onClick = { currency = code }) {
                                Text(if (currency == code) "✓ $code" else code)
                            }
                        }
                    }
                    if (currency != "SAR") {
                        OutlinedTextField(
                            value = originalAmount,
                            onValueChange = { originalAmount = it },
                            label = { Text("Importe original $currency") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { confirmed = false }) {
                                Text(if (!confirmed) "✓ Estimado" else "Estimado")
                            }
                            OutlinedButton(onClick = { confirmed = true }) {
                                Text(if (confirmed) "✓ Confirmado" else "Confirmado")
                            }
                        }
                    }
                }
                item {
                    Text("Fecha y hora", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = localDate,
                        onValueChange = { localDate = it },
                        label = { Text("Fecha (AAAA-MM-DD)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = localTime,
                        onValueChange = { localTime = it },
                        label = { Text("Hora (HH:mm)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Nota (opcional)") },
                        modifier = Modifier.fillMaxWidth(),
                    )

                }
            }
        }
    }
}

private fun minorNumberForForm(minor: Long): String =
    BigDecimal.valueOf(minor).movePointLeft(2).toPlainString()
