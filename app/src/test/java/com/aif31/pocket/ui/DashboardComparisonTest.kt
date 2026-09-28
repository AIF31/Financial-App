package com.aif31.pocket.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aif31.pocket.data.LedgerState
import com.aif31.pocket.data.Period
import com.aif31.pocket.data.Pocket
import com.aif31.pocket.data.PocketIconKey
import com.aif31.pocket.data.PocketPeriodSummary
import com.aif31.pocket.domain.SupportedCurrency
import java.time.LocalDate
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
