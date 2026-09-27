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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationAutoRecorderTest {
    private lateinit var database: FinanceDatabase
    private val instant = Instant.parse("2026-09-05T12:00:00Z")
    private val clock = Clock.fixed(instant, ZoneOffset.UTC)
    private val zone = ZoneId.of("Asia/Riyadh")
    private val confirmations = mutableListOf<Pair<Boolean, Boolean>>()
    private lateinit var ledger: RoomPocketLedger
    private lateinit var store: NotificationSuggestionStore

    @Before fun setUp() = runTest {
        database = FinanceDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
        ledger = RoomPocketLedger(database, clock, zone, recordNotificationConfirmation = { a, c -> confirmations += a to c })
        store = NotificationSuggestionStore(database, clock)
        assertEquals(LedgerResult.Success, ledger.execute(LedgerCommand.Initialize(100_000)))
    }

    @After fun tearDown() = database.close()

    private suspend fun recordManually(pocketId: String, merchant: String, minutesAgo: Long = 1) {
        assertEquals(
            LedgerResult.Success,
            ledger.execute(
                LedgerCommand.AddMovement(
                    pocketId = pocketId,
                    type = MovementType.EXPENSE,
                    accountingAmountMinor = 500,
                    occurredAtUtcMillis = instant.toEpochMilli() - minutesAgo * 60_000,
                    localDate = LocalDate.of(2026, 9, 5),
                    merchant = merchant,
                )
            ),
        )
    }

    private suspend fun detect(identity: String, payment: ParsedPayment) =
        store.ingest("bank.app", identity, instant.toEpochMilli(), payment).suggestionId

    @Test fun known_merchant_is_recorded_in_its_last_pocket_without_touching_beta_metrics() = runTest {
        val pockets = ledger.state.first().pockets.map { it.pocket }
        val remembered = pockets[1]
        recordManually(pockets[0].id, "Tamimi Global", minutesAgo = 30)
        recordManually(remembered.id, "TAMIMI GLOBAL")
        val suggestionId = detect("one", ParsedPayment(2_305, SupportedCurrency.SAR, "Tamimi×Global"))

        val outcome = NotificationAutoRecorder(ledger, zone).record(suggestionId)

        assertEquals(AutoRecordOutcome.Recorded(autoRecordedMovementId(suggestionId), remembered.name), outcome)
        val state = ledger.state.first()
        assertTrue(state.movementSuggestions.isEmpty())
        val movement = state.movements.single { isAutoRecordedMovement(it.id) }
        assertEquals(remembered.id, movement.pocketId)
        assertEquals(2_305L, movement.accountingAmountMinor)
        assertTrue(confirmations.isEmpty())
    }

    @Test fun an_auto_recorded_movement_is_identified_by_its_full_suggestion_identity() = runTest {
        val pocket = ledger.state.first().pockets.first().pocket
        recordManually(pocket.id, "Corner Shop")
        val suggestionId = detect("full-id", ParsedPayment(1_000, SupportedCurrency.SAR, "Corner Shop"))

        NotificationAutoRecorder(ledger, zone).record(suggestionId)

        assertTrue(ledger.state.first().movements.any { it.id == "ntf-$suggestionId" })
    }

    @Test fun retrying_a_recorded_suggestion_never_creates_a_second_movement() = runTest {
        val pocket = ledger.state.first().pockets.first().pocket
        recordManually(pocket.id, "Corner Shop")
        val suggestionId = detect("retry", ParsedPayment(1_000, SupportedCurrency.SAR, "Corner Shop"))
        val recorder = NotificationAutoRecorder(ledger, zone)

        recorder.record(suggestionId)
        val retry = recorder.record(suggestionId)

        assertEquals(AutoRecordOutcome.NeedsReview(ReviewReason.UNAVAILABLE), retry)
        assertEquals(1, ledger.state.first().movements.count { isAutoRecordedMovement(it.id) })
    }

    @Test fun uncertain_payments_stay_in_the_review_inbox() = runTest {
        val pocket = ledger.state.first().pockets.first().pocket
        recordManually(pocket.id, "Known Shop")
        val recorder = NotificationAutoRecorder(ledger, zone)

        val newMerchant = detect("new", ParsedPayment(1_000, SupportedCurrency.SAR, "Brand New Shop"))
        val noMerchant = detect("none", ParsedPayment(1_000, SupportedCurrency.SAR, null))
        val foreign = detect("foreign", ParsedPayment(73_118, SupportedCurrency.MXN, "Known Shop"))

        assertEquals(AutoRecordOutcome.NeedsReview(ReviewReason.NEW_MERCHANT), recorder.record(newMerchant))
        assertEquals(AutoRecordOutcome.NeedsReview(ReviewReason.NO_MERCHANT), recorder.record(noMerchant))
        assertEquals(AutoRecordOutcome.NeedsReview(ReviewReason.FOREIGN_CURRENCY), recorder.record(foreign))
        assertEquals(3, ledger.state.first().movementSuggestions.size)
        assertTrue(ledger.state.first().movements.none { isAutoRecordedMovement(it.id) })
    }

    @Test fun a_payment_after_the_period_ends_is_recorded_once_the_new_period_is_caught_up() = runTest {
        val pocket = ledger.state.first().pockets.first().pocket
        recordManually(pocket.id, "Corner Cafe")
        val nextPeriodClock = Clock.fixed(Instant.parse("2026-09-26T09:00:00Z"), ZoneOffset.UTC)
        val laterLedger = RoomPocketLedger(database, nextPeriodClock, zone)
        val suggestionId = NotificationSuggestionStore(database, nextPeriodClock)
            .ingest("bank.app", "next-period", nextPeriodClock.millis(), ParsedPayment(1_200, SupportedCurrency.SAR, "Corner Cafe"))
            .suggestionId
        val recorder = NotificationAutoRecorder(laterLedger, zone, catchUpPeriods = {
            laterLedger.execute(LedgerCommand.CatchUpPeriods(preferredStartDay = 25))
        })

        val outcome = recorder.record(suggestionId)

        assertEquals(AutoRecordOutcome.Recorded(autoRecordedMovementId(suggestionId), pocket.name), outcome)
    }

    @Test fun a_foreign_currency_needs_conversion_even_from_a_new_merchant() = runTest {
        val suggestionId = detect("foreign-new", ParsedPayment(10_000, SupportedCurrency.MXN, "OXXO"))

        assertEquals(
            AutoRecordOutcome.NeedsReview(ReviewReason.FOREIGN_CURRENCY),
            NotificationAutoRecorder(ledger, zone).record(suggestionId),
        )
    }

    @Test fun a_newest_expense_in_an_archived_pocket_sends_the_payment_to_review() = runTest {
        val pockets = ledger.state.first().pockets.map { it.pocket }
        recordManually(pockets[0].id, "Corner Cafe", minutesAgo = 30)
        recordManually(pockets[1].id, "Corner Cafe")
        assertEquals(LedgerResult.Success, ledger.execute(LedgerCommand.ArchivePocket(pockets[1].id)))
        val suggestionId = detect("archived-newest", ParsedPayment(1_000, SupportedCurrency.SAR, "Corner Cafe"))

        assertEquals(
            AutoRecordOutcome.NeedsReview(ReviewReason.POCKET_ARCHIVED),
            NotificationAutoRecorder(ledger, zone).record(suggestionId),
        )
    }

    @Test fun merchant_memory_ignores_punctuation_inside_names() = runTest {
        val pocket = ledger.state.first().pockets.first().pocket
        recordManually(pocket.id, "K.F.C")
        val suggestionId = detect("kfc", ParsedPayment(2_500, SupportedCurrency.SAR, "KFC"))

        assertEquals(
            AutoRecordOutcome.Recorded(autoRecordedMovementId(suggestionId), pocket.name),
            NotificationAutoRecorder(ledger, zone).record(suggestionId),
        )
    }

    @Test fun archived_pockets_are_not_reused() = runTest {
        val pocket = ledger.state.first().pockets.first().pocket
        recordManually(pocket.id, "Old Shop")
        assertEquals(LedgerResult.Success, ledger.execute(LedgerCommand.ArchivePocket(pocket.id)))
        val suggestionId = detect("archived", ParsedPayment(1_000, SupportedCurrency.SAR, "Old Shop"))

        assertEquals(
            AutoRecordOutcome.NeedsReview(ReviewReason.POCKET_ARCHIVED),
            NotificationAutoRecorder(ledger, zone).record(suggestionId),
        )
    }
}
