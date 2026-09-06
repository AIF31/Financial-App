package com.aif31.pocket.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.aif31.pocket.data.FinanceDatabase
import com.aif31.pocket.data.LedgerCommand
import com.aif31.pocket.data.LedgerResult
import com.aif31.pocket.data.MovementType
import com.aif31.pocket.data.RoomPocketLedger
import com.aif31.pocket.domain.SupportedCurrency
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
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
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationSuggestionLedgerTest {
    private lateinit var database: FinanceDatabase
    private val instant = Instant.parse("2026-09-05T12:00:00Z")
    private val fixedClock = Clock.fixed(instant, ZoneOffset.UTC)

    @Before fun setUp() {
        database = FinanceDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
    }

    @After fun tearDown() = database.close()

    @Test fun updates_share_identity_and_confirmation_creates_exactly_one_movement() = runTest {
        val ledger = RoomPocketLedger(database, fixedClock)
        assertEquals(LedgerResult.Success, ledger.execute(LedgerCommand.Initialize(100_000)))
        val store = NotificationSuggestionStore(database, fixedClock)
        store.ingest("example.payments", "notification-7", instant.toEpochMilli(), ParsedPayment(1200, SupportedCurrency.SAR, "First"))
        store.ingest("example.payments", "notification-7", instant.toEpochMilli() + 1, ParsedPayment(2500, SupportedCurrency.SAR, "Updated"))
        val state = ledger.state.first { it.movementSuggestions.isNotEmpty() }
        assertEquals(1, state.movementSuggestions.size)
        assertEquals(2500L, state.movementSuggestions.single().amountMinor)
        val suggestion = state.movementSuggestions.single()
        val movement = LedgerCommand.AddMovement(
            pocketId = state.pockets.first().pocket.id,
            type = MovementType.EXPENSE,
            accountingAmountMinor = suggestion.amountMinor,
            occurredAtUtcMillis = instant.toEpochMilli(),
            localDate = LocalDate.of(2026, 9, 5),
            originalCurrencyCode = "SAR",
        )
        assertEquals(LedgerResult.Success, ledger.execute(LedgerCommand.ConfirmSuggestion(suggestion.id, movement)))
        assertTrue(ledger.execute(LedgerCommand.ConfirmSuggestion(suggestion.id, movement)) is LedgerResult.Rejected)
        assertEquals(1, database.financeDao().movements().size)
        val tombstone = database.financeDao().movementSuggestion(suggestion.id)!!
        assertEquals("CONFIRMED", tombstone.status)
        assertNull(tombstone.amountMinor)
        assertNull(tombstone.currencyCode)
        assertNull(tombstone.effectiveAtUtcMillis)
        assertNull(tombstone.sourcePackage)
        assertNull(tombstone.merchant)
    }

    @Test fun rejection_removes_normalized_content_and_keeps_only_expiring_identity() = runTest {
        val ledger = RoomPocketLedger(database, fixedClock)
        ledger.execute(LedgerCommand.Initialize(100_000))
        NotificationSuggestionStore(database, fixedClock).ingest(
            "example.payments", "notification-8", instant.toEpochMilli(), ParsedPayment(1200, SupportedCurrency.SAR, "Shop")
        )
        val id = ledger.state.first { it.movementSuggestions.isNotEmpty() }.movementSuggestions.single().id
        assertEquals(LedgerResult.Success, ledger.execute(LedgerCommand.RejectSuggestion(id)))
        val tombstone = database.financeDao().movementSuggestion(id)!!
        assertEquals("REJECTED", tombstone.status)
        assertNull(tombstone.amountMinor)
        assertNull(tombstone.currencyCode)
        assertNull(tombstone.effectiveAtUtcMillis)
        assertNull(tombstone.sourcePackage)
        assertNull(tombstone.merchant)
    }

    @Test fun rejected_identity_cannot_reappear_before_expiry() = runTest {
        val ledger = RoomPocketLedger(database, fixedClock)
        ledger.execute(LedgerCommand.Initialize(100_000))
        val store = NotificationSuggestionStore(database, fixedClock)
        store.ingest(
            "example.payments", "rejected", instant.toEpochMilli(), ParsedPayment(1_200, SupportedCurrency.SAR, "First")
        )
        val id = ledger.state.first { it.movementSuggestions.isNotEmpty() }.movementSuggestions.single().id
        assertEquals(LedgerResult.Success, ledger.execute(LedgerCommand.RejectSuggestion(id)))

        store.ingest(
            "example.payments", "rejected", instant.toEpochMilli() + 1, ParsedPayment(2_500, SupportedCurrency.USD, "Second")
        )

        assertTrue(database.financeDao().observeMovementSuggestions().first().isEmpty())
        val tombstone = database.financeDao().movementSuggestion(id)!!
        assertEquals("REJECTED", tombstone.status)
        assertNull(tombstone.amountMinor)
        assertNull(tombstone.currencyCode)
        assertNull(tombstone.merchant)
    }

    @Test fun different_notification_identities_with_the_same_amount_remain_distinct() = runTest {
        val store = NotificationSuggestionStore(database, fixedClock)
        store.ingest(
            "example.payments", "first", instant.toEpochMilli(), ParsedPayment(1_200, SupportedCurrency.SAR, "First")
        )
        store.ingest(
            "example.payments", "second", instant.toEpochMilli(), ParsedPayment(1_200, SupportedCurrency.SAR, "Second")
        )

        val suggestions = database.financeDao().observeMovementSuggestions().first()
        assertEquals(2, suggestions.size)
        assertEquals(2, suggestions.map { it.identityHash }.toSet().size)
        assertEquals(setOf("First", "Second"), suggestions.map { it.merchant }.toSet())
    }

    @Test fun non_allowlisted_packages_never_reach_persistence() = runTest {
        val hostile = object : CharSequence {
            override val length: Int = 1
            override fun get(index: Int): Char = 'x'
            override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = this
            override fun toString(): String = error("content was accessed before allowlist check")
        }
        val captured = NotificationCapture(NotificationSuggestionStore(database, fixedClock)).ingest(
            allowedPackages = setOf("selected.app"),
            sourcePackage = "other.app",
            notificationIdentity = "notification-9",
            postedAtUtcMillis = instant.toEpochMilli(),
            title = hostile,
            text = hostile,
        )

        assertEquals(false, captured)
        assertTrue(database.financeDao().observeMovementSuggestions().first().isEmpty())
    }

    @Test fun expired_suggestions_cannot_be_confirmed_or_rejected() = runTest {
        val clock = MutableClock(instant)
        val ledger = RoomPocketLedger(database, clock)
        ledger.execute(LedgerCommand.Initialize(100_000))
        NotificationSuggestionStore(database, clock).ingest(
            "example.payments", "expired", instant.toEpochMilli(), ParsedPayment(1_200, SupportedCurrency.SAR, null)
        )
        val id = ledger.state.first { it.movementSuggestions.isNotEmpty() }.movementSuggestions.single().id
        val pocketId = ledger.state.first().pockets.first().pocket.id
        val movement = LedgerCommand.AddMovement(
            pocketId = pocketId,
            type = MovementType.EXPENSE,
            accountingAmountMinor = 1_200,
            occurredAtUtcMillis = instant.toEpochMilli(),
            localDate = LocalDate.of(2026, 9, 5),
        )

        clock.advance(Duration.ofDays(31))

        assertTrue(ledger.execute(LedgerCommand.ConfirmSuggestion(id, movement)) is LedgerResult.Rejected)
        assertTrue(ledger.execute(LedgerCommand.RejectSuggestion(id)) is LedgerResult.Rejected)
        assertTrue(database.financeDao().movements().isEmpty())
    }

    @Test fun catch_up_deletes_suggestions_at_the_exact_thirty_day_expiry() = runTest {
        val clock = MutableClock(instant)
        val ledger = RoomPocketLedger(database, clock)
        ledger.execute(LedgerCommand.Initialize(100_000))
        val store = NotificationSuggestionStore(database, clock)
        store.ingest(
            "example.payments", "expires-on-catch-up", clock.millis(), ParsedPayment(1_200, SupportedCurrency.SAR, null)
        )
        val id = database.financeDao().observeMovementSuggestions().first().single().identityHash

        clock.advance(Duration.ofDays(30))
        assertEquals(LedgerResult.Success, ledger.execute(LedgerCommand.CatchUpPeriods(preferredStartDay = 25)))

        assertNull(database.financeDao().movementSuggestion(id))
        assertTrue(database.financeDao().observeMovementSuggestions().first().isEmpty())
    }

    @Test fun notification_updates_do_not_extend_the_original_expiry() = runTest {
        val clock = MutableClock(instant)
        val store = NotificationSuggestionStore(database, clock)
        store.ingest(
            "example.payments", "updated", clock.millis(), ParsedPayment(1_200, SupportedCurrency.SAR, null)
        )
        val id = database.financeDao().observeMovementSuggestions().first().single().identityHash
        val originalExpiry = database.financeDao().movementSuggestion(id)!!.expiresAtUtcMillis

        clock.advance(Duration.ofDays(29))
        store.ingest(
            "example.payments", "updated", clock.millis(), ParsedPayment(2_500, SupportedCurrency.SAR, null)
        )

        assertEquals(originalExpiry, database.financeDao().movementSuggestion(id)!!.expiresAtUtcMillis)
        assertEquals(2_500L, database.financeDao().movementSuggestion(id)!!.amountMinor)
    }

    @Test fun backup_excludes_suggestion_content_and_restore_clears_the_inbox() = runTest {
        val ledger = RoomPocketLedger(database, fixedClock)
        ledger.execute(LedgerCommand.Initialize(100_000))
        NotificationSuggestionStore(database, fixedClock).ingest(
            "private.source.package",
            "private-notification-identity",
            instant.toEpochMilli(),
            ParsedPayment(1_200, SupportedCurrency.SAR, "Private Merchant"),
        )
        val suggestionId = ledger.state.first { it.movementSuggestions.isNotEmpty() }.movementSuggestions.single().id

        val backup = ledger.exportBackup()
        val backupText = backup.decodeToString()

        assertFalse(backupText.contains("private.source.package"))
        assertFalse(backupText.contains("Private Merchant"))
        assertFalse(backupText.contains(suggestionId))
        assertEquals(LedgerResult.Success, ledger.restoreBackup(backup))
        assertTrue(database.financeDao().observeMovementSuggestions().first().isEmpty())
    }

    @Test fun failed_confirmation_preserves_the_pending_suggestion_and_creates_no_movement() = runTest {
        val ledger = RoomPocketLedger(database, fixedClock)
        ledger.execute(LedgerCommand.Initialize(100_000))
        NotificationSuggestionStore(database, fixedClock).ingest(
            "example.payments", "failed-confirmation", instant.toEpochMilli(),
            ParsedPayment(1_200, SupportedCurrency.SAR, "Shop"),
        )
        val suggestion = ledger.state.first { it.movementSuggestions.isNotEmpty() }.movementSuggestions.single()

        val result = ledger.execute(
            LedgerCommand.ConfirmSuggestion(
                suggestion.id,
                LedgerCommand.AddMovement(
                    pocketId = "missing-pocket",
                    type = MovementType.EXPENSE,
                    accountingAmountMinor = suggestion.amountMinor,
                    occurredAtUtcMillis = instant.toEpochMilli(),
                    localDate = LocalDate.of(2026, 9, 5),
                    originalCurrencyCode = suggestion.currency.name,
                ),
            )
        )

        assertTrue(result is LedgerResult.Rejected)
        assertTrue(database.financeDao().movements().isEmpty())
        val pending = database.financeDao().movementSuggestion(suggestion.id)!!
        assertEquals("PENDING", pending.status)
        assertEquals(1_200L, pending.amountMinor)
        assertEquals("SAR", pending.currencyCode)
        assertEquals("Shop", pending.merchant)
    }

    @Test fun reused_notification_identity_after_tombstone_expiry_creates_a_fresh_movement() = runTest {
        val clock = MutableClock(instant)
        val ledger = RoomPocketLedger(database, clock)
        ledger.execute(LedgerCommand.Initialize(100_000))
        val store = NotificationSuggestionStore(database, clock)
        val pocketId = ledger.state.first().pockets.first().pocket.id
        suspend fun ingest(amount: Long) {
            store.ingest(
                "example.payments", "reused", clock.millis(), ParsedPayment(amount, SupportedCurrency.SAR, null)
            )
        }
        fun movement(id: String?, amount: Long) = LedgerCommand.AddMovement(
            id = id,
            pocketId = pocketId,
            type = MovementType.EXPENSE,
            accountingAmountMinor = amount,
            occurredAtUtcMillis = instant.toEpochMilli(),
            localDate = LocalDate.of(2026, 9, 5),
        )

        ingest(1_200)
        val firstSuggestion = ledger.state.first { it.movementSuggestions.isNotEmpty() }.movementSuggestions.single()
        assertEquals(LedgerResult.Success, ledger.execute(LedgerCommand.ConfirmSuggestion(firstSuggestion.id, movement("caller-id", 1_200))))
        val firstMovement = database.financeDao().movements().single()
        assertTrue(firstMovement.id != "caller-id")

        clock.advance(Duration.ofDays(31))
        ingest(2_500)
        val secondSuggestion = ledger.state.first { it.movementSuggestions.isNotEmpty() }.movementSuggestions.single()
        assertEquals(
            LedgerResult.Success,
            ledger.execute(LedgerCommand.ConfirmSuggestion(secondSuggestion.id, movement(firstMovement.id, 2_500))),
        )

        val movements = database.financeDao().movements()
        assertEquals(2, movements.size)
        assertEquals(setOf(1_200L, 2_500L), movements.map { it.accountingAmountMinor }.toSet())
        assertEquals(2, movements.map { it.id }.toSet().size)
    }

    @Test fun confirmation_metrics_use_original_values_and_fail_best_effort() = runTest {
        val corrections = mutableListOf<Pair<Boolean, Boolean>>()
        val ledger = RoomPocketLedger(
            database = database,
            clock = fixedClock,
            recordNotificationConfirmation = { amount, currency ->
                corrections += amount to currency
                error("metrics unavailable")
            },
        )
        assertEquals(LedgerResult.Success, ledger.execute(LedgerCommand.Initialize(100_000)))
        NotificationSuggestionStore(database, fixedClock).ingest(
            "example.payments", "notification-10", instant.toEpochMilli(), ParsedPayment(1_200, SupportedCurrency.SAR, null)
        )
        val suggestion = ledger.state.first { it.movementSuggestions.isNotEmpty() }.movementSuggestions.single()

        val result = ledger.execute(
            LedgerCommand.ConfirmSuggestion(
                suggestion.id,
                LedgerCommand.AddMovement(
                    pocketId = ledger.state.first().pockets.first().pocket.id,
                    type = MovementType.EXPENSE,
                    accountingAmountMinor = 600,
                    occurredAtUtcMillis = instant.toEpochMilli(),
                    localDate = LocalDate.of(2026, 9, 5),
                    originalAmountMinor = 1_300,
                    originalCurrencyCode = "USD",
                ),
            )
        )

        assertEquals(LedgerResult.Success, result)
        assertEquals(listOf(true to true), corrections)
        assertEquals(1, database.financeDao().movements().size)

        NotificationSuggestionStore(database, fixedClock).ingest(
            "example.payments", "notification-11", instant.toEpochMilli(), ParsedPayment(1_200, SupportedCurrency.SAR, null)
        )
        val unchanged = ledger.state.first { it.movementSuggestions.isNotEmpty() }.movementSuggestions.single()
        assertEquals(
            LedgerResult.Success,
            ledger.execute(
                LedgerCommand.ConfirmSuggestion(
                    unchanged.id,
                    LedgerCommand.AddMovement(
                        pocketId = ledger.state.first().pockets.first().pocket.id,
                        type = MovementType.EXPENSE,
                        accountingAmountMinor = 1_200,
                        occurredAtUtcMillis = instant.toEpochMilli(),
                        localDate = LocalDate.of(2026, 9, 5),
                        originalCurrencyCode = "SAR",
                    ),
                )
            ),
        )
        assertEquals(listOf(true to true, false to false), corrections)
        assertEquals(2, database.financeDao().movements().size)
    }

    private class MutableClock(private var current: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = current
        fun advance(duration: Duration) { current = current.plus(duration) }
    }
}
