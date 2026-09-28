package com.aif31.pocket

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aif31.pocket.data.LedgerState
import com.aif31.pocket.data.Period
import com.aif31.pocket.data.Pocket
import com.aif31.pocket.data.PocketIconKey
import com.aif31.pocket.data.PocketPeriodSummary
import com.aif31.pocket.domain.SupportedCurrency
import com.aif31.pocket.ui.PocketTheme
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ComparisonScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val february = Period("p1", LocalDate.of(2026, 1, 25), LocalDate.of(2026, 2, 25), 30_000, 25)
    private val march = Period("p2", LocalDate.of(2026, 2, 25), LocalDate.of(2026, 3, 25), 30_000, 25)
    private val april = Period("p3", LocalDate.of(2026, 3, 25), LocalDate.of(2026, 4, 25), 30_000, 25, accountingCurrency = SupportedCurrency.MXN)
    private val market = Pocket("market", "Supermercado", PocketIconKey.forName("Supermercado"), 0, false, false)

    @Test
    fun periods_without_a_shared_rate_still_show_the_compared_daily_pace_in_its_own_currency() {
        // February (SAR) and April (MXN) are not adjacent, so no frozen boundary rate converts between them.
        // February: SAR 31.00 over 31 days. April: MXN 20.00 after 10 of its days.
        val state = LedgerState(
            periods = listOf(february, march, april),
            currentPeriod = april,
            pocketSummariesByPeriod = mapOf(
                february.id to listOf(spend(3_100)),
                march.id to emptyList(),
                april.id to listOf(spend(2_000)),
            ),
            unallocatedMinorByPeriod = mapOf(february.id to 0L, march.id to 0L, april.id to 0L),
            movements = emptyList(),
            elapsedDays = 10,
            totalDays = 30,
            currentLocalDate = LocalDate.of(2026, 4, 3),
        )
        compose.setContent {
            PocketTheme { ComparisonScreen(state, april.id, february.id, onPeriodsChange = { _, _ -> }, onBack = {}) }
        }

        compose.onNodeWithTag("comparison_list").performScrollToNode(hasText("Periodo comparado: SAR 1.00 al día"))
        compose.onNodeWithTag("comparison_list").performScrollToNode(
            hasContentDescription("Gasto diario promedio. 25 mar – 24 abr 2026: MXN 2.00. 25 ene – 24 feb 2026: SAR 1.00"),
        )
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
