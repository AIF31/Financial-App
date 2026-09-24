package com.aif31.pocket

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aif31.pocket.data.*
import com.aif31.pocket.domain.SupportedCurrency
import com.aif31.pocket.fx.*
import java.time.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
@OptIn(ExperimentalTestApi::class)
class ExpenseWorkflowHostTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var database: FinanceDatabase
    private lateinit var ledger: RoomPocketLedger
    private lateinit var state: LedgerState
    private val date = LocalDate.of(2026, 2, 26)

    @Before fun setup() = runBlocking {
        database = FinanceDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
        val zone = ZoneId.of("Asia/Riyadh")
        ledger = RoomPocketLedger(database, Clock.fixed(Instant.parse("2026-02-26T09:00:00Z"), zone), zone)
        ledger.execute(LedgerCommand.Initialize(100_000))
        state = ledger.state.first()
    }
    @After fun close() { database.close() }

    @Test fun delayed_save_disables_double_tap_and_retry_keeps_the_draft_identity() {
        val requests = mutableListOf<LedgerCommand.AddMovement>()
        var response = CompletableDeferred<LedgerResult>()
        val delayedLedger = object : PocketLedger by ledger {
            override suspend fun execute(command: LedgerCommand): LedgerResult {
                requests += command as LedgerCommand.AddMovement
                val result = response.await()
                return if (result == LedgerResult.Success) ledger.execute(command) else result
            }
        }
        var saved = 0
        compose.setContent {
            ProductionMovementScreen(state, delayedLedger, {}, { saved++ }, ledger.movementDefaults())
        }
        compose.onNodeWithTag("movement_amount").performTextInput("12.50")
        compose.onNodeWithTag("movement_pocket_Supermercado").performClick()
        compose.onNodeWithTag("movement_save").performClick()
        compose.onNodeWithTag("movement_save").assertIsNotEnabled().assertTextEquals("Guardando…")
        assertEquals(1, requests.size)
        compose.runOnIdle { response.complete(LedgerResult.Rejected("Inténtalo otra vez")) }
        compose.onNodeWithTag("movement_form").performScrollToNode(hasText("Inténtalo otra vez"))
        compose.onNodeWithText("Inténtalo otra vez").assertIsDisplayed()
        compose.onNodeWithTag("movement_amount").assertTextContains("12.50")
        assertEquals(0, saved)
        assertTrue(runBlocking { ledger.state.first().movements.isEmpty() })
        response = CompletableDeferred()
        compose.onNodeWithTag("movement_save").performClick()
        compose.onNodeWithTag("movement_save").assertIsNotEnabled()
        assertEquals(2, requests.size)
        assertEquals(requests[0].id, requests[1].id)
        assertTrue(requests.all { it.createOnly })
        compose.runOnIdle { response.complete(LedgerResult.Success) }
        compose.waitUntil(5_000) { saved == 1 }
        val movement = runBlocking { ledger.state.first().movements.single() }
        assertEquals(requests[1].pocketId, movement.pocketId)
        assertEquals(requests[1].localDate, movement.localDate)
        assertEquals(1_250L, movement.accountingAmountMinor)
        assertEquals(ConversionStatus.CONFIRMED, movement.conversionStatus)
        assertEquals(requests[1].paymentMethodId, movement.paymentMethodId)
    }

    @Test fun interrupted_save_retains_draft_and_id_for_safe_retry() {
        val requests = mutableListOf<LedgerCommand.AddMovement>()
        var response = CompletableDeferred<LedgerResult>()
        val delayedLedger = object : PocketLedger by ledger {
            override suspend fun execute(command: LedgerCommand): LedgerResult {
                requests += command as LedgerCommand.AddMovement
                val result = response.await()
                return if (result == LedgerResult.Success) ledger.execute(command) else result
            }
        }
        var saved = 0
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            ProductionMovementScreen(state, delayedLedger, {}, { saved++ }, ledger.movementDefaults())
        }
        compose.onNodeWithTag("movement_amount").performTextInput("12.50")
        compose.onNodeWithTag("movement_pocket_Supermercado").performClick()
        compose.onNodeWithTag("movement_save").performClick()
        assertEquals(1, requests.size)
        val firstResponse = response

        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("movement_amount").assertTextContains("12.50")
        compose.onNodeWithTag("movement_form").performScrollToNode(hasText("No se confirmó el guardado. Reintenta con este borrador."))
        response = CompletableDeferred(LedgerResult.Success)
        compose.onNodeWithTag("movement_save").performClick()
        compose.waitUntil(5_000) { saved == 1 }
        assertEquals(2, requests.size)
        assertEquals(requests[0].id, requests[1].id)
        firstResponse.complete(LedgerResult.Success)
        compose.waitForIdle()
        assertEquals(1, saved)
        assertEquals(1, runBlocking { ledger.state.first().movements.size })
    }

    @Test fun loading_foreign_quote_blocks_save_and_switching_to_same_currency_cancels_it() {
        val pending = CompletableDeferred<FxQuote>()
        val repository = object : ExchangeRateRepository {
            override suspend fun quote(requestedDate: LocalDate, base: SupportedCurrency, quote: SupportedCurrency, forceRefresh: Boolean) = pending.await()
        }
        compose.setContent {
            ProductionMovementScreen(state, ledger, {}, {}, ledger.movementDefaults(),
                defaultExpenseCurrency = SupportedCurrency.USD, onlineFxEnabled = true, exchangeRates = repository)
        }
        compose.onNodeWithTag("movement_amount").performTextInput("10.00")
        compose.onNodeWithTag("movement_pocket_Supermercado").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntilExactlyOneExists(hasText("Consultando tipo de cambio…"), 5_000)
        compose.onNodeWithTag("movement_save").assertIsNotEnabled()
        compose.onNodeWithTag("movement_currency_SAR").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntilExactlyOneExists(hasText("Guardar gasto · SAR 10.00"), 5_000)
        pending.complete(FxQuote(date, date, SupportedCurrency.USD, SupportedCurrency.SAR, "3.75", "LATE"))
        compose.waitForIdle()
        compose.onNodeWithTag("movement_save").assertIsEnabled().assertTextContains("SAR 10.00", substring = true)
        compose.onNodeWithText("Fuente: LATE").assertDoesNotExist()
    }

    @Test fun provider_failure_blocks_foreign_save_but_allows_offline_recovery() {
        val repository = object : ExchangeRateRepository {
            override suspend fun quote(requestedDate: LocalDate, base: SupportedCurrency, quote: SupportedCurrency, forceRefresh: Boolean): FxQuote = throw QuoteFailure.Unavailable()
        }
        compose.setContent {
            ProductionMovementScreen(state, ledger, {}, {}, ledger.movementDefaults(),
                defaultExpenseCurrency = SupportedCurrency.MXN, onlineFxEnabled = true, exchangeRates = repository)
        }
        compose.onNodeWithTag("movement_amount").performTextInput("10.00")
        compose.onNodeWithTag("movement_pocket_Supermercado").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntilExactlyOneExists(hasText("No hay un tipo de cambio disponible para esa fecha"), 5_000)
        compose.onNodeWithTag("movement_save").assertIsNotEnabled()
        compose.onNodeWithTag("movement_currency_SAR").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntilExactlyOneExists(hasText("Guardar gasto · SAR 10.00"), 5_000)
        compose.onNodeWithTag("movement_save").assertIsEnabled().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(5_000) { runBlocking { ledger.state.first().movements.size == 1 } }
        assertEquals(1_000L, runBlocking { ledger.state.first().movements.single().accountingAmountMinor })
    }

    @Test fun comma_decimal_is_saved_and_a_negative_sign_is_never_discarded() {
        compose.setContent {
            ProductionMovementScreen(state, ledger, {}, {}, ledger.movementDefaults())
        }
        compose.onNodeWithTag("movement_amount").performTextInput("12,50")
        compose.onNodeWithTag("movement_pocket_Supermercado").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntilExactlyOneExists(hasText("Guardar gasto · SAR 12.50"), 5_000)
        compose.onNodeWithTag("movement_save").assertIsEnabled()

        compose.onNodeWithTag("movement_amount").performTextReplacement("-12,50")

        compose.onNodeWithTag("movement_amount").assertTextContains("-12,50")
        compose.onNodeWithText("Escribe un importe válido").assertIsDisplayed()
        compose.onNodeWithTag("movement_save").assertIsNotEnabled()

        compose.onNodeWithTag("movement_amount").performTextReplacement("12.50")
        compose.onNodeWithTag("movement_save").assertIsEnabled().performClick()
        compose.waitUntil(5_000) { runBlocking { ledger.state.first().movements.size == 1 } }
        assertEquals(1_250L, runBlocking { ledger.state.first().movements.single().accountingAmountMinor })
    }

    @Test fun applying_a_matching_template_requotes_instead_of_reusing_the_saved_conversion() {
        runBlocking {
            ledger.execute(LedgerCommand.AddMovement(
                id = "saved", pocketId = state.pockets.first().pocket.id, type = MovementType.EXPENSE,
                accountingAmountMinor = 401, occurredAtUtcMillis = ledger.movementDefaults().instantMillis,
                localDate = date, originalAmountMinor = 100, originalCurrencyCode = "USD",
            ))
            ledger.execute(LedgerCommand.UpsertTemplate(
                name = "Same inputs", amountMinor = 100, pocketId = state.pockets.first().pocket.id,
                inputCurrency = SupportedCurrency.USD,
            ))
            state = ledger.state.first()
        }
        val repository = object : ExchangeRateRepository {
            override suspend fun quote(requestedDate: LocalDate, base: SupportedCurrency, quote: SupportedCurrency, forceRefresh: Boolean) =
                FxQuote(requestedDate, requestedDate, base, quote, "2", "NEW_TEMPLATE_QUOTE")
        }
        compose.setContent {
            ProductionMovementScreen(state, ledger, {}, {}, ledger.movementDefaults(),
                initialMovement = state.movements.single(), onlineFxEnabled = true, exchangeRates = repository)
        }
        compose.waitUntilExactlyOneExists(hasText("Fuente: Conversión manual histórica"), 5_000)
        compose.onNodeWithTag("movement_form").performScrollToNode(hasText("Same inputs"))
        compose.onNodeWithText("Same inputs").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntilExactlyOneExists(hasText("Fuente: NEW_TEMPLATE_QUOTE"), 5_000)
        compose.onNodeWithTag("movement_save").assertIsEnabled().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(5_000) {
            runBlocking { ledger.state.first().movements.single().conversionSource == "NEW_TEMPLATE_QUOTE" }
        }
    }

    @Test fun metadata_only_edit_preserves_a_legacy_manual_conversion_offline() {
        runBlocking {
            assertEquals(LedgerResult.Success, ledger.execute(LedgerCommand.AddMovement(
                id = "legacy", pocketId = state.pockets.first().pocket.id, type = MovementType.EXPENSE,
                accountingAmountMinor = 401, occurredAtUtcMillis = ledger.movementDefaults().instantMillis,
                localDate = date, originalAmountMinor = 100, originalCurrencyCode = "USD",
                conversionStatus = ConversionStatus.ESTIMATED,
            )))
        }
        val initial = runBlocking { ledger.state.first().movements.single() }
        var saved = false
        compose.setContent {
            ProductionMovementScreen(state, ledger, {}, { saved = true }, ledger.movementDefaults(), initialMovement = initial)
        }
        compose.onNodeWithTag("movement_form").performScrollToNode(hasText("Comercio (opcional)"))
        compose.onNodeWithText("Comercio (opcional)").performTextInput("Edited")
        compose.onNodeWithTag("movement_save").assertIsEnabled().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(5_000) { saved }
        val edited = runBlocking { ledger.state.first().movements.single() }
        assertEquals(initial.copy(merchant = "Edited"), edited)
    }
}
