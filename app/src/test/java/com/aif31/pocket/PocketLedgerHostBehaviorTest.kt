package com.aif31.pocket

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aif31.pocket.data.FinanceDatabase
import com.aif31.pocket.data.ConversionStatus
import com.aif31.pocket.data.LedgerCommand
import com.aif31.pocket.data.LedgerResult
import com.aif31.pocket.data.MovementType
import com.aif31.pocket.data.PocketIconKey
import com.aif31.pocket.data.PeriodPocketEntity
import com.aif31.pocket.data.RolloverReleaseEntity
import com.aif31.pocket.data.RoomPocketLedger
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PocketLedgerHostBehaviorTest {
    private lateinit var database: FinanceDatabase
    private val zone = ZoneId.of("Asia/Riyadh")
    private val clock = Clock.fixed(Instant.parse("2026-02-26T09:00:00Z"), zone)

    @Before
    fun setUp() {
        database = FinanceDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun allocation_refund_and_selective_rollover_are_transactional() = runTest {
        val ledger = RoomPocketLedger(database, clock, zone)
        ledger.execute(LedgerCommand.Initialize(100_000))
        var state = ledger.state.first { !it.needsOnboarding }
        val supermarket = state.pockets.first { it.pocket.name == "Supermercado" }
        val travel = state.pockets.first { it.pocket.name == "Viajes" }
        val period = state.currentPeriod!!

        assertTrue(ledger.execute(LedgerCommand.UpsertPocket(name = "Supermercado")) is LedgerResult.Rejected)
        assertEquals(LedgerResult.Success, ledger.execute(LedgerCommand.SetAllocation(period.id, supermarket.pocket.id, 80_000)))
        assertTrue(ledger.execute(LedgerCommand.SetAllocation(period.id, travel.pocket.id, 20_001)) is LedgerResult.Rejected)
        ledger.execute(LedgerCommand.SetAllocation(period.id, travel.pocket.id, 20_000))
        ledger.execute(LedgerCommand.UpsertPocket(travel.pocket.id, travel.pocket.name, rolloverEnabled = true))
        ledger.execute(LedgerCommand.AddMovement(pocketId = supermarket.pocket.id, type = MovementType.EXPENSE, sarAmountMinor = 90_000, occurredAtUtcMillis = clock.millis(), localDate = LocalDate.of(2026, 2, 26)))
        ledger.execute(LedgerCommand.AddMovement(pocketId = supermarket.pocket.id, type = MovementType.REFUND, sarAmountMinor = 5_000, occurredAtUtcMillis = clock.millis() + 1, localDate = LocalDate.of(2026, 2, 26)))
        ledger.execute(LedgerCommand.AddMovement(pocketId = travel.pocket.id, type = MovementType.EXPENSE, sarAmountMinor = 5_000, occurredAtUtcMillis = clock.millis() + 2, localDate = LocalDate.of(2026, 2, 26)))

        state = ledger.state.first { it.movements.size == 3 }
        assertEquals(-5_000, state.pockets.first { it.pocket.id == supermarket.pocket.id }.availabilityMinor)
        assertEquals(90_000, state.netSpendMinor)
        assertEquals(LedgerResult.Success, ledger.execute(LedgerCommand.CreateNextPeriod()))

        state = ledger.state.first { it.periods.size == 2 }
        val nextPockets = state.pocketSummariesByPeriod.getValue(state.periods.maxBy { it.start }.id)
        assertEquals(15_000, nextPockets.first { it.pocket.id == travel.pocket.id }.rolloverMinor)
        assertEquals(0, nextPockets.first { it.pocket.id == supermarket.pocket.id }.rolloverMinor)
    }

    @Test
    fun editing_historical_spend_cascades_rollover_without_mutating_later_period_data() = runTest {
        val ledger = RoomPocketLedger(database, clock, zone)
        val dao = database.financeDao()
        ledger.execute(LedgerCommand.Initialize(100_000))
        var state = ledger.state.first { !it.needsOnboarding }
        val pocket = state.pockets.first { it.pocket.name == "Viajes" }.pocket
        val first = state.currentPeriod!!
        ledger.execute(LedgerCommand.UpsertPocket(pocket.id, pocket.name, rolloverEnabled = true))
        ledger.execute(LedgerCommand.SetAllocation(first.id, pocket.id, 20_000))
        ledger.execute(LedgerCommand.AddMovement("first-spend", pocket.id, MovementType.EXPENSE, 8_000, clock.millis(), LocalDate.of(2026, 2, 26)))
        ledger.execute(LedgerCommand.CreateNextPeriod())

        state = ledger.state.first { it.periods.size == 2 }
        val second = state.periods[1]
        ledger.execute(LedgerCommand.UpdatePeriodFunds(second.id, 110_000))
        ledger.execute(LedgerCommand.SetAllocation(second.id, pocket.id, 10_000))
        ledger.execute(LedgerCommand.AddMovement("second-spend", pocket.id, MovementType.EXPENSE, 5_000, clock.millis(), LocalDate.of(2026, 3, 26)))
        ledger.execute(LedgerCommand.CreateNextPeriod())

        state = ledger.state.first { it.periods.size == 3 }
        val third = state.periods[2]
        ledger.execute(LedgerCommand.UpdatePeriodFunds(third.id, 120_000))
        ledger.execute(LedgerCommand.SetAllocation(third.id, pocket.id, 11_000))
        ledger.execute(LedgerCommand.AddMovement("third-spend", pocket.id, MovementType.EXPENSE, 6_000, clock.millis(), LocalDate.of(2026, 4, 26)))
        ledger.execute(LedgerCommand.CreateNextPeriod())

        state = ledger.state.first { it.periods.size == 4 }
        val fourth = state.periods[3]
        ledger.execute(LedgerCommand.UpdatePeriodFunds(fourth.id, 130_000))
        val laterIds = state.periods.drop(1).map { it.id }.toSet()
        suspend fun laterRollover() = dao.allocations().filter { it.periodId in laterIds && it.pocketId == pocket.id }
            .sortedBy { allocation -> state.periods.indexOfFirst { it.id == allocation.periodId } }
            .map { it.rolloverMinor }
        assertEquals(listOf(12_000L, 17_000L, 22_000L), laterRollover())
        val laterFundsBefore = dao.periods().filter { it.id in laterIds }.map { it.id to it.newFundsMinor }
        val laterBudgetsBefore = dao.allocations().filter { it.periodId in laterIds }.map { Triple(it.periodId, it.pocketId, it.budgetMinor) }
        val laterMovementsBefore = dao.movements().filter { it.periodId in laterIds }

        ledger.execute(LedgerCommand.AddMovement("first-spend", pocket.id, MovementType.EXPENSE, 10_000, clock.millis(), LocalDate.of(2026, 2, 26)))

        assertEquals(listOf(10_000L, 15_000L, 20_000L), laterRollover())
        assertEquals(laterFundsBefore, dao.periods().filter { it.id in laterIds }.map { it.id to it.newFundsMinor })
        assertEquals(laterBudgetsBefore, dao.allocations().filter { it.periodId in laterIds }.map { Triple(it.periodId, it.pocketId, it.budgetMinor) })
        assertEquals(laterMovementsBefore, dao.movements().filter { it.periodId in laterIds })
    }

    @Test
    fun rollover_includes_refunds_without_a_budget_and_clamps_negative_availability() = runTest {
        val ledger = RoomPocketLedger(database, clock, zone)
        ledger.execute(LedgerCommand.Initialize(20_000))
        val state = ledger.state.first { !it.needsOnboarding }
        val refundPocket = state.pockets.first { it.pocket.name == "Viajes" }.pocket
        val negativePocket = state.pockets.first { it.pocket.name == "Ocio" }.pocket
        ledger.execute(LedgerCommand.UpsertPocket(refundPocket.id, refundPocket.name, rolloverEnabled = true))
        ledger.execute(LedgerCommand.UpsertPocket(negativePocket.id, negativePocket.name, rolloverEnabled = true))
        ledger.execute(LedgerCommand.AddMovement("refund-only", refundPocket.id, MovementType.REFUND, 4_000, clock.millis(), LocalDate.of(2026, 2, 26)))
        ledger.execute(LedgerCommand.SetAllocation(state.currentPeriod!!.id, negativePocket.id, 2_000))
        ledger.execute(LedgerCommand.AddMovement("overspent", negativePocket.id, MovementType.EXPENSE, 3_000, clock.millis(), LocalDate.of(2026, 2, 26)))

        ledger.execute(LedgerCommand.CreateNextPeriod())

        val next = ledger.state.first { it.periods.size == 2 }.pocketSummariesByPeriod.getValue(ledger.state.first().periods.last().id)
        assertEquals(4_000L, next.first { it.pocket.id == refundPocket.id }.rolloverMinor)
        assertEquals(0L, next.first { it.pocket.id == negativePocket.id }.rolloverMinor)
    }

    @Test
    fun allocation_delete_restore_move_and_historical_eligibility_recalculate_from_the_earliest_source() = runTest {
        val firstLedger = RoomPocketLedger(database, clock, zone)
        firstLedger.execute(LedgerCommand.Initialize(30_000))
        var state = firstLedger.state.first { !it.needsOnboarding }
        val pocket = state.pockets.first { it.pocket.name == "Viajes" }.pocket
        val first = state.currentPeriod!!
        firstLedger.execute(LedgerCommand.UpsertPocket(pocket.id, pocket.name, rolloverEnabled = true))
        firstLedger.execute(LedgerCommand.SetAllocation(first.id, pocket.id, 10_000))
        firstLedger.execute(LedgerCommand.AddMovement("mutable", pocket.id, MovementType.EXPENSE, 3_000, clock.millis(), LocalDate.of(2026, 2, 26)))
        firstLedger.execute(LedgerCommand.CreateNextPeriod())
        state = firstLedger.state.first { it.periods.size == 2 }
        val second = state.periods[1]

        val secondLedger = RoomPocketLedger(database, Clock.fixed(Instant.parse("2026-03-26T09:00:00Z"), zone), zone)
        secondLedger.execute(LedgerCommand.UpsertPocket(pocket.id, pocket.name, rolloverEnabled = false))
        secondLedger.execute(LedgerCommand.CreateNextPeriod())
        val third = secondLedger.state.first { it.periods.size == 3 }.periods[2]
        suspend fun rollover(periodId: String) = database.financeDao().allocation(periodId, pocket.id)!!.rolloverMinor

        firstLedger.execute(LedgerCommand.SetAllocation(first.id, pocket.id, 12_000))
        assertEquals(9_000L, rollover(second.id))
        assertEquals(0L, rollover(third.id))

        val deleted = firstLedger.execute(LedgerCommand.DeleteMovement("mutable")) as LedgerResult.Deleted
        assertEquals(12_000L, rollover(second.id))
        assertEquals(0L, rollover(third.id))
        firstLedger.execute(LedgerCommand.RestoreMovement(deleted.movement))
        assertEquals(9_000L, rollover(second.id))

        firstLedger.execute(LedgerCommand.AddMovement("mutable", pocket.id, MovementType.EXPENSE, 3_000, clock.millis(), LocalDate.of(2026, 3, 26)))
        assertEquals(12_000L, rollover(second.id))
        assertEquals(0L, rollover(third.id))
    }

    @Test
    fun archive_is_blocked_by_active_templates_then_releases_current_accounting_and_rejects_new_references() = runTest {
        val firstLedger = RoomPocketLedger(database, clock, zone)
        firstLedger.execute(LedgerCommand.Initialize(30_000))
        var state = firstLedger.state.first { !it.needsOnboarding }
        val pocket = state.pockets.first { it.pocket.name == "Viajes" }.pocket
        val first = state.currentPeriod!!
        firstLedger.execute(LedgerCommand.UpsertPocket(pocket.id, pocket.name, rolloverEnabled = true))
        firstLedger.execute(LedgerCommand.SetAllocation(first.id, pocket.id, 10_000))
        firstLedger.execute(LedgerCommand.AddMovement("first-expense", pocket.id, MovementType.EXPENSE, 5_000, clock.millis(), LocalDate.of(2026, 2, 26)))
        firstLedger.execute(LedgerCommand.CreateNextPeriod())

        val ledger = RoomPocketLedger(database, Clock.fixed(Instant.parse("2026-03-26T09:00:00Z"), zone), zone)
        state = ledger.state.first { it.currentPeriod?.start == LocalDate.of(2026, 3, 25) }
        val current = state.currentPeriod!!
        ledger.execute(LedgerCommand.UpdatePeriodFunds(current.id, 30_000))
        ledger.execute(LedgerCommand.SetAllocation(current.id, pocket.id, 30_000))
        ledger.execute(LedgerCommand.AddMovement("kept-expense", pocket.id, MovementType.EXPENSE, 2_000, clock.millis(), LocalDate.of(2026, 3, 26)))
        ledger.execute(LedgerCommand.AddMovement("kept-refund", pocket.id, MovementType.REFUND, 1_000, clock.millis(), LocalDate.of(2026, 3, 26)))
        ledger.execute(LedgerCommand.UpsertTemplate("phone", "Teléfono", 1_000, pocket.id))
        ledger.execute(LedgerCommand.UpsertTemplate("subscription", "Suscripción", 2_000, pocket.id))

        val blocked = ledger.execute(LedgerCommand.ArchivePocket(pocket.id)) as LedgerResult.Rejected
        assertTrue(blocked.message.contains("Teléfono"))
        assertTrue(blocked.message.contains("Suscripción"))
        assertFalse(database.financeDao().pockets().single { it.id == pocket.id }.archived)

        val templateIds = ledger.state.first { it.templates.size == 2 }.templates.associate { it.name to it.id }
        ledger.execute(LedgerCommand.ArchiveTemplate(templateIds.getValue("Teléfono")))
        ledger.execute(LedgerCommand.ArchiveTemplate(templateIds.getValue("Suscripción")))
        assertEquals(LedgerResult.Success, ledger.execute(LedgerCommand.ArchivePocket(pocket.id)))

        state = ledger.state.first { it.pockets.any { summary -> summary.pocket.id == pocket.id && summary.retiredThisPeriod } }
        val retired = state.pockets.single { it.pocket.id == pocket.id }
        assertEquals(30_000L, state.unallocatedMinor)
        assertEquals(0L, retired.budgetMinor)
        assertEquals(0L, retired.rolloverMinor)
        assertEquals(5_000L, retired.rolloverReleasedMinor)
        assertTrue(retired.retiredThisPeriod)
        assertEquals(
            setOf("kept-expense", "kept-refund"),
            state.movements.filter { it.periodId == current.id && it.pocketId == pocket.id }.map { it.id }.toSet(),
        )
        assertEquals(5_000L, database.financeDao().rolloverReleases().single { it.periodId == current.id && it.pocketId == pocket.id }.amountMinor)

        assertTrue(ledger.execute(LedgerCommand.SetAllocation(current.id, pocket.id, 1_000)) is LedgerResult.Rejected)
        assertTrue(ledger.execute(LedgerCommand.AddMovement("new", pocket.id, MovementType.EXPENSE, 1_000, clock.millis(), LocalDate.of(2026, 3, 26))) is LedgerResult.Rejected)
        assertTrue(ledger.execute(LedgerCommand.UpsertTemplate(name = "Nueva", amountMinor = 1_000, pocketId = pocket.id)) is LedgerResult.Rejected)

        ledger.execute(LedgerCommand.CreateNextPeriod())
        val after = ledger.state.first { it.periods.size == 3 }
        assertFalse(after.pocketSummariesByPeriod.getValue(after.periods.last().id).any { it.pocket.id == pocket.id })
        assertTrue(after.pocketSummariesByPeriod.getValue(first.id).any { it.pocket.id == pocket.id })
    }

    @Test
    fun catch_up_with_no_missing_periods_keeps_the_existing_period_unchanged() = runTest {
        val ledger = RoomPocketLedger(database, clock, zone)
        ledger.execute(LedgerCommand.Initialize(100_000))
        val before = ledger.state.first { !it.needsOnboarding }.periods

        assertEquals(LedgerResult.Success, ledger.execute(LedgerCommand.CatchUpPeriods(preferredStartDay = 25)))

        val after = ledger.state.first { it.periods.size == before.size }.periods
        assertEquals(before, after)
        assertFalse(after.single().needsReview)
    }

    @Test
    fun catch_up_creates_one_missing_period_and_marks_only_it_for_review() = runTest {
        val mutableClock = MutableClock(Instant.parse("2026-02-26T09:00:00Z"), zone)
        val ledger = RoomPocketLedger(database, mutableClock, zone)
        ledger.execute(LedgerCommand.Initialize(100_000))
        mutableClock.value = Instant.parse("2026-03-26T09:00:00Z")

        assertEquals(LedgerResult.Success, ledger.execute(LedgerCommand.CatchUpPeriods(preferredStartDay = 25)))

        val periods = ledger.state.first { it.periods.size == 2 }.periods
        assertEquals(listOf(LocalDate.of(2026, 2, 25), LocalDate.of(2026, 3, 25)), periods.map { it.start })
        assertEquals(listOf(false, true), periods.map { it.needsReview })
        assertEquals(LocalDate.of(2026, 3, 25), periods.last().start)
    }

    @Test
    fun catch_up_creates_every_missing_period_sequentially_and_is_idempotent() = runTest {
        val mutableClock = MutableClock(Instant.parse("2026-02-26T09:00:00Z"), zone)
        val ledger = RoomPocketLedger(database, mutableClock, zone)
        ledger.execute(LedgerCommand.Initialize(100_000))
        mutableClock.value = Instant.parse("2026-06-26T09:00:00Z")

        assertEquals(LedgerResult.Success, ledger.execute(LedgerCommand.CatchUpPeriods(preferredStartDay = 25)))
        val firstCatchUp = ledger.state.first { it.periods.size == 5 }.periods
        assertEquals(
            listOf(
                LocalDate.of(2026, 2, 25),
                LocalDate.of(2026, 3, 25),
                LocalDate.of(2026, 4, 25),
                LocalDate.of(2026, 5, 25),
                LocalDate.of(2026, 6, 25),
            ),
            firstCatchUp.map { it.start },
        )
        assertEquals(listOf(false, false, false, false, true), firstCatchUp.map { it.needsReview })

        assertEquals(LedgerResult.Success, ledger.execute(LedgerCommand.CatchUpPeriods(preferredStartDay = 25)))
        val repeated = ledger.state.first { it.periods.size == 5 }.periods
        assertEquals(firstCatchUp.map { it.id }, repeated.map { it.id })
        assertEquals(firstCatchUp, repeated)
    }

    @Test
    fun backup_restore_rejects_bad_relationships_atomically_and_csv_is_observable() = runTest {
        val source = RoomPocketLedger(database, clock, zone)
        source.execute(LedgerCommand.Initialize(50_000))
        val sourceState = source.state.first { !it.needsOnboarding }
        val pocket = sourceState.pockets.first()
        source.execute(LedgerCommand.SetAllocation(sourceState.currentPeriod!!.id, pocket.pocket.id, 25_000))
        source.execute(LedgerCommand.AddMovement(pocketId = pocket.pocket.id, type = MovementType.EXPENSE, sarAmountMinor = 1_250, occurredAtUtcMillis = clock.millis(), localDate = LocalDate.of(2026, 2, 26), merchant = "KAUST Market", note = "fruta"))
        val backup = source.exportBackup()

        assertTrue(source.previewBackup(backup).valid)
        assertFalse(source.previewBackup(backup.copyOf(backup.size / 2)).valid)
        assertFalse(source.previewBackup(backup.decodeToString().replaceFirst("\"version\": 3", "\"version\": 99").encodeToByteArray()).valid)
        assertFalse(source.previewBackup(backup.decodeToString().replaceFirst("\"budgetMinor\": 25000", "\"budgetMinor\": 60000").encodeToByteArray()).valid)
        assertEquals(LedgerResult.Success, source.restoreBackup(backup))
        withFreshLedger { target ->
            assertEquals(LedgerResult.Success, target.execute(LedgerCommand.Initialize(99_900)))
            assertEquals(LedgerResult.Success, target.restoreBackup(backup))
            val restored = target.state.first { !it.needsOnboarding && it.movements.size == 1 }
            assertEquals(50_000, restored.newFundsMinor)
            assertEquals(PocketIconKey.SUPERMARKET, restored.pockets.first { it.pocket.name == "Supermercado" }.pocket.iconKey)
        }
        val legacyBackup = backup.decodeToString()
            .replaceFirst("\"version\": 3", "\"version\": 1")
            .replace(Regex(",\\s*\"iconKey\": \"[A-Z]+\""), "")
            .encodeToByteArray()
        withFreshLedger { target ->
            assertEquals(LedgerResult.Success, target.restoreBackup(legacyBackup))
            val restored = target.state.first { !it.needsOnboarding }
            assertEquals(PocketIconKey.TRANSPORT, restored.pockets.first { it.pocket.name == "Transporte" }.pocket.iconKey)
        }

        val text = backup.decodeToString()
        val relation = "\"pocketId\": \"${pocket.pocket.id}\""
        val index = text.lastIndexOf(relation)
        assertTrue(index >= 0)
        val broken = text.replaceRange(index, index + relation.length, "\"pocketId\": \"missing\"").encodeToByteArray()
        withFreshLedger { empty ->
            assertFalse(empty.previewBackup(broken).valid)
            assertTrue(empty.restoreBackup(broken) is LedgerResult.Rejected)
            assertTrue(empty.state.first().needsOnboarding)
        }

        val csv = source.exportCsv().decodeToString()
        assertTrue(csv.startsWith("id,tipo,fecha,zona,pocket,importe_sar"))
        assertTrue(csv.contains("\"KAUST Market\""))
        assertTrue(csv.contains("\"12.50\""))
    }

    @Test
    fun backup_version_three_round_trips_period_Pocket_state_and_rollover_releases() = runTest {
        val ledger = RoomPocketLedger(database, clock, zone)
        ledger.execute(LedgerCommand.Initialize(50_000))
        val state = ledger.state.first { !it.needsOnboarding }
        val periodId = state.currentPeriod!!.id
        val pocketId = state.pockets.first { it.pocket.name == "Viajes" }.pocket.id
        val dao = database.financeDao()
        dao.putPeriodPocket(PeriodPocketEntity(periodId, pocketId, rolloverEligible = true, retired = true))
        dao.putRolloverRelease(RolloverReleaseEntity(periodId, pocketId, amountMinor = 5_000))

        val backup = ledger.exportBackup()
        assertTrue(backup.decodeToString().contains("\"version\": 3"))
        dao.clearRolloverReleases()
        dao.clearPeriodPockets()

        assertEquals(LedgerResult.Success, ledger.restoreBackup(backup))
        val restoredPeriodPockets = dao.periodPockets()
        assertEquals(state.pockets.size, restoredPeriodPockets.size)
        assertEquals(
            PeriodPocketEntity(periodId, pocketId, rolloverEligible = true, retired = true),
            restoredPeriodPockets.single { it.periodId == periodId && it.pocketId == pocketId },
        )
        assertEquals(
            listOf(RolloverReleaseEntity(periodId, pocketId, amountMinor = 5_000)),
            dao.rolloverReleases(),
        )

        val legacyVersionTwo = backup.decodeToString()
            .replaceFirst("\"version\": 3", "\"version\": 2")
            .replace(
                Regex(
                    "\\s*\"periodPockets\": \\[.*?],\\s*\"rolloverReleases\": \\[.*?],",
                    RegexOption.DOT_MATCHES_ALL,
                ),
                "",
            )
            .encodeToByteArray()
        assertTrue(ledger.previewBackup(legacyVersionTwo).valid)
    }

    @Test
    fun alert_reversals_fx_confirmation_and_delete_restore_are_observable() = runTest {
        val ledger = RoomPocketLedger(database, clock, zone)
        ledger.execute(LedgerCommand.Initialize(20_000))
        var state = ledger.state.first { !it.needsOnboarding }
        val pocket = state.pockets.first()
        val periodId = state.currentPeriod!!.id
        ledger.execute(LedgerCommand.SetAllocation(periodId, pocket.pocket.id, 10_000))

        suspend fun saveAlert(amount: Long) = ledger.execute(
            LedgerCommand.AddMovement(id = "alert", pocketId = pocket.pocket.id, type = MovementType.EXPENSE, sarAmountMinor = amount,
                occurredAtUtcMillis = clock.millis(), localDate = LocalDate.of(2026, 2, 26))
        )
        saveAlert(8_000)
        state = ledger.state.first { it.movements.singleOrNull()?.sarAmountMinor == 8_000L }
        assertTrue(state.pockets.first().atRisk)
        assertFalse(state.pockets.first().exhausted)
        saveAlert(10_000)
        state = ledger.state.first { it.movements.singleOrNull()?.sarAmountMinor == 10_000L }
        assertTrue(state.pockets.first().exhausted)
        saveAlert(7_900)
        state = ledger.state.first { it.movements.singleOrNull()?.sarAmountMinor == 7_900L }
        assertFalse(state.pockets.first().atRisk)

        ledger.execute(LedgerCommand.AddMovement(id = "fx", pocketId = pocket.pocket.id, type = MovementType.EXPENSE,
            sarAmountMinor = 375, occurredAtUtcMillis = clock.millis() + 1, localDate = LocalDate.of(2026, 2, 26),
            originalAmountMinor = 100, originalCurrencyCode = "USD", conversionStatus = ConversionStatus.ESTIMATED))
        state = ledger.state.first { it.movements.any { movement -> movement.id == "fx" } }
        assertEquals(375, state.movements.first { it.id == "fx" }.sarAmountMinor)
        ledger.execute(LedgerCommand.AddMovement(id = "fx", pocketId = pocket.pocket.id, type = MovementType.EXPENSE,
            sarAmountMinor = 400, occurredAtUtcMillis = clock.millis() + 1, localDate = LocalDate.of(2026, 2, 26),
            originalAmountMinor = 100, originalCurrencyCode = "USD", conversionStatus = ConversionStatus.CONFIRMED))
        state = ledger.state.first { it.movements.firstOrNull { movement -> movement.id == "fx" }?.sarAmountMinor == 400L }
        val confirmed = state.movements.first { it.id == "fx" }
        assertEquals(100L, confirmed.originalAmountMinor)
        assertEquals(ConversionStatus.CONFIRMED, confirmed.conversionStatus)

        val deleted = ledger.execute(LedgerCommand.DeleteMovement("fx")) as LedgerResult.Deleted
        assertTrue(ledger.state.first { it.movements.none { movement -> movement.id == "fx" } }.movements.none { it.id == "fx" })
        ledger.execute(LedgerCommand.RestoreMovement(deleted.movement))
        assertTrue(ledger.state.first { it.movements.any { movement -> movement.id == "fx" } }.movements.any { it.id == "fx" })
    }

    @Test
    fun editing_historical_spend_recalculates_the_current_period_comparison() = runTest {
        val firstPeriodLedger = RoomPocketLedger(database, clock, zone)
        firstPeriodLedger.execute(LedgerCommand.Initialize(20_000))
        val firstState = firstPeriodLedger.state.first { !it.needsOnboarding }
        val pocketId = firstState.pockets.first().pocket.id
        firstPeriodLedger.execute(LedgerCommand.AddMovement(id = "historical", pocketId = pocketId, type = MovementType.EXPENSE,
            sarAmountMinor = 1_000, occurredAtUtcMillis = clock.millis(), localDate = LocalDate.of(2026, 2, 26)))
        firstPeriodLedger.execute(LedgerCommand.CreateNextPeriod())

        val laterClock = Clock.fixed(Instant.parse("2026-03-26T09:00:00Z"), zone)
        val currentLedger = RoomPocketLedger(database, laterClock, zone)
        assertEquals(1_000L, currentLedger.state.first { it.currentPeriod?.start == LocalDate.of(2026, 3, 25) }.previousPeriodNetSpendMinor)
        currentLedger.execute(LedgerCommand.AddMovement(id = "historical", pocketId = pocketId, type = MovementType.EXPENSE,
            sarAmountMinor = 500, occurredAtUtcMillis = clock.millis(), localDate = LocalDate.of(2026, 2, 26)))
        assertEquals(500L, currentLedger.state.first { it.previousPeriodNetSpendMinor == 500L }.previousPeriodNetSpendMinor)
    }

    @Test
    fun movement_defaults_read_the_current_clock_without_a_database_emission() {
        val mutableClock = MutableClock(Instant.parse("2026-02-26T20:59:00Z"), zone)
        val ledger = RoomPocketLedger(database, mutableClock, zone)

        assertEquals(LocalDate.of(2026, 2, 26), ledger.movementDefaults().localDate)

        mutableClock.value = Instant.parse("2026-02-26T21:01:00Z")

        assertEquals(LocalDate.of(2026, 2, 27), ledger.movementDefaults().localDate)
        assertEquals(mutableClock.millis(), ledger.movementDefaults().instantMillis)
    }

    @Test
    fun state_does_not_expose_an_expired_period_as_current_before_catch_up() = runTest {
        val ledger = RoomPocketLedger(database, clock, zone)
        ledger.execute(LedgerCommand.Initialize(20_000))

        val laterLedger = RoomPocketLedger(
            database,
            Clock.fixed(Instant.parse("2026-05-01T09:00:00Z"), zone),
            zone,
        )

        assertNull(laterLedger.state.first { it.periods.isNotEmpty() }.currentPeriod)
    }

    @Test
    fun csv_export_escapes_quotes_newlines_and_neutralizes_formulas() = runTest {
        val ledger = RoomPocketLedger(database, clock, zone)
        ledger.execute(LedgerCommand.Initialize(100_000))
        val pocket = ledger.state.first { !it.needsOnboarding }.pockets.first()
        ledger.execute(
            LedgerCommand.AddMovement(
                id = "csv-risk",
                pocketId = pocket.pocket.id,
                type = MovementType.EXPENSE,
                sarAmountMinor = 1_250,
                occurredAtUtcMillis = clock.millis(),
                localDate = LocalDate.of(2026, 2, 26),
                merchant = "=2+2",
                note = "línea \"uno\"\nlínea dos",
            )
        )

        val csv = ledger.exportCsv().decodeToString()

        assertTrue(csv.contains("\"'=2+2\""))
        assertTrue(csv.contains("\"línea \"\"uno\"\"\nlínea dos\""))
    }

    private suspend fun withFreshLedger(block: suspend (RoomPocketLedger) -> Unit) {
        val freshDb = FinanceDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
        try {
            block(RoomPocketLedger(freshDb, clock, zone))
        } finally {
            freshDb.close()
        }
    }

    private class MutableClock(
        var value: Instant,
        private val zoneId: ZoneId,
    ) : Clock() {
        override fun getZone(): ZoneId = zoneId
        override fun withZone(zone: ZoneId): Clock = MutableClock(value, zone)
        override fun instant(): Instant = value
    }
}
