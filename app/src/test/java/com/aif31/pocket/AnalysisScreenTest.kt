package com.aif31.pocket

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
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
import org.junit.Assert.assertTrue
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

        list.performScrollToNode(hasText("SAR\u00A010.00 al día"))
        list.performScrollToNode(
            hasContentDescription("Gasto diario promedio por periodo. 25 ene – 24 feb 2026: SAR 1.00. 25 feb – 24 mar 2026: SAR 10.00, en curso"),
        )
        // The change is spoken in words; the arrow is only shown.
        list.performScrollToNode(hasText("media SAR\u00A050.00 · SAR\u00A019.00 más que el anterior, un 61% más"))
        list.performScrollToNode(hasText("Gastas más los sábados: SAR\u00A050.00 de media"))
        list.performScrollToNode(hasText("Tarjeta"))
        list.performScrollToNode(hasText("33% del gasto neto se registró en otra moneda"))
        list.performScrollToNode(hasText("Original MXN\u00A0150.00 · 1 movimiento"))

        list.performScrollToNode(
            hasContentDescription("Supermercado: SAR\u00A010.00 al día. SAR\u00A09.00 más que feb, un 900% más. 100% del gasto"),
        )
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

    @Test
    fun from_three_periods_each_pocket_speaks_its_values_per_period() {
        val january = Period("p0", LocalDate.of(2025, 12, 25), LocalDate.of(2026, 1, 25), 30_000, 25)
        val state = threePeriodState(january)
        compose.setContent { PocketTheme { AnalysisScreen(state, march.id, onPeriodChange = {}, onBack = {}) } }

        compose.onNodeWithTag("analysis_list").performScrollToNode(
            hasContentDescription("Últimos 3 periodos: ene SAR\u00A02.00, feb SAR\u00A01.00, mar SAR\u00A010.00", substring = true),
        )
    }

    @Test
    @Config(qualifiers = "w411dp-h1400dp")
    fun sections_follow_the_chosen_order_and_the_sheet_reports_each_move() {
        val january = Period("p0", LocalDate.of(2025, 12, 25), LocalDate.of(2026, 1, 25), 30_000, 25)
        val state = threePeriodState(january)
        val chosen = listOf(AnalysisSection.MERCHANTS) + AnalysisSection.entries.filterNot { it == AnalysisSection.MERCHANTS }
        val changes = mutableListOf<List<AnalysisSection>>()
        compose.setContent {
            PocketTheme {
                AnalysisScreen(
                    state,
                    march.id,
                    onPeriodChange = {},
                    onBack = {},
                    sectionOrder = chosen,
                    onSectionOrderChange = { changes += it },
                )
            }
        }

        val merchantsTop = compose.onNodeWithText("Comercios principales").fetchSemanticsNode().boundsInRoot.top
        val trendTop = compose.onNodeWithText("Tendencia del gasto diario").fetchSemanticsNode().boundsInRoot.top
        assertTrue("Merchants come first when chosen first", merchantsTop < trendTop)

        compose.onNodeWithContentDescription("Organizar secciones").performClick()
        compose.onNodeWithTag("section_order_sheet").assertIsDisplayed()
        compose.onNodeWithContentDescription("Subir Comercios principales").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Bajar Comercios principales").performClick()
        compose.onNodeWithText("Restablecer").performClick()

        assertEquals(
            listOf(
                listOf(AnalysisSection.TREND, AnalysisSection.MERCHANTS) + chosen.drop(2),
                AnalysisSection.entries,
            ),
            changes,
        )
    }

    @Test
    fun a_refund_pocket_keeps_its_negative_amount_and_a_pocket_without_movements_is_named_once() {
        val gifts = Pocket("gifts", "Regalos", PocketIconKey.forName("Regalos"), 1, false, false)
        val travel = Pocket("travel", "Viajes", PocketIconKey.forName("Viajes"), 2, false, false)
        val leisure = Pocket("leisure", "Ocio", PocketIconKey.forName("Ocio"), 3, false, false)
        val state = LedgerState(
            periods = listOf(february, march),
            currentPeriod = march,
            pocketCatalog = listOf(market, gifts, travel, leisure),
            pocketSummariesByPeriod = mapOf(
                february.id to listOf(spend(3_100)),
                march.id to listOf(spend(9_000), spend(-900, gifts), spend(0, travel), spend(-1, leisure)),
            ),
            unallocatedMinorByPeriod = mapOf(february.id to 0L, march.id to 0L),
            movements = listOf(
                movement("feb", february, LocalDate.of(2026, 2, 1), 3_100, merchant = "Tamimi"),
                movement("a", march, LocalDate.of(2026, 2, 28), 9_000, merchant = "Tamimi"),
                movement("r", march, LocalDate.of(2026, 3, 1), 900, merchant = "Jarir", pocket = gifts, type = MovementType.REFUND),
                // One cent refunded over nine days averages to zero a day, but it is still movement, not "no spending".
                movement("c", march, LocalDate.of(2026, 3, 1), 1, merchant = "Jarir", pocket = leisure, type = MovementType.REFUND),
            ),
            elapsedDays = 9,
            totalDays = 28,
            currentLocalDate = LocalDate.of(2026, 3, 5),
        )
        compose.setContent { PocketTheme { AnalysisScreen(state, march.id, onPeriodChange = {}, onBack = {}) } }
        val list = compose.onNodeWithTag("analysis_list")

        // March nets 8,099 minor units (9,000 spent, 900 and 1 refunded), so Supermercado's 9,000 is 111% of it.
        list.performScrollToNode(hasContentDescription("Supermercado: SAR\u00A010.00 al día", substring = true))
        compose.onNode(hasContentDescription("111% del gasto", substring = true)).assertExists()
        list.performScrollToNode(hasContentDescription("Ocio: SAR\u00A00.00 al día", substring = true))
        compose.onNode(hasContentDescription("Ocio: SAR\u00A00.00 al día", substring = true))
            .assert(hasContentDescription("Ocio: SAR\u00A00.00 al día. Devolución neta"))
        list.performScrollToNode(hasContentDescription("Regalos: SAR\u00A0-1.00 al día", substring = true))
        compose.onNode(hasContentDescription("Regalos: SAR\u00A0-1.00 al día", substring = true))
            .assert(hasContentDescription("Devolución neta", substring = true))
        list.performScrollToNode(hasText("Sin gasto en mar ni feb: Viajes."))
    }

    private fun threePeriodState(january: Period) = LedgerState(
        periods = listOf(january, february, march),
        currentPeriod = march,
        pocketCatalog = listOf(market),
        pocketSummariesByPeriod = mapOf(
            january.id to listOf(spend(6_200)),
            february.id to listOf(spend(3_100)),
            march.id to listOf(spend(9_000)),
        ),
        unallocatedMinorByPeriod = mapOf(january.id to 0L, february.id to 0L, march.id to 0L),
        movements = listOf(
            movement("jan", january, LocalDate.of(2026, 1, 2), 6_200, merchant = "Tamimi"),
            movement("feb", february, LocalDate.of(2026, 2, 1), 3_100, merchant = "Tamimi"),
            movement("a", march, LocalDate.of(2026, 2, 28), 9_000, merchant = "Tamimi"),
        ),
        elapsedDays = 9,
        totalDays = 28,
        currentLocalDate = LocalDate.of(2026, 3, 5),
    )

    private fun spend(expense: Long, pocket: Pocket = market) = PocketPeriodSummary(
        pocket = pocket,
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
        pocket: Pocket = market,
        type: MovementType = MovementType.EXPENSE,
    ) = Movement(
        id = id, pocketId = pocket.id, pocketName = pocket.name, periodId = period.id, type = type,
        accountingAmountMinor = amount, occurredAtUtcMillis = 0, localDate = date, zoneId = "Asia/Riyadh",
        merchant = merchant, note = null, paymentMethodId = method?.first, paymentMethodName = method?.second,
        originalAmountMinor = original?.first, originalCurrencyCode = (original?.second ?: period.accountingCurrency).name,
        conversionStatus = ConversionStatus.CONFIRMED, rate = null, conversionEffectiveDate = null, conversionSource = null,
    )
}
