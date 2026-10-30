package com.aif31.pocket

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.aif31.pocket.data.LedgerState
import com.aif31.pocket.data.MerchantSpend
import com.aif31.pocket.data.MovementDrill
import com.aif31.pocket.data.Pocket
import com.aif31.pocket.data.RecurringCharges
import com.aif31.pocket.data.SpendingAnalysis
import com.aif31.pocket.data.medianMinor
import com.aif31.pocket.domain.SupportedCurrency
import com.aif31.pocket.ui.DisclosureRow
import com.aif31.pocket.ui.MetricRow
import com.aif31.pocket.ui.MoneyText
import com.aif31.pocket.ui.NameValueRow
import com.aif31.pocket.ui.PeriodLabels
import com.aif31.pocket.ui.PocketArtworkPlate
import com.aif31.pocket.ui.PocketTopAppBar
import com.aif31.pocket.ui.RelativeLine
import com.aif31.pocket.ui.RolloverBar
import com.aif31.pocket.ui.RolloverLegend
import com.aif31.pocket.ui.SpendCalendar
import com.aif31.pocket.ui.TrendBar
import com.aif31.pocket.ui.TrendBars
import com.aif31.pocket.ui.TrendReference
import com.aif31.pocket.ui.ValueBar
import com.aif31.pocket.ui.changeSpoken
import com.aif31.pocket.ui.changeText
import com.aif31.pocket.ui.counted
import com.aif31.pocket.ui.formatPeriodRange
import com.aif31.pocket.ui.recurringSpanish
import com.aif31.pocket.ui.sharePercent
import com.aif31.pocket.ui.shortLabel
import com.aif31.pocket.ui.shortSpanish
import com.aif31.pocket.ui.unbroken
import com.aif31.pocket.ui.widestTextWidth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import com.aif31.pocket.ui.plusMargin

private const val TOP_MERCHANTS = 8

/** A line needs at least three periods to show a pattern; two points would only repeat the change written beside it. */
private const val MIN_LINE_PERIODS = 3

/** Where a Pocket row's text and chart start: past its 32 dp plate and the 10 dp gap after it. */
private val ROW_INDENT = 42.dp
private val dayTitle = DateTimeFormatter.ofPattern("EEEE d MMM", Locale.forLanguageTag("es"))
private val recurringDate = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es"))

/**
 * Breakdowns of one budget period: how daily spending trends across periods, each Pocket's own pattern, where and
 * when the money went, how it was paid, what was spent in other currencies, and how rollover has moved. Every figure
 * is read-only. The sections follow [sectionOrder], which the person changes from the app bar.
 */
