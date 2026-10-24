package com.aif31.pocket

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.aif31.pocket.data.ConversionStatus
import com.aif31.pocket.data.LedgerState
import com.aif31.pocket.data.Movement
import com.aif31.pocket.data.MovementType
import com.aif31.pocket.data.Period
import com.aif31.pocket.data.Pocket
import com.aif31.pocket.data.PocketIconKey
import com.aif31.pocket.data.PocketPeriodSummary
import com.aif31.pocket.ui.PocketTheme
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Renders the Análisis screen on a device from eight periods of in-memory data, saves a screenshot of each section to
 * the app's external files directory for visual review, and reorders sections through the sheet. It never opens the
 * app's database or preferences. Excluded from the regular device suite as a [LargeTest].
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class AnalysisDesignDeviceTest {
    @get:Rule val compose = createComposeRule()

    private val shots by lazy {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "analysis-design").apply { deleteRecursively(); mkdirs() }
    }

    @Test
    fun eight_periods_render_every_section_and_the_sections_reorder() {
        val state = eightPeriodState()
        var order by mutableStateOf<List<AnalysisSection>>(AnalysisSection.entries)
        compose.setContent {
            PocketTheme {
                AnalysisScreen(
                    state = state,
                    periodId = state.currentPeriod!!.id,
                    onPeriodChange = {},
                    onBack = {},
                    sectionOrder = order,
                    onSectionOrderChange = { order = it },
                )
            }
        }
        val list = compose.onNodeWithTag("analysis_list")

        // Items: 0 period picker, then one per section in order.
        list.performScrollToIndex(1)
        compose.onNodeWithText("Tendencia del gasto diario").assertIsDisplayed()
        capture("01-trend")
        list.performScrollToIndex(2)
        capture("02-pockets")
        list.performScrollToNode(
            hasContentDescription("Últimos 8 periodos: mar SAR 38.20", substring = true),
        )
        capture("03-pockets-lower")
        list.performScrollToIndex(4)
        capture("04-merchants")
        list.performScrollToIndex(5)
        capture("05-when")
        list.performScrollToIndex(6)
        capture("06-methods")
        list.performScrollToIndex(AnalysisSection.entries.size)
        capture("07-rollover")

        compose.onNodeWithContentDescription("Organizar secciones").performClick()
        compose.onNodeWithTag("section_order_sheet").assertIsDisplayed()
        capture("08-sheet")
        compose.onNodeWithContentDescription("Bajar Tendencia del gasto diario").performClick()
        capture("09-sheet-moved")
        compose.onNodeWithText("Listo").performClick()
        compose.waitForIdle()
        assertEquals(AnalysisSection.POCKETS, order.first())
        list.performScrollToIndex(0)
        capture("10-reordered")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        // Let the sheet's and list's motion settle before the frame is taken.
        Thread.sleep(600)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(shots, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Eight monthly periods from 25 February to 24 October 2026, labeled mar to oct, today being 23 October. */
    private fun eightPeriodState(): LedgerState {
        val starts = (0..8).map { LocalDate.of(2026, 2, 25).plusMonths(it.toLong()) }
        val periods = (0 until 8).map { Period("p$it", starts[it], starts[it + 1], 300_000, 25) }
        val current = periods.last()
        val today = LocalDate.of(2026, 10, 23)
        val elapsed = ChronoUnit.DAYS.between(current.start, today).toInt() + 1
        fun daysOf(period: Period) = if (period == current) elapsed else ChronoUnit.DAYS.between(period.start, period.endExclusive).toInt()

        val movements = mutableListOf<Movement>()
        val summaries = periods.associate { period ->
            period.id to POCKETS.map { spec ->
                val spend = (spec.dailySar[periods.indexOf(period)] * 100 * daysOf(period)).roundToLong()
                if (spend > 0) movements += movement(spec, period, spend)
                val rollover = if (period == current) ROLLOVER[spec.name] else null
                PocketPeriodSummary(
                    pocket = spec.pocket,
                    budgetMinor = 60_000,
                    rolloverMinor = rollover?.first ?: 0,
                    rolloverEligible = rollover != null,
                    expenseMinor = spend,
                    netSpendMinor = spend,
                    availabilityMinor = rollover?.second ?: (60_000 - spend),
                    consumedPercent = (spend * 100 / 60_000).toInt(),
                    atRisk = false,
                    exhausted = false,
                )
            }
        }
        return LedgerState(
            periods = periods,
            currentPeriod = current,
            pocketCatalog = POCKETS.map { it.pocket },
            pocketSummariesByPeriod = summaries,
            unallocatedMinorByPeriod = periods.associate { it.id to 0L },
            movements = movements,
            elapsedDays = elapsed,
            totalDays = ChronoUnit.DAYS.between(current.start, current.endExclusive).toInt(),
            currentLocalDate = today,
        )
    }

    private fun movement(spec: PocketSpec, period: Period, amount: Long) = Movement(
        id = "${spec.pocket.id}-${period.id}", pocketId = spec.pocket.id, pocketName = spec.name, periodId = period.id,
        type = MovementType.EXPENSE, accountingAmountMinor = amount, occurredAtUtcMillis = 0,
        localDate = period.start.plusDays(1), zoneId = "Asia/Riyadh", merchant = spec.merchant, note = null,
        paymentMethodId = spec.method.first, paymentMethodName = spec.method.second, originalAmountMinor = null,
        originalCurrencyCode = "SAR", conversionStatus = ConversionStatus.CONFIRMED, rate = null,
        conversionEffectiveDate = null, conversionSource = null,
    )

    private class PocketSpec(val name: String, val merchant: String?, val method: Pair<String, String>, val dailySar: List<Double>) {
        val pocket = Pocket(name.lowercase().filter(Char::isLetter), name, PocketIconKey.forName(name), 0, false, name in ROLLOVER)
    }

    private companion object {
        val CARD = "card" to "Tarjeta"
        val CASH = "cash" to "Efectivo"
        val ROLLOVER = mapOf("Viajes" to (30_000L to 41_000L), "Restaurantes/café" to (8_000L to 12_000L), "Supermercado" to (15_000L to 9_550L))
        val POCKETS = listOf(
            PocketSpec("Restaurantes/café", "Starbucks", CARD, listOf(38.2, 52.4, 40.1, 61.0, 44.8, 39.9, 41.10, 47.27)),
            PocketSpec("Supermercado", "Panda", CARD, listOf(30.1, 28.7, 27.9, 31.4, 29.0, 27.5, 26.40, 21.13)),
            PocketSpec("E-commerce", "Amazon.sa", CARD, listOf(9.8, 22.1, 11.4, 25.6, 15.2, 14.0, 12.80, 19.24)),
            PocketSpec("Universidad", "Jarir", CASH, listOf(4.9, 5.0, 5.1, 5.0, 5.2, 5.1, 5.10, 5.72)),
            PocketSpec("Viajes", null, CARD, listOf(0.0, 0.0, 0.0, 24.3, 0.0, 0.0, 18.30, 2.75)),
            PocketSpec("Salud", null, CASH, listOf(6.2, 3.1, 8.4, 2.2, 4.9, 6.0, 9.90, 2.58)),
            PocketSpec("Ocio", null, CASH, listOf(1.2, 0.9, 1.4, 0.8, 0.7, 0.6, 0.55, 0.69)),
            PocketSpec("Transporte", null, CARD, listOf(12.4, 13.0, 11.9, 13.6, 12.8, 13.1, 14.85, 0.0)),
        )
    }
}
