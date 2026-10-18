package com.aif31.pocket

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aif31.pocket.data.ConversionStatus
import com.aif31.pocket.data.LedgerState
import com.aif31.pocket.data.Movement
import com.aif31.pocket.data.MovementDrill
import com.aif31.pocket.data.MovementType
import com.aif31.pocket.data.Period
import com.aif31.pocket.data.Pocket
import com.aif31.pocket.data.PocketIconKey
import com.aif31.pocket.data.PocketPeriodSummary
import com.aif31.pocket.domain.SupportedCurrency
import com.aif31.pocket.ui.PocketTheme
import com.aif31.pocket.ui.TrendBar
import com.aif31.pocket.ui.TrendBars
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AnalysisScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val february = Period("p1", LocalDate.of(2026, 1, 25), LocalDate.of(2026, 2, 25), 30_000, 25)
    private val march = Period("p2", LocalDate.of(2026, 2, 25), LocalDate.of(2026, 3, 25), 30_000, 25)
    private val market = Pocket("market", "Supermercado", PocketIconKey.forName("Supermercado"), 0, false, false)

    @Test
    fun the_period_breakdowns_report_merchants_payment_methods_foreign_currency_and_the_trend() {
        // February: SAR 31.00 over 31 days. March, day 9: SAR 90.00 across three Movements.
        val state = LedgerState(
            periods = listOf(february, march),
            currentPeriod = march,
            pocketCatalog = listOf(market),
            pocketSummariesByPeriod = mapOf(february.id to listOf(spend(3_100)), march.id to listOf(spend(9_000))),
            unallocatedMinorByPeriod = mapOf(february.id to 0L, march.id to 0L),
            movements = listOf(
                movement("feb", february, LocalDate.of(2026, 2, 1), 3_100, merchant = "Tamimi"),
                movement("a", march, LocalDate.of(2026, 2, 28), 5_000, merchant = "Tamimi", method = "card" to "Tarjeta"),
                movement("b", march, LocalDate.of(2026, 3, 1), 1_000, merchant = "Jarir"),
                movement("c", march, LocalDate.of(2026, 3, 2), 3_000, merchant = "OXXO", original = 15_000L to SupportedCurrency.MXN),
            ),
            elapsedDays = 9,
            totalDays = 28,
            currentLocalDate = LocalDate.of(2026, 3, 5),
        )
        var selected: String? = null
        val opened = mutableListOf<Pair<MovementDrill, String>>()
        compose.setContent {
            PocketTheme {
                AnalysisScreen(state, march.id, onPeriodChange = { selected = it }, onBack = {}, onOpenMovements = { drill, title -> opened += drill to title })
            }
        }
        val list = compose.onNodeWithTag("analysis_list")

        list.performScrollToNode(hasText("SAR 10.00 al día en el periodo elegido"))
        list.performScrollToNode(
            hasContentDescription("Gasto diario promedio por periodo. 25 ene – 24 feb 2026: SAR 1.00. 25 feb – 24 mar 2026: SAR 10.00, en curso"),
        )
        list.performScrollToNode(hasText("1 compra · media SAR 50.00 · ▲\u00A0SAR 19.00 frente al anterior"))
        list.performScrollToNode(hasText("Gastas más los sábados: SAR 50.00 de media"))
        list.performScrollToNode(hasText("Tarjeta"))
        list.performScrollToNode(hasText("33% del gasto neto se registró en otra moneda"))
        list.performScrollToNode(hasText("Original MXN 150.00 · 1 movimiento"))

        list.performScrollToNode(hasContentDescription("Supermercado: promedio diario por periodo, SAR 1.00, SAR 10.00", substring = true))
        list.performScrollToNode(hasTestTag("trend_bars"))
        compose.onNodeWithTag("trend_bars").performTouchInput { click(Offset(width * 0.25f, height - 1f)) }
        assertEquals(february.id, selected)

        list.performScrollToNode(hasText("Jarir"))
        compose.onNodeWithText("Jarir").performClick()
        list.performScrollToNode(hasContentDescription("28: SAR 50.00"))
        compose.onNodeWithContentDescription("28: SAR 50.00").performClick()
        assertEquals(
            listOf(
                MovementDrill.merchant(march.id, "jarir") to "Jarir",
                MovementDrill.day(march.id, LocalDate.of(2026, 2, 28)) to "Sábado 28 feb",
            ),
            opened,
        )
    }

    @Test
    fun trend_bars_select_with_the_current_mapping_and_offer_a_screen_reader_action_per_bar() {
        var window by mutableStateOf(listOf("p1", "p2"))
        var selected: String? = null
        compose.setContent {
            val shown = window
            PocketTheme {
                TrendBars(
                    bars = shown.map { TrendBar(it, 100, selected = false) },
                    description = "Tendencia",
                    onSelect = { selected = shown[it] },
                )
            }
        }

        // Same number of bars, different periods: a tap must use the new mapping.
        window = listOf("p0", "p1")
        compose.waitForIdle()
        compose.onNodeWithTag("trend_bars").performTouchInput { click(Offset(width * 0.25f, height - 1f)) }
        assertEquals("p0", selected)

        val actions = compose.onNodeWithTag("trend_bars").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(listOf("Ver periodo p0", "Ver periodo p1"), actions.map { it.label })
        actions[1].action()
        assertEquals("p1", selected)
    }

    private fun spend(expense: Long) = PocketPeriodSummary(
        pocket = market,
        budgetMinor = 30_000,
        rolloverMinor = 0,
        expenseMinor = expense,
        refundMinor = 0,
        netSpendMinor = expense,
        availabilityMinor = 30_000 - expense,
        consumedPercent = (expense * 100 / 30_000).toInt(),
        atRisk = false,
        exhausted = false,
    )

    private fun movement(
        id: String,
        period: Period,
        date: LocalDate,
        amount: Long,
        merchant: String,
        method: Pair<String, String>? = null,
        original: Pair<Long, SupportedCurrency>? = null,
    ) = Movement(
        id = id, pocketId = market.id, pocketName = market.name, periodId = period.id, type = MovementType.EXPENSE,
        accountingAmountMinor = amount, occurredAtUtcMillis = 0, localDate = date, zoneId = "Asia/Riyadh",
        merchant = merchant, note = null, paymentMethodId = method?.first, paymentMethodName = method?.second,
        originalAmountMinor = original?.first, originalCurrencyCode = (original?.second ?: period.accountingCurrency).name,
        conversionStatus = ConversionStatus.CONFIRMED, rate = null, conversionEffectiveDate = null, conversionSource = null,
    )
}
