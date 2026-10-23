package com.aif31.pocket.notifications

import com.aif31.pocket.data.ConversionStatus
import com.aif31.pocket.data.LedgerState
import com.aif31.pocket.data.Movement
import com.aif31.pocket.data.MovementType
import com.aif31.pocket.data.PeriodInsights
import com.aif31.pocket.data.Period
import com.aif31.pocket.data.Pocket
import com.aif31.pocket.data.PocketIconKey
import com.aif31.pocket.data.PocketPeriodSummary
import com.aif31.pocket.domain.SupportedCurrency
import com.aif31.pocket.settings.AppPreferences
import com.aif31.pocket.settings.PreferencesStore
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaceAlertsTest {
    private val february = Period("p1", LocalDate.of(2026, 1, 25), LocalDate.of(2026, 2, 25), 30_000, 25)
    private val march = Period("p2", LocalDate.of(2026, 2, 25), LocalDate.of(2026, 3, 25), 30_000, 25)
    private val market = pocket("market", "Supermercado")
    private val food = pocket("food", "Restaurantes")
    private val gifts = pocket("gifts", "Regalos")
    private val travel = pocket("travel", "Viajes")

    @Test
    fun each_pocket_reports_its_highest_level() {
        val insights = insights(
            summary(market, budget = 10_000, expense = 8_500).copy(atRisk = true),
            summary(food, budget = 10_000, expense = 10_500).copy(exhausted = true),
            summary(gifts, budget = 10_000, expense = 6_000),
            summary(travel, budget = 10_000, expense = 100),
            movements = runOutMovements(gifts),
        )

        assertEquals(
            mapOf(market.id to PaceAlertLevel.AT_RISK, food.id to PaceAlertLevel.EXHAUSTED, gifts.id to PaceAlertLevel.RUN_OUT),
            PaceAlerts.levels(insights),
        )
    }

    @Test
    fun a_pocket_funded_only_by_rollover_alerts_when_it_will_run_out() {
        // SAR 100.00 of rollover and no budget of its own; SAR 60.00 spent over two days.
        val rolloverOnly = summary(gifts, budget = 10_000, expense = 6_000).copy(budgetMinor = 0, rolloverMinor = 10_000)

        assertEquals(mapOf(gifts.id to PaceAlertLevel.RUN_OUT), PaceAlerts.levels(insights(rolloverOnly, movements = runOutMovements(gifts))))
    }

    @Test
    fun alerts_are_due_once_per_level_and_again_only_when_a_pocket_gets_worse() {
        val runOut = insights(summary(gifts, budget = 10_000, expense = 6_000), movements = runOutMovements(gifts))
        val atRisk = insights(summary(gifts, budget = 10_000, expense = 8_500).copy(atRisk = true), movements = runOutMovements(gifts))

        val first = PaceAlerts.due(runOut, emptySet())
        val afterFirst = PaceAlerts.seen(runOut, emptySet())
        val repeated = PaceAlerts.due(runOut, afterFirst)
        val escalated = PaceAlerts.due(atRisk, afterFirst)
        val afterEscalation = PaceAlerts.seen(atRisk, afterFirst)

        assertEquals(listOf(PaceAlertLevel.RUN_OUT), first.map { it.level })
        assertTrue(repeated.isEmpty())
        assertEquals(listOf(PaceAlertLevel.AT_RISK), escalated.map { it.level })
        assertTrue(PaceAlerts.due(runOut, afterEscalation).isEmpty())
    }

    @Test
    fun a_pocket_jumping_straight_to_exhausted_gets_one_alert_and_old_periods_are_forgotten() {
        val exhausted = insights(summary(food, budget = 10_000, expense = 10_500).copy(exhausted = true))

        val due = PaceAlerts.due(exhausted, setOf("${february.id}/${food.id}/EXHAUSTED"))
        val seen = PaceAlerts.seen(exhausted, setOf("${february.id}/${food.id}/EXHAUSTED"))

        assertEquals(listOf(PaceAlertLevel.EXHAUSTED), due.map { it.level })
        assertEquals(PaceAlertLevel.entries.map { "${march.id}/${food.id}/$it" }.toSet(), seen)
    }

    @Test
    fun alert_copy_names_the_pocket_and_what_is_left() {
        val atRisk = insights(summary(market, budget = 10_000, expense = 8_500).copy(atRisk = true))
        val runOut = insights(summary(gifts, budget = 10_000, expense = 6_000), movements = runOutMovements(gifts))
        val exhausted = insights(summary(food, budget = 10_000, expense = 10_500).copy(exhausted = true))

        val risk = PaceAlerts.due(atRisk, emptySet()).single()
        val pace = PaceAlerts.due(runOut, emptySet()).single()
        val over = PaceAlerts.due(exhausted, emptySet()).single()

        assertEquals("Supermercado lleva el 85%", risk.title)
        assertEquals("Quedan SAR 15.00 de SAR 100.00 en este periodo.", risk.text)
        assertEquals("Regalos va rápido", pace.title)
        assertEquals("Al ritmo actual, se agota el 12 mar. Quedan SAR 40.00.", pace.text)
        assertEquals("Restaurantes sin disponible", over.title)
        assertEquals("Vas SAR 5.00 por encima de lo asignado.", over.text)
    }

    @Test
    fun the_coordinator_seeds_silently_then_alerts_only_for_changes_not_seen_in_the_app() = runTest {
        val preferences = FakePreferences()
        val posted = mutableListOf<PaceAlert>()
        val coordinator = PaceAlertCoordinator(preferences) { posted += it }
        val calm = state(summary(market, budget = 10_000, expense = 8_500).copy(atRisk = true))
        val seenInApp = state(summary(market, budget = 10_000, expense = 10_500).copy(exhausted = true))
        val background = state(
            summary(market, budget = 10_000, expense = 10_500).copy(exhausted = true),
            summary(food, budget = 10_000, expense = 8_500).copy(atRisk = true),
        )

        coordinator.notifyNew(calm)
        coordinator.markSeen(seenInApp)
        coordinator.notifyNew(background)

        assertEquals(listOf(food.id to PaceAlertLevel.AT_RISK), posted.map { it.pocketId to it.level })
    }

    @Test
    fun turning_alerts_off_still_marks_levels_as_seen() = runTest {
        val preferences = FakePreferences(AppPreferences(paceAlertsEnabled = false, paceAlertsSent = emptySet()))
        val posted = mutableListOf<PaceAlert>()
        val coordinator = PaceAlertCoordinator(preferences) { posted += it }
        val atRisk = state(summary(market, budget = 10_000, expense = 8_500).copy(atRisk = true))

        coordinator.notifyNew(atRisk)
        preferences.setPaceAlertsEnabled(true)
        coordinator.notifyNew(atRisk)

        assertTrue(posted.isEmpty())
    }

    private class FakePreferences(initial: AppPreferences = AppPreferences()) : PreferencesStore {
        override val state = MutableStateFlow(initial)
        override suspend fun setFuturePeriodStartDay(day: Int) = Unit
        override suspend fun setReminder(enabled: Boolean, time: LocalTime) = Unit
        override suspend fun setOnlineFxEnabled(enabled: Boolean) = Unit
        override suspend fun setDefaultExpenseCurrency(currency: SupportedCurrency) = Unit
        override suspend fun setPaceAlertsEnabled(enabled: Boolean) {
            state.value = state.value.copy(paceAlertsEnabled = enabled)
        }
        override suspend fun updatePaceAlertsSent(transform: (Set<String>?) -> Set<String>) {
            state.value = state.value.copy(paceAlertsSent = transform(state.value.paceAlertsSent))
        }
    }

    /** Two spending days of SAR 30.00 by 5 March, so SAR 40.00 of SAR 100.00 runs out on 12 March. */
    private fun runOutMovements(pocket: Pocket) = listOf(
        movement("${pocket.id}-a", LocalDate.of(2026, 2, 26), pocket),
        movement("${pocket.id}-b", LocalDate.of(2026, 3, 3), pocket),
    )

    private fun insights(vararg summaries: PocketPeriodSummary, movements: List<Movement> = emptyList()): PeriodInsights =
        PeriodInsights.of(state(*summaries, movements = movements), march.id)!!

    private fun state(vararg summaries: PocketPeriodSummary, movements: List<Movement> = emptyList()) = LedgerState(
        periods = listOf(february, march),
        currentPeriod = march,
        pockets = summaries.toList(),
        pocketCatalog = summaries.map { it.pocket },
        pocketSummariesByPeriod = mapOf(february.id to emptyList(), march.id to summaries.toList()),
        unallocatedMinorByPeriod = mapOf(february.id to 0L, march.id to 0L),
        movements = movements,
        elapsedDays = 9,
        totalDays = 28,
        currentLocalDate = LocalDate.of(2026, 3, 5),
    )

    private fun pocket(id: String, name: String) = Pocket(id, name, PocketIconKey.forName(name), 0, false, false)

    private fun summary(pocket: Pocket, budget: Long, expense: Long) = PocketPeriodSummary(
        pocket = pocket,
        budgetMinor = budget,
        rolloverMinor = 0,
        expenseMinor = expense,
        netSpendMinor = expense,
        availabilityMinor = budget - expense,
        consumedPercent = (expense * 100 / budget).toInt(),
        atRisk = false,
        exhausted = false,
    )

    private fun movement(id: String, date: LocalDate, pocket: Pocket) = Movement(
        id = id, pocketId = pocket.id, pocketName = pocket.name, periodId = march.id, type = MovementType.EXPENSE,
        accountingAmountMinor = 3_000, occurredAtUtcMillis = 0, localDate = date, zoneId = "Asia/Riyadh",
        merchant = null, note = null, paymentMethodId = null, paymentMethodName = null,
        originalAmountMinor = null, originalCurrencyCode = SupportedCurrency.SAR.name,
        conversionStatus = ConversionStatus.CONFIRMED, rate = null, conversionEffectiveDate = null, conversionSource = null,
    )
}
