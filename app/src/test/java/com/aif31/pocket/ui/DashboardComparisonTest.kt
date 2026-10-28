package com.aif31.pocket.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aif31.pocket.data.ConversionStatus
import com.aif31.pocket.data.LedgerState
import com.aif31.pocket.data.Movement
import com.aif31.pocket.data.MovementType
import com.aif31.pocket.data.Period
import com.aif31.pocket.data.Pocket
import com.aif31.pocket.data.PocketIconKey
import com.aif31.pocket.data.PocketPeriodSummary
import com.aif31.pocket.domain.SupportedCurrency
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class DashboardComparisonTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val february = Period("p1", LocalDate.of(2026, 1, 25), LocalDate.of(2026, 2, 25), 30_000, 25)
    private val march = Period("p2", LocalDate.of(2026, 2, 25), LocalDate.of(2026, 3, 25), 30_000, 25, accountingCurrency = SupportedCurrency.MXN)
    private val market = Pocket("market", "Supermercado", PocketIconKey.forName("Supermercado"), 0, false, false)

    @Test
    fun previous_period_in_another_currency_without_a_frozen_rate_still_shows_its_daily_average() {
        // March switched to MXN with no boundary rate recorded, so February (SAR) cannot be converted.
        // February: SAR 31.00 over 31 days.
        val state = LedgerState(
            periods = listOf(february, march),
            currentPeriod = march,
            pocketSummariesByPeriod = mapOf(february.id to listOf(spend(3_100)), march.id to listOf(spend(1_000))),
            unallocatedMinorByPeriod = mapOf(february.id to 0L, march.id to 0L),
            movements = emptyList(),
            elapsedDays = 5,
            totalDays = 28,
            currentLocalDate = LocalDate.of(2026, 3, 1),
        )
        compose.setContent {
            PocketTheme {
                ActionableDashboardContent(
                    state = state,
                    contentPadding = PaddingValues(),
                    onManagePockets = {},
                    onRecordExpenseIn = {},
                    onComparePeriods = {},
                )
            }
        }

        compose.onNodeWithContentDescription("Mostrar métricas del periodo").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag("dashboard_list").performScrollToNode(hasText("Promedio diario del periodo anterior"))
        compose.onNodeWithTag("dashboard_list").performScrollToNode(hasText("SAR 1.00"))
    }

    @Test
    fun a_pocket_at_risk_says_when_it_runs_out_and_the_analysis_opens_from_the_dashboard() {
        // Day 9 of a SAR period: SAR 850.00 of SAR 1,000.00 spent on two days, so SAR 150.00 lasts until 7 March.
        val sarMarch = march.copy(accountingCurrency = SupportedCurrency.SAR)
        val summary = spend(85_000).copy(budgetMinor = 100_000, availabilityMinor = 15_000, consumedPercent = 85, atRisk = true)
        val state = LedgerState(
            periods = listOf(sarMarch),
            currentPeriod = sarMarch,
            pockets = listOf(summary),
            pocketSummariesByPeriod = mapOf(sarMarch.id to listOf(summary)),
            unallocatedMinorByPeriod = mapOf(sarMarch.id to 0L),
            movements = listOf(expense("a", sarMarch, LocalDate.of(2026, 2, 26), 42_500), expense("b", sarMarch, LocalDate.of(2026, 3, 3), 42_500)),
            elapsedDays = 9,
            totalDays = 28,
            currentLocalDate = LocalDate.of(2026, 3, 5),
        )
        var analysisOpened = false
        compose.setContent {
            PocketTheme {
                ActionableDashboardContent(
                    state = state,
                    contentPadding = PaddingValues(),
                    onManagePockets = {},
                    onOpenAnalysis = { analysisOpened = true },
                )
            }
        }

        compose.onNodeWithTag("dashboard_list").performScrollToNode(
            hasContentDescription("Al ritmo actual, se agota el 7 mar", substring = true)
                .and(hasContentDescription("En riesgo", substring = true)),
        )
        compose.onNodeWithTag("dashboard_list").performScrollToNode(hasTestTag("open_analysis"))
        compose.onNodeWithTag("open_analysis").performClick()
        assertTrue(analysisOpened)
    }

    @Test
    fun a_small_budget_spending_fast_shows_no_status_or_run_out_date_before_the_middle_of_the_period() {
        // Day 9 of a SAR period: SAR 127.20 of SAR 159.00 spent on two days would run out on 8 March.
        val sarMarch = march.copy(accountingCurrency = SupportedCurrency.SAR)
        val summary = spend(12_720).copy(budgetMinor = 15_900, availabilityMinor = 3_180, consumedPercent = 80, atRisk = true)
        val state = LedgerState(
            periods = listOf(sarMarch),
            currentPeriod = sarMarch,
            pockets = listOf(summary),
            pocketSummariesByPeriod = mapOf(sarMarch.id to listOf(summary)),
            unallocatedMinorByPeriod = mapOf(sarMarch.id to 0L),
            movements = listOf(expense("a", sarMarch, LocalDate.of(2026, 2, 26), 6_000), expense("b", sarMarch, LocalDate.of(2026, 3, 3), 6_720)),
            elapsedDays = 9,
            totalDays = 28,
            currentLocalDate = LocalDate.of(2026, 3, 5),
        )
        compose.setContent {
            PocketTheme {
                ActionableDashboardContent(state = state, contentPadding = PaddingValues(), onManagePockets = {}, onOpenAnalysis = {})
            }
        }

        compose.onNodeWithTag("dashboard_list").performScrollToNode(hasTestTag("pocket_row_${market.name}"))
        compose.onNodeWithTag("pocket_row_${market.name}")
            .assert(hasContentDescription("80% consumido · Presupuesto SAR 159.00", substring = true))
            .assert(hasContentDescription("se agota", substring = true).not())
            .assert(hasContentDescription("En riesgo", substring = true).not())
            .assert(hasContentDescription("ritmo", substring = true, ignoreCase = true).not())
        compose.onNodeWithTag("run_out_${market.name}").assertDoesNotExist()
    }

    private fun expense(id: String, period: Period, date: LocalDate, amount: Long) = Movement(
        id = id, pocketId = market.id, pocketName = market.name, periodId = period.id, type = MovementType.EXPENSE,
        accountingAmountMinor = amount, occurredAtUtcMillis = 0, localDate = date, zoneId = "Asia/Riyadh",
        merchant = null, note = null, paymentMethodId = null, paymentMethodName = null,
        originalAmountMinor = null, originalCurrencyCode = period.accountingCurrency.name,
        conversionStatus = ConversionStatus.CONFIRMED, rate = null, conversionEffectiveDate = null, conversionSource = null,
    )

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
}
