package com.aif31.pocket.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.aif31.pocket.data.FinanceDatabase
import com.aif31.pocket.data.LedgerCommand
import com.aif31.pocket.data.LedgerResult
import com.aif31.pocket.data.MovementType
import com.aif31.pocket.data.PocketLedger
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

    private suspend fun recordManually(pocketId: String, merchant: String, minutesAgo: Long = 1, amountMinor: Long = 500) {
        val occurredAt = instant.minusSeconds(minutesAgo * 60)
        assertEquals(
            LedgerResult.Success,
            ledger.execute(
                LedgerCommand.AddMovement(
                    pocketId = pocketId,
                    type = MovementType.EXPENSE,
                    accountingAmountMinor = amountMinor,
                    occurredAtUtcMillis = occurredAt.toEpochMilli(),
                    localDate = occurredAt.atZone(zone).toLocalDate(),
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

    @Test fun a_payment_already_entered_by_hand_stays_in_review_as_a_possible_duplicate() = runTest {
        val pocket = ledger.state.first().pockets.first().pocket
        recordManually(pocket.id, "Starbucks", minutesAgo = 3, amountMinor = 2_500)
        val suggestionId = detect("dup", ParsedPayment(2_500, SupportedCurrency.SAR, "STARBUCKS"))

        val outcome = NotificationAutoRecorder(ledger, zone).record(suggestionId)

        assertEquals(AutoRecordOutcome.NeedsReview(ReviewReason.POSSIBLE_DUPLICATE), outcome)
        val state = ledger.state.first()
        assertEquals(listOf(suggestionId), state.movementSuggestions.map { it.id })
        assertTrue(state.movements.none { isAutoRecordedMovement(it.id) })
    }

    @Test fun a_hand_entry_dated_shortly_after_the_alert_also_marks_a_possible_duplicate() = runTest {
        val pocket = ledger.state.first().pockets.first().pocket
        recordManually(pocket.id, "Starbucks", minutesAgo = -45, amountMinor = 2_500)
        val suggestionId = detect("dup-later", ParsedPayment(2_500, SupportedCurrency.SAR, "Starbucks"))

        assertEquals(
            AutoRecordOutcome.NeedsReview(ReviewReason.POSSIBLE_DUPLICATE),
            NotificationAutoRecorder(ledger, zone).record(suggestionId),
        )
    }

    @Test fun the_same_purchase_alerted_by_a_second_app_is_not_recorded_twice() = runTest {
        val pocket = ledger.state.first().pockets.first().pocket
        recordManually(pocket.id, "Corner Shop", minutesAgo = 60 * 24 * 3)
        val recorder = NotificationAutoRecorder(ledger, zone)
        val first = detect("sms", ParsedPayment(1_000, SupportedCurrency.SAR, "Corner Shop"))
        val second = store.ingest("bank.other", "push", instant.toEpochMilli() + 30_000, ParsedPayment(1_000, SupportedCurrency.SAR, "Corner Shop"))
            .suggestionId

        assertEquals(AutoRecordOutcome.Recorded(autoRecordedMovementId(first), pocket.name), recorder.record(first))
        assertEquals(AutoRecordOutcome.NeedsReview(ReviewReason.POSSIBLE_DUPLICATE), recorder.record(second))
        assertEquals(1, ledger.state.first().movements.count { isAutoRecordedMovement(it.id) })
    }

    @Test fun the_same_amount_at_the_same_merchant_on_another_day_is_still_recorded() = runTest {
        val pocket = ledger.state.first().pockets.first().pocket
        recordManually(pocket.id, "Starbucks", minutesAgo = 60 * 24, amountMinor = 2_500)
        val suggestionId = detect("daily-coffee", ParsedPayment(2_500, SupportedCurrency.SAR, "Starbucks"))

        assertEquals(
            AutoRecordOutcome.Recorded(autoRecordedMovementId(suggestionId), pocket.name),
            NotificationAutoRecorder(ledger, zone).record(suggestionId),
        )
    }

    @Test fun a_hand_entry_converted_from_another_currency_still_marks_a_possible_duplicate() = runTest {
        // USD 10.00 typed in by hand becomes SAR 37.50; the bank then reports the SAR charge.
        val pocket = ledger.state.first().pockets.first().pocket
        val occurredAt = instant.minusSeconds(180)
        assertEquals(
            LedgerResult.Success,
            ledger.execute(
                LedgerCommand.AddMovement(
                    pocketId = pocket.id,
                    type = MovementType.EXPENSE,
                    accountingAmountMinor = 3_750,
                    occurredAtUtcMillis = occurredAt.toEpochMilli(),
                    localDate = occurredAt.atZone(zone).toLocalDate(),
                    merchant = "Shop",
                    originalAmountMinor = 1_000,
                    originalCurrencyCode = "USD",
                ),
            ),
        )
        val suggestionId = detect("converted", ParsedPayment(3_750, SupportedCurrency.SAR, "SHOP"))

        assertEquals(
            AutoRecordOutcome.NeedsReview(ReviewReason.POSSIBLE_DUPLICATE),
            NotificationAutoRecorder(ledger, zone).record(suggestionId),
        )
    }

    @Test fun a_hand_entry_saved_while_the_payment_is_being_recorded_still_blocks_it() = runTest {
        val pocket = ledger.state.first().pockets.first().pocket
        recordManually(pocket.id, "Starbucks", minutesAgo = 60 * 24 * 3, amountMinor = 900)
        val suggestionId = detect("race", ParsedPayment(2_500, SupportedCurrency.SAR, "Starbucks"))
        // The person saves the same purchase after the recorder's check but before its write.
        val racing = object : PocketLedger by ledger {
            var raced = false
            override suspend fun execute(command: LedgerCommand): LedgerResult {
                if (command is LedgerCommand.ConfirmSuggestion && !raced) {
                    raced = true
                    recordManually(pocket.id, "Starbucks", minutesAgo = 1, amountMinor = 2_500)
                }
                return ledger.execute(command)
            }
        }

        val outcome = NotificationAutoRecorder(racing, zone).record(suggestionId)

        assertEquals(AutoRecordOutcome.NeedsReview(ReviewReason.POSSIBLE_DUPLICATE), outcome)
        val state = ledger.state.first()
        assertEquals(1, state.movements.count { it.merchant == "Starbucks" && it.accountingAmountMinor == 2_500L })
        assertEquals(listOf(suggestionId), state.movementSuggestions.map { it.id })
    }
}
