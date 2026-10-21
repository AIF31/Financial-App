package com.aif31.pocket

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.aif31.pocket.data.LedgerState
import com.aif31.pocket.data.MerchantSpend
import com.aif31.pocket.data.MovementDrill
import com.aif31.pocket.data.PeriodInsights
import com.aif31.pocket.data.RecurringCharges
import com.aif31.pocket.data.SpendingAnalysis
import com.aif31.pocket.data.medianMinor
import com.aif31.pocket.domain.SupportedCurrency
import com.aif31.pocket.ui.DisclosureRow
import com.aif31.pocket.ui.MetricRow
import com.aif31.pocket.ui.MoneyText
import com.aif31.pocket.ui.PocketArtworkPlate
import com.aif31.pocket.ui.PocketTopAppBar
import com.aif31.pocket.ui.Sparkline
import com.aif31.pocket.ui.SpendCalendar
import com.aif31.pocket.ui.TrendBar
import com.aif31.pocket.ui.TrendBars
import com.aif31.pocket.ui.ValueBar
import com.aif31.pocket.ui.counted
import com.aif31.pocket.ui.formatPeriodRange
import com.aif31.pocket.ui.recurringSpanish
import com.aif31.pocket.ui.shortSpanish
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private const val TOP_MERCHANTS = 8
private val monthLabel = DateTimeFormatter.ofPattern("MMM", Locale.forLanguageTag("es"))
private val dayTitle = DateTimeFormatter.ofPattern("EEEE d MMM", Locale.forLanguageTag("es"))
private val recurringDate = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es"))

/**
 * Breakdowns of one budget period: how daily spending trends across periods, where and when the money went, how it
 * was paid, what was spent in other currencies, and how rollover has grown. Every figure is read-only.
 */