@Composable
internal fun AnalysisScreen(
    state: LedgerState,
    periodId: String,
    onPeriodChange: (String) -> Unit,
    onBack: () -> Unit,
    /** Opens the Movements behind a tapped figure, titled with what was tapped. */
    onOpenMovements: (drill: MovementDrill, title: String) -> Unit = { _, _ -> },
    sectionOrder: List<AnalysisSection> = AnalysisSection.entries,
    onSectionOrderChange: (List<AnalysisSection>) -> Unit = {},
) {
    val analysis = remember(state, periodId) { SpendingAnalysis.of(state, periodId) }
    val recurring = remember(state, periodId) {
        if (periodId == state.currentPeriod?.id) RecurringCharges.of(state, periodId) else null
    }
    var organizing by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            PocketTopAppBar(
                title = "Análisis del periodo",
                onNavigateBack = onBack,
                actions = {
                    IconButton(onClick = { organizing = true }, modifier = Modifier.testTag("organize_sections")) {
                        Icon(Icons.Default.Reorder, contentDescription = "Organizar secciones")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("analysis_list"),
            contentPadding = padding.plusMargin(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val bounded = Modifier.fillMaxWidth().widthIn(max = 760.dp)
            item(key = "period-picker", contentType = "period-picker") {
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
            sectionOrder.forEach { section ->
                when (section) {
                    AnalysisSection.TREND -> item(key = section.name, contentType = section.name) {
                        Section(section.title, bounded) { TrendSection(analysis, currency, onPeriodChange) }
                    }
                    AnalysisSection.POCKETS -> if (analysis.pocketTrends.isNotEmpty()) {
                        item(key = section.name, contentType = section.name) { Section(section.title, bounded) { PocketSection(analysis, currency) } }
                    }
                    AnalysisSection.RECURRING -> if (recurring != null) {
                        item(key = section.name, contentType = section.name) {
                            Section(section.title, bounded) { RecurringSection(recurring, currency, periodId, onOpenMovements) }
                        }
                    }
                    AnalysisSection.MERCHANTS -> item(key = section.name, contentType = section.name) {
                        Section(section.title, bounded) { MerchantSection(analysis, currency, onOpenMovements) }
                    }
                    AnalysisSection.WHEN -> item(key = section.name, contentType = section.name) {
                        Section(section.title, bounded) { WhenSection(analysis, currency, onOpenMovements) }
                    }
                    AnalysisSection.PAYMENT_METHODS -> item(key = section.name, contentType = section.name) {
                        Section(section.title, bounded) { PaymentMethodSection(analysis, currency, onOpenMovements) }
                    }
                    AnalysisSection.FOREIGN_CURRENCY -> item(key = section.name, contentType = section.name) {
                        Section(section.title, bounded) { ForeignCurrencySection(analysis, currency) }
                    }
                    AnalysisSection.ROLLOVER -> if (analysis.rollover.isNotEmpty()) {
                        item(key = section.name, contentType = section.name) { Section(section.title, bounded) { RolloverSection(analysis, currency) } }
                    }
                }
            }
        }
    }
    if (organizing) {
        SectionOrderSheet(order = sectionOrder, onOrderChange = onSectionOrderChange, onDismiss = { organizing = false })
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
private fun Muted(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

@Composable
private fun TrendSection(analysis: SpendingAnalysis, currency: SupportedCurrency, onPeriodChange: (String) -> Unit) {
    fun money(minor: Long) = MoneyText.unbroken(minor, currency)
    val trend = analysis.trend
    val selected = analysis.insights
    val average = selected.averageDailySpendMinor
    Text(
        average?.let { perDayHeadline(money(it)) } ?: AnnotatedString("Sin días transcurridos"),
        style = MaterialTheme.typography.headlineMedium,
    )
    val earlier = trend.dropLast(1).filter { it.averageDailySpendMinor != null }
    val notes = mutableListOf<String>()
    if (earlier.isEmpty()) {
        Muted("Aún no hay periodos anteriores en ${currency.name} para mostrar una tendencia.")
    } else {
        val typical = medianMinor(earlier.map { it.averageDailySpendMinor!! })!!
        // One earlier period is its own median, so the chip names that period instead of "lo habitual".
        val single = earlier.singleOrNull()?.shortLabel()
        val than = single?.let { "que $it" } ?: "de lo habitual"
        val unchanged = "Igual que ${single ?: "lo habitual"}"
        average?.let {
            DeltaChip(
                text = changeText(it - typical, typical, currency, than = than, unchanged = unchanged),
                spoken = changeSpoken(it - typical, typical, currency, than = than, unchanged = unchanged),
            )
        }
        if (single == null) {
            Muted("Mediana de ${counted(earlier.size, "periodo anterior", "periodos anteriores")}: ${money(typical)} al día.")
        }
        val description = "Gasto diario promedio por periodo. " + trend.joinToString(". ") { insights ->
            "${formatPeriodRange(insights.period.start, insights.period.endExclusive)}: " +
                (insights.averageDailySpendMinor?.let { MoneyText.format(it, currency) } ?: "sin días") +
                if (insights.inProgress) ", en curso" else ""
        }
        TrendBars(
            bars = trend.map { TrendBar(it.shortLabel(), it.averageDailySpendMinor, it.period.id == selected.period.id) },
            description = description,
            onSelect = { onPeriodChange(trend[it].period.id) },
            valueLabel = MoneyText::grouped,
            reference = if (single == null) TrendReference(typical, "mediana ${MoneyText.grouped(typical)}") else null,
            modifier = Modifier.padding(top = 8.dp),
        )
        notes += "Toca una barra para ver ese periodo."
    }
    if (trend.any { it.inProgress }) notes += "El periodo en curso cuenta solo el gasto hasta hoy."
    if (trend.any { it.period.isTransition }) notes += "Los periodos de transición son más largos; se comparan por promedio diario."
    if (analysis.earlierPeriodsInOtherCurrency) notes += "Los periodos anteriores usan otra moneda y no se incluyen."
    if (notes.isNotEmpty()) Muted(notes.joinToString(" "))
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
}

/** "SAR 99.42 al día", the unit smaller and quieter than the amount. */
@Composable
private fun perDayHeadline(amount: String): AnnotatedString = buildAnnotatedString {
    append(amount)
    withStyle(
        SpanStyle(
            fontSize = MaterialTheme.typography.bodyLarge.fontSize,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) { append(" al día") }
}

@Composable
private fun DeltaChip(text: AnnotatedString, spoken: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.small,
        modifier = modifier.clearAndSetSemantics { contentDescription = spoken },
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

/**
 * Each Pocket's average daily spend in this period, with the change from the previous one and its share of the total.
 * From three periods up, a line shows each Pocket's own pattern against its usual level; the amount beside it carries
 * the size, so a small Pocket's line is as readable as a large one's.
 */
@Composable
private fun PocketSection(analysis: SpendingAnalysis, currency: SupportedCurrency) {
    fun money(minor: Long) = MoneyText.unbroken(minor, currency)
    val labels = analysis.trend.map { it.shortLabel() }
    val selectedLabel = labels.last()
    val previousLabel = labels.getOrNull(labels.lastIndex - 1)
    val drawLines = labels.size >= MIN_LINE_PERIODS
    // Only Pockets with no net movement at all are quiet; one whose refunds outweigh its spending keeps its negative
    // amounts. Both decisions use unrounded net spend, so a few cents never vanish into a rounded daily average of zero.
    val (quiet, active) = analysis.pocketTrends.partition { trend -> trend.netSpendByPeriodMinor.all { it == 0L } }
    // Shares are of the net total, as refunds reduce net spending everywhere else in the app.
    val netTotal = active.sumOf { it.netSpendByPeriodMinor.last() }
    Muted(
        when {
            drawLines -> "Promedio al día en los últimos ${labels.size} periodos. La línea discontinua es lo habitual de cada Pocket."
            previousLabel != null -> "Promedio al día, frente a $previousLabel."
            else -> "Promedio al día."
        },
    )
    if (drawLines) {
        PeriodLabels(labels, selectedIndex = labels.lastIndex, modifier = Modifier.fillMaxWidth().padding(start = ROW_INDENT))
    }
    active.forEach { trend ->
        val values = trend.averageDailyByPeriodMinor
        val current = values.last()
        val previous = if (previousLabel != null) values[values.lastIndex - 1] else null
        // Whether a period had any spending is read from its unrounded net, not from a daily average that rounds to zero.
        val currentNet = trend.netSpendByPeriodMinor.last()
        val previousNet = trend.netSpendByPeriodMinor.getOrNull(trend.netSpendByPeriodMinor.lastIndex - 1)
        val change: AnnotatedString?
        val spokenChange: String?
        when {
            current == null || previous == null -> {
                change = null
                spokenChange = null
            }
            currentNet == 0L -> {
                spokenChange = if (previousNet != 0L) "Sin gasto · antes ${money(previous)}" else "Sin gasto en $selectedLabel"
                change = AnnotatedString(spokenChange)
            }
            // From nothing, spending is new; a refund needs no change line, as its share already says "Devolución neta".
            previousNet == 0L -> {
                spokenChange = if (currentNet > 0) "Nuevo en $selectedLabel" else null
                change = spokenChange?.let(::AnnotatedString)
            }
            else -> {
                change = changeText(current - previous, previous, currency)
                spokenChange = changeSpoken(current - previous, previous, currency, than = "que $previousLabel")
            }
        }
        val share = when {
            currentNet < 0 -> "Devolución neta"
            currentNet > 0 -> sharePercent(currentNet, netTotal)?.let { "$it% del gasto" }
            else -> null
        }
        val spoken = buildString {
            append("${trend.pocket.name}: ${current?.let { "${money(it)} al día" } ?: "sin días"}")
            spokenChange?.let { append(". $it") }
            share?.let { append(". $it") }
            if (drawLines) {
                append(". Últimos ${labels.size} periodos: ")
                append(labels.zip(values).joinToString(", ") { (label, value) -> "$label ${value?.let(::money) ?: "sin días"}" })
            }
        }
        PocketTrendRow(
            pocket = trend.pocket,
            value = current?.let(::money) ?: "—",
            lineValues = values.takeIf { drawLines },
            change = change,
            share = share,
            spokenDescription = spoken,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (quiet.isNotEmpty()) {
        val period = when {
            drawLines -> "estos periodos"
            previousLabel != null -> "$selectedLabel ni $previousLabel"
            else -> selectedLabel
        }
        Muted("Sin gasto en $period: ${quiet.joinToString(", ") { it.pocket.name }}.")
    }
}

@Composable
private fun PocketTrendRow(
    pocket: Pocket,
    value: String,
    lineValues: List<Long?>?,
    change: AnnotatedString?,
    share: String?,
    spokenDescription: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .padding(vertical = 6.dp)
            .clearAndSetSemantics { contentDescription = spokenDescription },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PocketHeading(pocket, value)
        lineValues?.let { RelativeLine(it, Modifier.fillMaxWidth().padding(start = ROW_INDENT)) }
        if (change != null || share != null) {
            FlowRow(Modifier.fillMaxWidth().padding(start = ROW_INDENT), horizontalArrangement = Arrangement.SpaceBetween) {
                change?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                share?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

/** A Pocket's plate, name and amount: the first line of every per-Pocket row. */
@Composable
private fun PocketHeading(pocket: Pocket, value: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PocketArtworkPlate(pocket.iconKey, plateSize = 32.dp)
        NameValueRow(pocket.name, value, Modifier.weight(1f))
    }
}

@Composable
private fun RecurringSection(
    charges: RecurringCharges,
    currency: SupportedCurrency,
    periodId: String,
    onOpenMovements: (MovementDrill, String) -> Unit,
) {
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
                "Esperado el ${charge.expectedDate.format(recurringDate)} · ${MoneyText.unbroken(charge.expectedAmountMinor, currency)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (charge.overdue) Text("Vencido", style = MaterialTheme.typography.labelMedium)
        }
    }
    charges.totalExpectedMinor?.takeIf { charges.items.isNotEmpty() }?.let { total ->
        Text("Faltan por cobrarse: ${MoneyText.unbroken(total, currency)}", style = MaterialTheme.typography.titleSmall)
    }
}

/** A merchant's supporting line as shown, with arrows, and as spoken, in words only. */
private class MerchantSupporting(val shown: AnnotatedString, val spoken: String)

/** "media SAR 45.83 · ▲ SAR 62.00 más que el anterior (+16%)", the change in the same words as every other one. */
@Composable
private fun merchantSupporting(merchant: MerchantSpend, currency: SupportedCurrency): MerchantSupporting {
    val shown = mutableListOf<AnnotatedString>()
    val spoken = mutableListOf<String>()
    merchant.averageExpenseMinor?.let {
        "media ${MoneyText.unbroken(it, currency)}".let { average ->
            shown += AnnotatedString(average)
            spoken += average
        }
    }
    merchant.previousNetSpendMinor?.let { previous ->
        val delta = merchant.netSpendMinor - previous
        if (previous == 0L) {
            shown += AnnotatedString("nuevo este periodo")
            spoken += "nuevo este periodo"
        } else {
            val than = "que el anterior"
            val unchanged = "igual que el periodo anterior"
            shown += changeText(delta, previous, currency, than = than, unchanged = unchanged)
            spoken += changeSpoken(delta, previous, currency, than = than, unchanged = unchanged)
        }
    }
    return MerchantSupporting(
        shown = buildAnnotatedString {
            shown.forEachIndexed { index, part ->
                if (index > 0) append(" · ")
                append(part)
            }
        },
        spoken = spoken.joinToString(" · "),
    )
}

@Composable
private fun MerchantSection(analysis: SpendingAnalysis, currency: SupportedCurrency, onOpenMovements: (MovementDrill, String) -> Unit) {
    if (analysis.merchants.isEmpty()) {
        Muted("Aún no hay movimientos con comercio en este periodo.")
        return
    }
    var showAll by rememberSaveable { mutableStateOf(false) }
    val shown = if (showAll) analysis.merchants else analysis.merchants.take(TOP_MERCHANTS)
    // Shares are of all the period's net spending, including Movements without a merchant, so they match the total.
    val total = analysis.merchants.sumOf { it.netSpendMinor } + analysis.unnamedNetSpendMinor
    val largest = analysis.merchants.maxOf { it.netSpendMinor }.coerceAtLeast(1)
    val shares = shown.map { merchant ->
        merchant.netSpendMinor.takeIf { it > 0 }?.let { sharePercent(it, total) }?.let { "$it%" } ?: "—"
    }
    val shareWidth = widestTextWidth(shares, MaterialTheme.typography.labelMedium)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        shown.forEachIndexed { index, merchant ->
            Column(
                Modifier
                    .testTag("merchant_row")
                    .opensMovements { onOpenMovements(MovementDrill.merchant(analysis.period.id, merchant.key), merchant.name) }
                    .semantics(mergeDescendants = true) {}
                    .padding(vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                NameValueRow(merchant.name, MoneyText.unbroken(merchant.netSpendMinor, currency), Modifier.fillMaxWidth())
                ValueBar(
                    label = counted(merchant.expenseCount, "compra", "compras"),
                    fraction = merchant.netSpendMinor.coerceAtLeast(0).toFloat() / largest,
                    value = shares[index],
                    labelWidth = 72,
                    valueWidth = shareWidth,
                )
                val supporting = merchantSupporting(merchant, currency)
                if (supporting.shown.isNotEmpty()) {
                    Text(
                        supporting.shown,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        // Screen readers hear the change in words instead of the arrow.
                        modifier = Modifier.clearAndSetSemantics { text = AnnotatedString(supporting.spoken) },
                    )
                }
            }
        }
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
        Muted("Sin comercio registrado: ${MoneyText.unbroken(analysis.unnamedNetSpendMinor, currency)}.")
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
        busiest?.let { "Gastas más ${it.dayOfWeek.recurringSpanish()}: ${MoneyText.unbroken(it.averagePerDayMinor!!, currency)} de media" }
            ?: "Aún no hay gasto hasta hoy.",
        style = MaterialTheme.typography.bodyLarge,
    )
    Text("Promedio por día de la semana", style = MaterialTheme.typography.titleSmall)
    val maxAverage = analysis.weekdays.maxOf { it.averagePerDayMinor ?: 0 }.coerceAtLeast(1)
    val values = analysis.weekdays.map { day -> day.averagePerDayMinor?.let { MoneyText.unbroken(it, currency) } ?: "—" }
    // One value column for every weekday, so each track is the same length and the bars compare.
    val valueWidth = widestTextWidth(values, MaterialTheme.typography.labelMedium)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        analysis.weekdays.forEachIndexed { index, day ->
            val average = day.averagePerDayMinor
            ValueBar(
                label = day.dayOfWeek.shortSpanish(),
                fraction = (average ?: 0).coerceAtLeast(0).toFloat() / maxAverage,
                value = values[index],
                valueWidth = valueWidth,
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
        Muted("No incluye ${MoneyText.unbroken(insights.netSpendAfterTodayMinor, currency)} netos con fecha posterior a hoy.")
    }
}

@Composable
private fun PaymentMethodSection(analysis: SpendingAnalysis, currency: SupportedCurrency, onOpenMovements: (MovementDrill, String) -> Unit) {
    val methods = analysis.paymentMethods
    if (methods.isEmpty()) {
        Muted("Aún no hay movimientos en este periodo.")
        return
    }
    val shares = methods.map { method -> method.sharePercent?.let { "$it%" } ?: "—" }
    val shareWidth = widestTextWidth(shares, MaterialTheme.typography.labelMedium)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        methods.forEachIndexed { index, method ->
            val label = method.name ?: "Sin método"
            Column(
                Modifier
                    .opensMovements { onOpenMovements(MovementDrill.paymentMethod(analysis.period.id, method.id), label) }
                    .semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                NameValueRow(label, MoneyText.unbroken(method.netSpendMinor, currency), Modifier.fillMaxWidth())
                ValueBar(
                    label = counted(method.movementCount, "mov.", "mov."),
                    fraction = (method.sharePercent ?: 0) / 100f,
                    value = shares[index],
                    labelWidth = 56,
                    valueWidth = shareWidth,
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
            value = MoneyText.unbroken(foreign.accountingNetMinor, currency),
            supporting = buildString {
                append("Original ${MoneyText.unbroken(foreign.originalNetMinor, foreign.currency)}")
                append(" · ${counted(foreign.movementCount, "movimiento", "movimientos")}")
                if (foreign.estimatedCount > 0) {
                    append(
                        " · ${counted(foreign.estimatedCount, "estimado", "estimados")} " +
                            "(${MoneyText.unbroken(foreign.estimatedAccountingNetMinor, currency)}) hasta el tipo final",
                    )
                }
            },
        )
    }
}

/**
 * Each Pocket's rollover as one bar on a shared scale: what it held when the period began, what it added since, or the
 * part it used. The amount is what it carries into the next period, or would if the period ended today.
 */
@Composable
private fun RolloverSection(analysis: SpendingAnalysis, currency: SupportedCurrency) {
    fun money(minor: Long) = MoneyText.unbroken(minor, currency)
    val pockets = analysis.rollover
    val scale = pockets.maxOfOrNull { maxOf(it.rolloverByPeriodMinor.last(), it.carriesForwardMinor) }?.coerceAtLeast(1) ?: 1
    RolloverLegend()
    pockets.forEach { pocket ->
        val start = pocket.rolloverByPeriodMinor.last().coerceAtLeast(0)
        val now = pocket.carriesForwardMinor
        val (shown, spoken) = when {
            now > start -> "${money(start)} al inicio + ${money(now - start)} añadido" to
                "${money(start)} al inicio, ${money(now - start)} añadido"
            now < start -> "${money(start)} al inicio − ${money(start - now)} usado" to
                "${money(start)} al inicio, ${money(start - now)} usado"
            else -> "${money(start)} al inicio, sin cambios".let { it to it }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clearAndSetSemantics { contentDescription = "${pocket.pocket.name}: ${money(now)}. $spoken" },
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PocketHeading(pocket.pocket, money(now))
            RolloverBar(startMinor = start, nowMinor = now, scaleMinor = scale, modifier = Modifier.fillMaxWidth())
            Muted(shown)
        }
    }
    Muted(
        if (analysis.insights.inProgress) {
            "La cifra es lo que pasaría al siguiente periodo si terminara hoy."
        } else {
            "La cifra es lo que pasó al siguiente periodo."
        },
    )
}

/**
 * Reorders the Análisis sections. Each row moves with its arrow buttons, which screen readers and keyboards reach,
 * or by dragging its handle. Every change is applied at once, so closing the sheet in any way keeps it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SectionOrderSheet(
    order: List<AnalysisSection>,
    onOrderChange: (List<AnalysisSection>) -> Unit,
    onDismiss: () -> Unit,
) {
    // The sheet keeps its own copy, so a drag is not held back by the round trip through saved preferences.
    var current by remember { mutableStateOf(order) }
    fun change(newOrder: List<AnalysisSection>) {
        current = newOrder
        onOrderChange(newOrder)
    }
    var dragged by remember { mutableStateOf<AnalysisSection?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<AnalysisSection, Int>() }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .testTag("section_order_sheet"),
        ) {
            Text(
                "Organizar secciones",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 8.dp).semantics { heading() },
            )
            Muted(
                "Arrastra o usa las flechas. El orden se guarda en este teléfono.",
                Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
            )
            current.forEachIndexed { index, section ->
                key(section) {
                    val isDragged = dragged == section
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .onSizeChanged { heights[section] = it.height }
                            .zIndex(if (isDragged) 1f else 0f)
                            .graphicsLayer { translationY = if (isDragged) dragOffset else 0f },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.DragHandle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(48.dp)
                                .clearAndSetSemantics { }
                                .pointerInput(section) {
                                    detectDragGestures(
                                        onDragStart = {
                                            dragged = section
                                            dragOffset = 0f
                                        },
                                        onDragEnd = {
                                            dragged = null
                                            dragOffset = 0f
                                        },
                                        onDragCancel = {
                                            dragged = null
                                            dragOffset = 0f
                                        },
                                    ) { pointer, amount ->
                                        pointer.consume()
                                        dragOffset += amount.y
                                        val from = current.indexOf(section)
                                        val step = if (dragOffset > 0) 1 else -1
                                        val neighbour = current.getOrNull(from + step) ?: return@detectDragGestures
                                        val neighbourHeight = heights[neighbour] ?: return@detectDragGestures
                                        // Swap once the row has crossed half of its neighbour, keeping it under the finger.
                                        if (abs(dragOffset) > neighbourHeight / 2f) {
                                            change(current.moved(section, step))
                                            dragOffset -= step * neighbourHeight
                                        }
                                    }
                                }
                                .padding(12.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(section.title, style = MaterialTheme.typography.bodyLarge)
                            section.availability?.let { Muted(it) }
                        }
                        IconButton(onClick = { change(current.moved(section, -1)) }, enabled = index > 0) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Subir ${section.title}")
                        }
                        IconButton(onClick = { change(current.moved(section, 1)) }, enabled = index < current.lastIndex) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Bajar ${section.title}")
                        }
                    }
                }
                if (index < current.lastIndex) HorizontalDivider()
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { change(AnalysisSection.entries) }, enabled = current != AnalysisSection.entries) {
                    Text("Restablecer")
                }
                Button(onClick = onDismiss) { Text("Listo") }
            }
        }
    }
}
