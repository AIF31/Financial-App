package com.aif31.pocket

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aif31.pocket.data.FinanceDatabase
import com.aif31.pocket.data.LedgerCommand
import com.aif31.pocket.data.LedgerResult
import com.aif31.pocket.data.MovementType
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
    fun backup_restore_rejects_bad_relationships_atomically_and_csv_is_observable() = runTest {
        val source = RoomPocketLedger(database, clock, zone)
        source.execute(LedgerCommand.Initialize(50_000))
        val sourceState = source.state.first { !it.needsOnboarding }
        val pocket = sourceState.pockets.first()
        source.execute(LedgerCommand.SetAllocation(sourceState.currentPeriod!!.id, pocket.pocket.id, 25_000))
        source.execute(LedgerCommand.AddMovement(pocketId = pocket.pocket.id, type = MovementType.EXPENSE, sarAmountMinor = 1_250, occurredAtUtcMillis = clock.millis(), localDate = LocalDate.of(2026, 2, 26), merchant = "KAUST Market", note = "fruta"))
        val backup = source.exportBackup()

        assertTrue(source.previewBackup(backup).valid)
        assertTrue(source.restoreBackup(backup) is LedgerResult.Rejected)
        withFreshLedger { target ->
            assertEquals(LedgerResult.Success, target.restoreBackup(backup))
            assertEquals(1, target.state.first { !it.needsOnboarding }.movements.size)
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

    private suspend fun withFreshLedger(block: suspend (RoomPocketLedger) -> Unit) {
        val freshDb = FinanceDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
        try {
            block(RoomPocketLedger(freshDb, clock, zone))
        } finally {
            freshDb.close()
        }
    }
}