@Composable
internal fun AnalysisScreen(
    state: LedgerState,
    periodId: String,
    onPeriodChange: (String) -> Unit,
    onBack: () -> Unit,
    /** Opens the Movements behind a tapped figure, titled with what was tapped. */
    onOpenMovements: (drill: MovementDrill, title: String) -> Unit = { _, _ -> },
) {
    val analysis = remember(state, periodId) { SpendingAnalysis.of(state, periodId) }
    val recurring = remember(state, periodId) {
        if (periodId == state.currentPeriod?.id) RecurringCharges.of(state, periodId) else null
    }
    Scaffold(topBar = { PocketTopAppBar(title = "Análisis del periodo", onNavigateBack = onBack) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("analysis_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val bounded = Modifier.fillMaxWidth().widthIn(max = 760.dp)
            item {
                PeriodPicker(
                    label = "Periodo",
                    periods = state.periods,
                    selectedId = periodId,
                    onSelect = onPeriodChange,
                    modifier = bounded,
                )
            }
            if (analysis == null) return@LazyColumn
            val currency = analysis.period.accountingCurrency
            item { Section("Tendencia del gasto diario", bounded) { TrendSection(analysis, currency, onPeriodChange) } }
            recurring?.let { charges ->
                item {
                    Section("Cargos recurrentes", bounded) {
                        if (charges.items.isEmpty()) Muted("No hay cargos recurrentes pendientes detectados.")
                        charges.items.forEach { charge ->
                            Column(
                                Modifier.fillMaxWidth().opensMovements {
                                    onOpenMovements(MovementDrill.merchantHistory(periodId, charge.merchantKey), charge.merchantName)
                                }.padding(vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(charge.merchantName, style = MaterialTheme.typography.bodyLarge)
                                Muted(charge.pocketName)
                                Text(
                                    "Esperado el ${charge.expectedDate.format(recurringDate)} · ${MoneyText.format(charge.expectedAmountMinor, currency)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                if (charge.overdue) Text("Vencido", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        Text("Faltan por cobrarse: ${MoneyText.format(charges.totalExpectedMinor, currency)}", style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
            item { Section("Comercios principales", bounded) { MerchantSection(analysis, currency, onOpenMovements) } }
            item { Section("Cuándo gastas", bounded) { WhenSection(analysis, currency, onOpenMovements) } }
            item { Section("Métodos de pago", bounded) { PaymentMethodSection(analysis, currency, onOpenMovements) } }
            item { Section("Moneda extranjera", bounded) { ForeignCurrencySection(analysis, currency) } }
            if (analysis.rollover.isNotEmpty()) {
                item { Section("Rollover acumulado", bounded) { RolloverSection(analysis, currency) } }
            }
        }
    }
}

@Composable
private fun Section(title: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

/** A row that opens the Movements behind it, announced as such. */
private fun Modifier.opensMovements(onClick: () -> Unit): Modifier = composed {
    clip(MaterialTheme.shapes.small).clickable(onClickLabel = "Ver movimientos", role = Role.Button, onClick = onClick)
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The month most of a period falls in: a period from 25 Feb to 24 Mar reads as "mar". */
private fun PeriodInsights.shortLabel(): String =
    period.start.plusDays(totalDays / 2L).format(monthLabel).trimEnd('.')

@Composable
private fun TrendSection(analysis: SpendingAnalysis, currency: SupportedCurrency, onPeriodChange: (String) -> Unit) {
    fun money(minor: Long) = MoneyText.format(minor, currency)
    val trend = analysis.trend
    val selected = analysis.insights
    Text(
        selected.averageDailySpendMinor?.let { "${money(it)} al día en el periodo elegido" } ?: "Sin días transcurridos",
        style = MaterialTheme.typography.titleLarge,
    )
    val earlier = trend.dropLast(1).mapNotNull { it.averageDailySpendMinor }
    if (earlier.isEmpty()) {
        Muted("Aún no hay periodos anteriores en ${currency.name} para mostrar una tendencia.")
    } else {
        val typical = medianMinor(earlier)!!
        Muted("Mediana de ${counted(earlier.size, "periodo anterior", "periodos anteriores")}: ${money(typical)} al día.")
        val description = "Gasto diario promedio por periodo. " + trend.joinToString(". ") { insights ->
            "${formatPeriodRange(insights.period.start, insights.period.endExclusive)}: " +
                (insights.averageDailySpendMinor?.let(::money) ?: "sin días") + if (insights.inProgress) ", en curso" else ""
        }
        TrendBars(
            bars = trend.map { TrendBar(it.shortLabel(), it.averageDailySpendMinor, it.period.id == selected.period.id) },
            description = description,
            onSelect = { onPeriodChange(trend[it].period.id) },
        )
        Muted("Toca una barra para ver ese periodo.")
    }
    if (trend.any { it.inProgress }) {
        Muted("El periodo en curso cuenta solo el gasto hasta hoy.")
    }
    if (trend.any { it.period.isTransition }) {
        Muted("Los periodos de transición son más largos; se comparan por promedio diario.")
    }
    if (analysis.earlierPeriodsInOtherCurrency) {
        Muted("Los periodos anteriores usan otra moneda y no se incluyen.")
    }
    var tableVisible by rememberSaveable { mutableStateOf(false) }
    if (trend.size > 1) {
        DisclosureRow(tableVisible, { tableVisible = it }, if (tableVisible) "Ocultar tabla" else "Ver tabla de periodos")
        if (tableVisible) {
            trend.asReversed().forEach { insights ->
                MetricRow(
                    label = formatPeriodRange(insights.period.start, insights.period.endExclusive),
                    value = insights.averageDailySpendMinor?.let { "${money(it)}/día" } ?: "—",
                    supporting = buildString {
                        append("Gasto neto ${money(insights.netSpendMinor)}")
                        insights.budgetUsedPercent?.let { append(" · $it% del presupuesto") }
                        if (insights.inProgress) append(" · en curso")
                    },
                )
            }
        }
    }
    if (trend.size > 1 && analysis.pocketTrends.isNotEmpty()) {
        Text("Por Pocket", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        analysis.pocketTrends.forEach { pocketTrend ->
            val latest = pocketTrend.averageDailyByPeriodMinor.last()
            Row(
                Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PocketArtworkPlate(pocketTrend.pocket.iconKey, plateSize = 32.dp)
                Column(Modifier.weight(1f)) {
                    Text(pocketTrend.pocket.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        latest?.let { "${money(it)} al día" } ?: "—",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Sparkline(
                    pocketTrend.averageDailyByPeriodMinor,
                    description = "${pocketTrend.pocket.name}: promedio diario por periodo, " +
                        pocketTrend.averageDailyByPeriodMinor.joinToString(", ") { it?.let(::money) ?: "sin días" },
                )
            }
        }
    }
}

private fun merchantSupporting(merchant: MerchantSpend, currency: SupportedCurrency): String = buildString {
    append(counted(merchant.expenseCount, "compra", "compras"))
    merchant.averageExpenseMinor?.let { append(" · media ${MoneyText.format(it, currency)}") }
    val previous = merchant.previousNetSpendMinor ?: return@buildString
    val delta = merchant.netSpendMinor - previous
    when {
        previous == 0L -> append(" · nuevo este periodo")
        delta == 0L -> append(" · igual que el periodo anterior")
        // A no-break space keeps the arrow on the same line as the amount it describes.
        else -> append(" · ${if (delta > 0) "▲" else "▼"}\u00A0${MoneyText.format(abs(delta), currency)} frente al anterior")
    }
}

@Composable
private fun MerchantSection(analysis: SpendingAnalysis, currency: SupportedCurrency, onOpenMovements: (MovementDrill, String) -> Unit) {
    if (analysis.merchants.isEmpty()) {
        Muted("Aún no hay movimientos con comercio en este periodo.")
        return
    }
    var showAll by rememberSaveable { mutableStateOf(false) }
    val shown = if (showAll) analysis.merchants else analysis.merchants.take(TOP_MERCHANTS)
    shown.forEach { merchant ->
        MetricRow(
            label = merchant.name,
            value = MoneyText.format(merchant.netSpendMinor, currency),
            supporting = merchantSupporting(merchant, currency),
            modifier = Modifier.testTag("merchant_row").opensMovements {
                onOpenMovements(MovementDrill.merchant(analysis.period.id, merchant.key), merchant.name)
            },
        )
    }
    Muted("Toca un comercio para ver sus movimientos.")
    if (analysis.merchants.size > TOP_MERCHANTS) {
        DisclosureRow(
            showAll,
            { showAll = it },
            if (showAll) "Mostrar menos" else "Ver los ${analysis.merchants.size} comercios",
        )
    }
    if (analysis.unnamedNetSpendMinor != 0L) {
        Muted("Sin comercio registrado: ${MoneyText.format(analysis.unnamedNetSpendMinor, currency)}.")
    }
}

@Composable
private fun WhenSection(analysis: SpendingAnalysis, currency: SupportedCurrency, onOpenMovements: (MovementDrill, String) -> Unit) {
    val insights = analysis.insights
    if (insights.elapsedDays == 0) {
        Muted("El periodo aún no ha comenzado.")
        return
    }
    val busiest = analysis.weekdays.filter { (it.averagePerDayMinor ?: 0) > 0 }.maxByOrNull { it.averagePerDayMinor!! }
    Text(
        busiest?.let { "Gastas más ${it.dayOfWeek.recurringSpanish()}: ${MoneyText.format(it.averagePerDayMinor!!, currency)} de media" }
            ?: "Aún no hay gasto hasta hoy.",
        style = MaterialTheme.typography.bodyLarge,
    )
    Text("Promedio por día de la semana", style = MaterialTheme.typography.titleSmall)
    val maxAverage = analysis.weekdays.maxOf { it.averagePerDayMinor ?: 0 }.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        analysis.weekdays.forEach { day ->
            val average = day.averagePerDayMinor
            ValueBar(
                label = day.dayOfWeek.shortSpanish(),
                fraction = (average ?: 0).coerceAtLeast(0).toFloat() / maxAverage,
                value = average?.let { MoneyText.format(it, currency) } ?: "—",
                modifier = Modifier
                    .opensMovements {
                        val title = day.dayOfWeek.recurringSpanish().replaceFirstChar { it.uppercase() }
                        onOpenMovements(MovementDrill.weekday(analysis.period.id, day.dayOfWeek), title)
                    }
                    .semantics(mergeDescendants = true) {},
            )
        }
    }
    Text("Calendario del periodo", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
    SpendCalendar(
        start = insights.period.start,
        totalDays = insights.totalDays,
        dailyNetSpendMinor = analysis.dailyNetSpendMinor,
        currency = currency,
        onDayClick = { date -> onOpenMovements(MovementDrill.day(analysis.period.id, date), date.format(dayTitle).replaceFirstChar { it.uppercase() }) },
        modifier = Modifier.widthIn(max = 420.dp),
    )
    if (insights.netSpendAfterTodayMinor != 0L) {
        Muted("No incluye ${MoneyText.format(insights.netSpendAfterTodayMinor, currency)} netos con fecha posterior a hoy.")
    }
}

@Composable
private fun PaymentMethodSection(analysis: SpendingAnalysis, currency: SupportedCurrency, onOpenMovements: (MovementDrill, String) -> Unit) {
    val methods = analysis.paymentMethods
    if (methods.isEmpty()) {
        Muted("Aún no hay movimientos en este periodo.")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        methods.forEach { method ->
            val label = method.name ?: "Sin método"
            Column(
                Modifier
                    .opensMovements { onOpenMovements(MovementDrill.paymentMethod(analysis.period.id, method.id), label) }
                    .semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(Modifier.fillMaxWidth()) {
                    Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        MoneyText.format(method.netSpendMinor, currency),
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                ValueBar(
                    label = counted(method.movementCount, "mov.", "mov."),
                    fraction = (method.sharePercent ?: 0) / 100f,
                    value = method.sharePercent?.let { "$it%" } ?: "—",
                    labelWidth = 56,
                )
            }
        }
    }
    if (methods.all { it.id == null }) {
        Muted("Elige un método de pago al registrar gastos para ver este desglose.")
    }
}

@Composable
private fun ForeignCurrencySection(analysis: SpendingAnalysis, currency: SupportedCurrency) {
    if (analysis.foreignCurrencies.isEmpty()) {
        Muted("Todo el gasto de este periodo se registró en ${currency.name}.")
        return
    }
    analysis.foreignSharePercent?.let {
        Text("$it% del gasto neto se registró en otra moneda", style = MaterialTheme.typography.bodyLarge)
    }
    analysis.foreignCurrencies.forEach { foreign ->
        MetricRow(
            label = foreign.currency.name,
            value = MoneyText.format(foreign.accountingNetMinor, currency),
            supporting = buildString {
                append("Original ${MoneyText.format(foreign.originalNetMinor, foreign.currency)}")
                append(" · ${counted(foreign.movementCount, "movimiento", "movimientos")}")
                if (foreign.estimatedCount > 0) {
                    append(
                        " · ${counted(foreign.estimatedCount, "estimado", "estimados")} " +
                            "(${MoneyText.format(foreign.estimatedAccountingNetMinor, currency)}) hasta el tipo final",
                    )
                }
            },
        )
    }
}

@Composable
private fun RolloverSection(analysis: SpendingAnalysis, currency: SupportedCurrency) {
    val inProgress = analysis.insights.inProgress
    val showSparkline = analysis.trend.size > 1
    analysis.rollover.forEach { pocket ->
        val received = pocket.rolloverByPeriodMinor.last()
        Row(
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PocketArtworkPlate(pocket.pocket.iconKey, plateSize = 32.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(pocket.pocket.name, style = MaterialTheme.typography.bodyLarge)
                Muted("Recibido en este periodo: ${MoneyText.format(received, currency)}")
                Muted(
                    (if (inProgress) "Pasaría al siguiente si terminara hoy: " else "Pasó al siguiente: ") +
                        MoneyText.format(pocket.carriesForwardMinor, currency),
                )
            }
            if (showSparkline) {
                Sparkline(
                    pocket.rolloverByPeriodMinor,
                    description = "${pocket.pocket.name}: rollover recibido por periodo, " +
                        pocket.rolloverByPeriodMinor.joinToString(", ") { MoneyText.format(it, currency) },
                )
            }
        }
    }
}
