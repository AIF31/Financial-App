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
        assertEquals(
            listOf(PeriodPocketEntity(periodId, pocketId, rolloverEligible = true, retired = true)),
            dao.periodPockets(),
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
