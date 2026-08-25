package com.aif31.pocket

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.aif31.pocket.data.FinanceDatabase
import com.aif31.pocket.data.ConversionStatus
import com.aif31.pocket.data.LedgerCommand
import com.aif31.pocket.data.LedgerResult
import com.aif31.pocket.data.MovementType
import com.aif31.pocket.data.RoomPocketLedger
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
class PocketLedgerBehaviorTest {
    private lateinit var database: FinanceDatabase
    private val zone = ZoneId.of("Asia/Riyadh")
    private val clock = Clock.fixed(Instant.parse("2026-02-26T09:00:00Z"), zone)

    @Before fun setUp() {
        database = FinanceDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
    }

    @After fun tearDown() = database.close()

    @Test
    fun allocation_overspending_refund_and_selective_rollover_follow_the_product_rules() = runTest {
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
        ledger.execute(
            LedgerCommand.AddMovement(
                pocketId = supermarket.pocket.id,
                type = MovementType.EXPENSE,
                sarAmountMinor = 90_000,
                occurredAtUtcMillis = clock.millis(),
                localDate = java.time.LocalDate.of(2026, 2, 26),
            )
        )
        ledger.execute(
            LedgerCommand.AddMovement(
                pocketId = supermarket.pocket.id,
                type = MovementType.REFUND,
                sarAmountMinor = 5_000,
                occurredAtUtcMillis = clock.millis() + 1,
                localDate = java.time.LocalDate.of(2026, 2, 26),
            )
        )
        ledger.execute(
            LedgerCommand.AddMovement(
                pocketId = travel.pocket.id,
                type = MovementType.EXPENSE,
                sarAmountMinor = 5_000,
                occurredAtUtcMillis = clock.millis() + 2,
                localDate = java.time.LocalDate.of(2026, 2, 26),
            )
        )

        state = ledger.state.first { it.movements.size == 3 }
        assertEquals(-5_000, state.pockets.first { it.pocket.id == supermarket.pocket.id }.availabilityMinor)
        assertEquals(90_000, state.netSpendMinor)

        assertEquals(LedgerResult.Success, ledger.execute(LedgerCommand.CreateNextPeriod()))
        state = ledger.state.first { it.periods.size == 2 }
        val next = state.periods.maxBy { it.start }
        val nextPockets = state.pocketSummariesByPeriod.getValue(next.id)
        assertEquals(15_000, nextPockets.first { it.pocket.id == travel.pocket.id }.rolloverMinor)
        assertEquals(0, nextPockets.first { it.pocket.id == supermarket.pocket.id }.rolloverMinor)
    }

    @Test
    fun backup_round_trip_invalid_relationship_rollback_and_csv_are_observable() = runTest {
        val source = RoomPocketLedger(database, clock, zone)
        source.execute(LedgerCommand.Initialize(50_000))
        val sourceState = source.state.first { !it.needsOnboarding }
        val pocket = sourceState.pockets.first()
        source.execute(LedgerCommand.SetAllocation(sourceState.currentPeriod!!.id, pocket.pocket.id, 25_000))
        source.execute(
            LedgerCommand.AddMovement(
                pocketId = pocket.pocket.id,
                type = MovementType.EXPENSE,
                sarAmountMinor = 1_250,
                occurredAtUtcMillis = clock.millis(),
                localDate = java.time.LocalDate.of(2026, 2, 26),
                merchant = "KAUST Market",
                note = "fruta",
            )
        )
        val backup = source.exportBackup()
        assertTrue(source.previewBackup(backup).valid)
        assertFalse(source.previewBackup(backup.copyOf(backup.size / 2)).valid)
        assertFalse(source.previewBackup(backup.decodeToString().replaceFirst("\"version\": 2", "\"version\": 99").encodeToByteArray()).valid)
        assertFalse(source.previewBackup(backup.decodeToString().replaceFirst("\"budgetMinor\": 25000", "\"budgetMinor\": 60000").encodeToByteArray()).valid)
        assertEquals(LedgerResult.Success, source.restoreBackup(backup))

        val targetDb = FinanceDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
        try {
            val target = RoomPocketLedger(targetDb, clock, zone)
            assertEquals(LedgerResult.Success, target.execute(LedgerCommand.Initialize(99_900)))
            assertEquals(LedgerResult.Success, target.restoreBackup(backup))
            assertEquals(1, target.state.first { !it.needsOnboarding }.movements.size)
        } finally {
            targetDb.close()
        }

        val brokenText = backup.decodeToString()
        val relation = "\"pocketId\": \"${pocket.pocket.id}\""
        val broken = brokenText.replaceRange(brokenText.lastIndexOf(relation), brokenText.lastIndexOf(relation) + relation.length, "\"pocketId\": \"missing\"").encodeToByteArray()
        val emptyDb = FinanceDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
        try {
            val empty = RoomPocketLedger(emptyDb, clock, zone)
            assertFalse(empty.previewBackup(broken).valid)
            assertTrue(empty.restoreBackup(broken) is LedgerResult.Rejected)
            assertTrue(empty.state.first().needsOnboarding)
        } finally {
            emptyDb.close()
        }

        val csv = source.exportCsv().decodeToString()
        assertTrue(csv.startsWith("id,tipo,fecha,zona,pocket,importe_sar"))
        assertTrue(csv.contains("\"KAUST Market\""))
        assertTrue(csv.contains("\"12.50\""))
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
                occurredAtUtcMillis = clock.millis(), localDate = java.time.LocalDate.of(2026, 2, 26))
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
            sarAmountMinor = 375, occurredAtUtcMillis = clock.millis() + 1, localDate = java.time.LocalDate.of(2026, 2, 26),
            originalAmountMinor = 100, originalCurrencyCode = "USD", conversionStatus = ConversionStatus.ESTIMATED))
        state = ledger.state.first { it.movements.any { movement -> movement.id == "fx" } }
        assertEquals(375, state.movements.first { it.id == "fx" }.sarAmountMinor)
        ledger.execute(LedgerCommand.AddMovement(id = "fx", pocketId = pocket.pocket.id, type = MovementType.EXPENSE,
            sarAmountMinor = 400, occurredAtUtcMillis = clock.millis() + 1, localDate = java.time.LocalDate.of(2026, 2, 26),
            originalAmountMinor = 100, originalCurrencyCode = "USD", conversionStatus = ConversionStatus.CONFIRMED))
        state = ledger.state.first { it.movements.firstOrNull { movement -> movement.id == "fx" }?.sarAmountMinor == 400L }
        assertEquals(ConversionStatus.CONFIRMED, state.movements.first { it.id == "fx" }.conversionStatus)
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
            sarAmountMinor = 1_000, occurredAtUtcMillis = clock.millis(), localDate = java.time.LocalDate.of(2026, 2, 26)))
        firstPeriodLedger.execute(LedgerCommand.CreateNextPeriod())
        val currentLedger = RoomPocketLedger(database, Clock.fixed(Instant.parse("2026-03-26T09:00:00Z"), zone), zone)
        assertEquals(1_000L, currentLedger.state.first { it.currentPeriod?.start == java.time.LocalDate.of(2026, 3, 25) }.previousPeriodNetSpendMinor)
        currentLedger.execute(LedgerCommand.AddMovement(id = "historical", pocketId = pocketId, type = MovementType.EXPENSE,
            sarAmountMinor = 500, occurredAtUtcMillis = clock.millis(), localDate = java.time.LocalDate.of(2026, 2, 26)))
        assertEquals(500L, currentLedger.state.first { it.previousPeriodNetSpendMinor == 500L }.previousPeriodNetSpendMinor)
    }
}
