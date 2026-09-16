package com.aif31.pocket

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aif31.pocket.data.FinanceDatabase
import com.aif31.pocket.data.LedgerCommand
import com.aif31.pocket.data.LedgerResult
import com.aif31.pocket.data.LedgerState
import com.aif31.pocket.data.MovementType
import com.aif31.pocket.data.RoomPocketLedger
import com.aif31.pocket.domain.SupportedCurrency
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SnapshotConsistencyHostTest {
    private lateinit var context: Context
    private lateinit var database: FinanceDatabase
    private lateinit var writerDatabase: FinanceDatabase
    private lateinit var databaseName: String
    private lateinit var queryGate: InFlightWriteQueryGate
    private val zone = ZoneId.of("Asia/Riyadh")
    private val clock = Clock.fixed(Instant.parse("2026-02-26T09:00:00Z"), zone)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "snapshot-consistency-${UUID.randomUUID()}.db"
        queryGate = InFlightWriteQueryGate()
        database = Room.databaseBuilder(context, FinanceDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .setQueryCallback(queryGate, Executor { command -> command.run() })
            .build()
        writerDatabase = Room.databaseBuilder(context, FinanceDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()
    }

    @After
    fun tearDown() {
        writerDatabase.close()
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun initial_state_load_is_one_complete_snapshot_while_a_write_is_in_flight() = runTest {
        val ledger = RoomPocketLedger(database, clock, zone)
        val (periodId, pocketId) = seedVersion(database, ledger, 10_000, "Before")
        ledger.state.first { !it.needsOnboarding } // Prime Room's invalidation triggers before arming the gate.

        val state = readDuringPausedWrite("select * from pockets", write = {
            val dao = writerDatabase.financeDao()
            dao.updatePeriod(dao.period(periodId)!!.copy(newFundsMinor = 20_000))
            dao.putPocket(dao.pockets().single { it.id == pocketId }.copy(name = "After"))
        }) {
            RoomPocketLedger(database, clock, zone).state.first { !it.needsOnboarding }
        }

        assertCompleteVersion(state, pocketId, 10_000 to "Before", 20_000 to "After")
    }

    @Test
    fun repeated_invalidation_never_emits_a_mixed_snapshot() = runTest {
        val ledger = RoomPocketLedger(database, clock, zone)
        val (periodId, pocketId) = seedVersion(database, ledger, 10_000, "Before")
        ledger.state.first { !it.needsOnboarding }
        val states = Channel<LedgerState>(Channel.UNLIMITED)
        val continueAfterInitial = CountDownLatch(1)
        val collector = backgroundScope.launch(Dispatchers.IO) {
            var first = true
            ledger.state.collect { state ->
                states.send(state)
                if (first) {
                    first = false
                    check(continueAfterInitial.await(10, TimeUnit.SECONDS))
                }
            }
        }
        val initial = withTimeout(10_000) { states.receive() }
        assertCompleteVersion(initial, pocketId, 10_000 to "Before")

        database.withTransaction {
            val dao = database.financeDao()
            dao.updatePeriod(dao.period(periodId)!!.copy(newFundsMinor = 20_000))
            dao.putPocket(dao.pockets().single { it.id == pocketId }.copy(name = "Middle"))
        }
        val stateAfterRepeatedInvalidation = readDuringPausedWrite("select * from pockets", write = {
            val dao = writerDatabase.financeDao()
            dao.updatePeriod(dao.period(periodId)!!.copy(newFundsMinor = 30_000))
            dao.putPocket(dao.pockets().single { it.id == pocketId }.copy(name = "After"))
        }) {
            continueAfterInitial.countDown()
            withTimeout(10_000) { states.receive() }
        }

        collector.cancelAndJoin()
        assertCompleteVersion(stateAfterRepeatedInvalidation, pocketId, 30_000 to "After")
    }

    @Test
    fun catch_up_state_is_pre_write_or_post_write_while_the_write_is_in_flight() = runTest {
        val setup = RoomPocketLedger(database, clock, zone)
        setup.execute(LedgerCommand.Initialize(10_000))
        val initialPeriod = setup.state.first { !it.needsOnboarding }.currentPeriod!!
        assertEquals(
            LedgerResult.Success,
            setup.execute(
                LedgerCommand.ScheduleCurrencyChange(
                    targetCurrency = SupportedCurrency.USD,
                    rate = "0.2666",
                    effectiveDate = initialPeriod.endExclusive,
                    source = "Snapshot test",
                    quoteEffectiveDate = LocalDate.of(2026, 2, 26),
                )
            ),
        )
        val later = RoomPocketLedger(
            database,
            Clock.fixed(Instant.parse("2026-05-01T09:00:00Z"), zone),
            zone,
        )
        val writer = RoomPocketLedger(
            writerDatabase,
            Clock.fixed(Instant.parse("2026-05-01T09:00:00Z"), zone),
            zone,
        )
        later.state.first { it.periods.isNotEmpty() }

        val state = readDuringPausedWrite("select * from pockets", write = {
            assertEquals(LedgerResult.Success, writer.execute(LedgerCommand.CatchUpPeriods(25)))
        }) {
            later.state.first { it.periods.isNotEmpty() }
        }

        val preWrite = state.periods.size == 1 && state.currentPeriod == null && state.pendingCurrencyChange != null
        val postWrite = state.periods.size == 3 &&
            state.currentPeriod?.start == LocalDate.of(2026, 4, 25) &&
            state.pendingCurrencyChange == null
        assertTrue("Expected a complete pre-catch-up or post-catch-up state", preWrite || postWrite)
    }

    @Test
    fun failed_and_canceled_writes_expose_only_the_rolled_back_snapshot() = runTest {
        val ledger = RoomPocketLedger(database, clock, zone)
        val (periodId, pocketId) = seedVersion(database, ledger, 10_000, "Before")
        ledger.state.first { !it.needsOnboarding }

        listOf(false, true).forEach { cancelAfterPause ->
            val state = readDuringPausedWrite(
                "select * from pockets",
                rollbackAfterPause = !cancelAfterPause,
                cancelAfterPause = cancelAfterPause,
                write = {
                    val dao = writerDatabase.financeDao()
                    dao.updatePeriod(dao.period(periodId)!!.copy(newFundsMinor = 20_000))
                    dao.putPocket(dao.pockets().single { it.id == pocketId }.copy(name = "Never committed"))
                },
            ) {
                ledger.state.first { !it.needsOnboarding }
            }

            assertCompleteVersion(state, pocketId, 10_000 to "Before")
            assertCompleteVersion(ledger.state.first { !it.needsOnboarding }, pocketId, 10_000 to "Before")
        }
    }

    @Test
    fun restore_state_is_one_complete_snapshot_while_restore_is_in_flight() = runTest {
        val target = RoomPocketLedger(database, clock, zone)
        val writerTarget = RoomPocketLedger(writerDatabase, clock, zone)
        seedVersion(database, target, 10_000, "Before restore")
        target.state.first { !it.needsOnboarding }

        val sourceDatabase = FinanceDatabase.inMemory(context)
        val backup = try {
            val source = RoomPocketLedger(sourceDatabase, clock, zone)
            seedVersion(sourceDatabase, source, 20_000, "After restore")
            source.exportBackup()
        } finally {
            sourceDatabase.close()
        }

        val state = readDuringPausedWrite("select * from pockets", write = {
            assertEquals(LedgerResult.Success, writerTarget.restoreBackup(backup))
        }) {
            target.state.first { !it.needsOnboarding }
        }

        val preWrite = state.newFundsMinor == 10_000L && state.pockets.any { it.pocket.name == "Before restore" }
        val postWrite = state.newFundsMinor == 20_000L && state.pockets.any { it.pocket.name == "After restore" }
        assertTrue("Expected a complete pre-restore or post-restore state", preWrite || postWrite)
    }

    @Test
    fun csv_export_is_one_complete_snapshot_while_a_write_is_in_flight() = runTest {
        val ledger = RoomPocketLedger(database, clock, zone)
        val (periodId, pocketId) = seedVersion(database, ledger, 10_000, "Before")
        assertEquals(
            LedgerResult.Success,
            ledger.execute(
                LedgerCommand.AddMovement(
                    id = "csv-snapshot",
                    pocketId = pocketId,
                    type = MovementType.EXPENSE,
                    accountingAmountMinor = 1_000,
                    occurredAtUtcMillis = clock.millis(),
                    localDate = LocalDate.of(2026, 2, 26),
                )
            ),
        )

        val csv = readDuringPausedWrite("select * from periods", write = {
            val dao = writerDatabase.financeDao()
            dao.updatePeriod(dao.period(periodId)!!.copy(accountingCurrencyCode = "USD"))
            dao.putPocket(dao.pockets().single { it.id == pocketId }.copy(name = "After"))
        }) {
            ledger.exportCsv().decodeToString()
        }

        val row = csv.lineSequence().single { it.startsWith("\"csv-snapshot\"") }
        val preWrite = row.contains(",\"Before\",\"10.00\",\"SAR\",\"SAR\",")
        val postWrite = row.contains(",\"After\",\"10.00\",\"USD\",\"SAR\",")
        assertTrue("Expected a complete pre-write or post-write CSV row: $row", preWrite || postWrite)
    }

    private suspend fun seedVersion(
        targetDatabase: FinanceDatabase,
        ledger: RoomPocketLedger,
        newFundsMinor: Long,
        pocketName: String,
    ): Pair<String, String> {
        assertEquals(LedgerResult.Success, ledger.execute(LedgerCommand.Initialize(newFundsMinor)))
        val state = ledger.state.first { !it.needsOnboarding }
        val periodId = state.currentPeriod!!.id
        val pocketId = state.pockets.first().pocket.id
        val dao = targetDatabase.financeDao()
        dao.putPocket(dao.pockets().single { it.id == pocketId }.copy(name = pocketName))
        return periodId to pocketId
    }

    private fun assertCompleteVersion(
        state: LedgerState,
        pocketId: String,
        vararg versions: Pair<Int, String>,
    ) {
        val actual = state.newFundsMinor.toInt() to state.pockets.single { it.pocket.id == pocketId }.pocket.name
        assertTrue("Expected one complete snapshot but found $actual", actual in versions)
    }

    private suspend fun <T> TestScope.readDuringPausedWrite(
        secondReadFragment: String,
        rollbackAfterPause: Boolean = false,
        cancelAfterPause: Boolean = false,
        write: suspend () -> Unit,
        read: suspend () -> T,
    ): T {
        require(!rollbackAfterPause || !cancelAfterPause)
        val writerPaused = CountDownLatch(1)
        val allowWriter = CompletableDeferred<Unit>()
        val writerDone = CountDownLatch(1)
        val writer = backgroundScope.launch(Dispatchers.IO) {
            try {
                try {
                    writerDatabase.withTransaction {
                        write()
                        writerPaused.countDown()
                        allowWriter.await()
                        if (rollbackAfterPause) throw ExpectedRollbackException()
                    }
                } catch (error: ExpectedRollbackException) {
                    check(rollbackAfterPause)
                }
            } finally {
                writerDone.countDown()
            }
        }
        assertTrue(
            "Writer did not reach its in-flight pause",
            withContext(Dispatchers.IO) { writerPaused.await(10, TimeUnit.SECONDS) },
        )
        queryGate.arm(
            secondReadFragment,
            releaseWriter = { if (cancelAfterPause) writer.cancel() else allowWriter.complete(Unit) },
            writerDone = writerDone,
        )
        return try {
            read()
        } finally {
            if (cancelAfterPause) writer.cancel() else allowWriter.complete(Unit)
            writer.join()
            queryGate.disarm()
        }
    }

    private class ExpectedRollbackException : RuntimeException()

    private class InFlightWriteQueryGate : RoomDatabase.QueryCallback {
        private val active = AtomicReference<Gate?>()

        fun arm(secondReadFragment: String, releaseWriter: () -> Unit, writerDone: CountDownLatch) {
            check(active.compareAndSet(null, Gate(secondReadFragment, releaseWriter, writerDone)))
        }

        fun disarm() {
            active.set(null)
        }

        override fun onQuery(sqlQuery: String, bindArgs: List<Any?>) {
            val gate = active.get() ?: return
            val sql = sqlQuery.lowercase()
            if ((sql.startsWith("begin ") || sql.contains(gate.secondReadFragment)) && gate.triggered.compareAndSet(false, true)) {
                gate.releaseWriter()
                check(gate.writerDone.await(10, TimeUnit.SECONDS)) { "In-flight writer did not finish" }
            }
        }

        private data class Gate(
            val secondReadFragment: String,
            val releaseWriter: () -> Unit,
            val writerDone: CountDownLatch,
            val triggered: AtomicBoolean = AtomicBoolean(),
        )
    }
}
