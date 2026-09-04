package com.aif31.pocket

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import com.aif31.pocket.data.ConversionStatus
import com.aif31.pocket.data.FinanceDatabase
import com.aif31.pocket.data.LedgerCommand
import com.aif31.pocket.data.MovementType
import com.aif31.pocket.data.RoomPocketLedger
import com.aif31.pocket.settings.AppPreferences
import com.aif31.pocket.settings.PreferencesStore
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A deliberately paced visual tour for screen recording and human/AI design review.
 *
 * This is not part of the ordinary regression suite. Run it through
 * scripts/record-ui-ux-tour.ps1 so the matching device recording is captured.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
@OptIn(ExperimentalTestApi::class)
class PocketUiUxReviewTourTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var database: FinanceDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = FinanceDatabase.inMemory(context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun tour_every_screen_and_major_configuration_state() {
        val zone = ZoneId.of("Asia/Riyadh")
        val clock = Clock.fixed(Instant.parse("2026-08-24T09:00:00Z"), zone)
        val ledger = RoomPocketLedger(database, clock, zone)
        val preferences = ReviewPreferences()

        compose.setContent {
            PocketApp(
                ledger = ledger,
                preferences = preferences,
                reminderScheduler = null,
                onCreateBackup = {},
                onCreateCsv = {},
                onPickBackup = {},
                onRequestNotificationPermission = {},
            )
        }

        // Onboarding and privacy promise.
        compose.waitUntilExactlyOneExists(hasText("Configura tu primer periodo"), TIMEOUT)
        pauseForReview()
        compose.onNodeWithTag("new_funds").performTextInput("12500.00")
        compose.onNodeWithTag("start_day").performTextReplacement("25")
        closeSoftKeyboard()
        pauseForReview()
        compose.onNodeWithText("Comenzar").performClick()
        compose.waitUntilExactlyOneExists(hasTestTag("dashboard_list"), TIMEOUT)

        seedRepresentativeData(ledger, clock)

        // Dashboard: scan every metric and representative Pocket states.
        reviewScrollTarget("dashboard_list", "Tu periodo")
        reviewScrollTarget("dashboard_list", "Disponible")
        reviewScrollTarget("dashboard_list", "Supermercado")
        reviewScrollTarget("dashboard_list", "Transporte")
        compose.onNodeWithTag("dashboard_list").performScrollToNode(hasContentDescription("Mostrar métricas del periodo"))
        compose.onNodeWithContentDescription("Mostrar métricas del periodo").assertIsDisplayed()
        compose.onNodeWithContentDescription("Mostrar métricas del periodo").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntilExactlyOneExists(hasText("Ocultar más información"), TIMEOUT)
        reviewScrollTarget("dashboard_list", "Gasto diario promedio")
        reviewScrollTarget("dashboard_list", "Proyección estimada")

        // New movement: cover Pocket choice, currencies, consent/offline recovery, type, payment,
        // date/time, merchant, note, and both dialog actions without mutating review data.
        compose.onNodeWithTag("contextual_add").performClick()
        compose.waitUntilExactlyOneExists(hasText("Nuevo gasto"), TIMEOUT)
        pauseForReview()
        compose.onNodeWithTag("movement_amount").performTextInput("48.75")
        compose.onNodeWithTag("movement_pocket_Supermercado").performScrollTo().performClick()
        scrollMovementTo("Más detalles")
        compose.onNodeWithText("Más detalles").performClick()
        compose.onNodeWithTag("movement_form").performScrollToNode(hasTestTag("movement_currency_USD"))
        compose.onNodeWithTag("movement_currency_USD").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag("movement_currency_USD").assertTextContains("✓ USD")
        scrollMovementTo("Activa la conversión en línea")
        pauseForReview()
        compose.onNodeWithText("Activa la conversión en línea").assertIsDisplayed()
        compose.onNodeWithTag("movement_save").assertIsNotEnabled()
        compose.onNodeWithTag("movement_currency_SAR").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntilExactlyOneExists(hasText("Guardar gasto · SAR 48.75"), TIMEOUT)
        scrollMovementTo("Devolución")
        compose.onNodeWithText("Devolución").performClick()
        pauseForReview()
        scrollMovementTo("✓ Tarjeta")
        compose.onNodeWithText("✓ Tarjeta").performClick()
        scrollMovementTo("Fecha (AAAA-MM-DD)")
        compose.onNodeWithText("Fecha (AAAA-MM-DD)").assertIsDisplayed()
        pauseForReview()
        scrollMovementTo("Comercio (opcional)")
        compose.onNodeWithText("Comercio (opcional)").assertIsDisplayed()
        scrollMovementTo("Nota (opcional)")
        compose.onNodeWithText("Nota (opcional)").assertIsDisplayed()
        pauseForReview()
        compose.onNodeWithContentDescription("Cerrar").performClick()

        // Movement history: all filter families, search, detail, and edit dialog.
        compose.onNodeWithText("Movimientos").performClick()
        compose.waitUntilExactlyOneExists(hasTestTag("history_search"), TIMEOUT)
        pauseForReview()
        listOf("filter_period", "filter_pocket", "filter_currency", "filter_method").forEach { tag ->
            compose.onNodeWithTag("history_filters").performScrollToNode(hasTestTag(tag))
            compose.onNodeWithTag(tag).performClick()
            compose.onNodeWithTag("${tag}_option_1").performClick()
            pauseForReview(SHORT_PAUSE)
            compose.onNodeWithTag("clear_filters").performClick()
        }
        compose.onNodeWithTag("history_search").performTextInput("Tamimi")
        closeSoftKeyboard()
        pauseForReview()
        compose.onNodeWithTag("history_search").performTextClearance()
        compose.onNodeWithText("Inicio").performClick()
        compose.onNodeWithText("Movimientos").performClick()
        compose.waitUntilExactlyOneExists(hasText("Tamimi Market"), TIMEOUT)
        compose.onNodeWithText("Tamimi Market").performClick()
        pauseForReview()
        compose.onNodeWithText("Editar").performClick()
        compose.waitUntilExactlyOneExists(hasText("Guardar cambios"), TIMEOUT)
        pauseForReview()
        compose.onNodeWithTag("movement_form").performScrollToNode(hasTestTag("movement_currency_MXN"))
        compose.onNodeWithTag("movement_currency_MXN").performClick()
        pauseForReview()
        compose.onNodeWithContentDescription("Cerrar").performClick()

        // Pockets: period chooser, create/edit + rollover, allocation, order, archive controls.
        compose.onNodeWithText("Pockets").performClick()
        compose.waitUntilExactlyOneExists(hasText("Crear Pocket"), TIMEOUT)
        pauseForReview()
        compose.onNodeWithText("Crear Pocket").performClick()
        compose.waitUntilExactlyOneExists(hasText("Aplicar rollover"), TIMEOUT)
        compose.onAllNodes(isToggleable())[0].performClick()
        pauseForReview()
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithTag("pockets_list").performScrollToNode(hasTestTag("pocket_Supermercado"))
        compose.onNodeWithTag("pocket_Supermercado").performClick()
        compose.waitUntilExactlyOneExists(hasTestTag("allocation_amount"), TIMEOUT)
        pauseForReview()
        compose.onNodeWithContentDescription("Mover Supermercado abajo").performClick()
        compose.onNodeWithText("Editar Pocket").performClick()
        compose.waitUntilExactlyOneExists(hasText("Editar Pocket"), TIMEOUT)
        pauseForReview()
        compose.onNodeWithText("Cancelar").performClick()
        pauseForReview()

        // Settings: review and exercise every configuration section.
        compose.onNodeWithText("Ajustes").performClick()
        compose.onNodeWithText("Periodo y fondos").performClick()
        compose.waitUntilExactlyOneExists(hasTestTag("settings_list"), TIMEOUT)
        compose.onNodeWithText("Guardar fondos").performClick()
        pauseForReview(SHORT_PAUSE)
        compose.onNodeWithText("Atrás").performClick()
        compose.onNodeWithText("Recordatorio diario").performClick()
        compose.onNodeWithTag("reminder_time").performTextReplacement("20:30")
        closeSoftKeyboard()
        compose.onNodeWithTag("reminder_switch").performClick()
        pauseForReview()
        compose.onNodeWithText("Atrás").performClick()
        compose.onNodeWithText("Métodos de pago").performClick()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("payment_method_Tarjeta"))
        compose.onNodeWithTag("payment_method_Tarjeta").performClick()
        pauseForReview()
        compose.onNodeWithText("Atrás").performClick()
        compose.onNodeWithText("Plantillas recurrentes").performClick()
        compose.onNodeWithTag("template_pocket_Supermercado").performScrollTo().performClick()
        compose.onNodeWithTag("template_method_Tarjeta").performScrollTo().performClick()
        pauseForReview()
        compose.onNodeWithText("Atrás").performClick()
        compose.onNodeWithText("Datos y portabilidad").performClick()
        compose.onNodeWithText("Crear backup completo").assertIsDisplayed()
        compose.onNodeWithText("Restaurar backup").assertIsDisplayed()
        compose.onNodeWithText("Exportar CSV").assertIsDisplayed()
        pauseForReview(LONG_PAUSE)
        compose.onNodeWithText("Atrás").performClick()

        // End on the primary dashboard for a clear closing frame.
        compose.onNodeWithText("Inicio").performClick()
        compose.waitUntilExactlyOneExists(hasTestTag("dashboard_list"), TIMEOUT)
        reviewScrollTarget("dashboard_list", "Tu periodo", LONG_PAUSE)
    }

    private fun seedRepresentativeData(ledger: RoomPocketLedger, clock: Clock) = runBlocking {
        val state = ledger.state.first { !it.needsOnboarding && it.pockets.isNotEmpty() }
        val period = requireNotNull(state.currentPeriod)
        val supermarket = state.pockets.first { it.pocket.name == "Supermercado" }.pocket
        val transport = state.pockets.first { it.pocket.name == "Transporte" }.pocket
        val card = state.paymentMethods.first { it.name == "Tarjeta" }

        ledger.execute(LedgerCommand.SetAllocation(period.id, supermarket.id, 180_000))
        ledger.execute(LedgerCommand.SetAllocation(period.id, transport.id, 75_000))
        ledger.execute(
            LedgerCommand.AddMovement(
                id = "review-market",
                pocketId = supermarket.id,
                type = MovementType.EXPENSE,
                accountingAmountMinor = 22_875,
                occurredAtUtcMillis = clock.millis(),
                localDate = LocalDate.of(2026, 8, 24),
                merchant = "Tamimi Market",
                note = "Weekly groceries",
                paymentMethodId = card.id,
            ),
        )
        ledger.execute(
            LedgerCommand.AddMovement(
                id = "review-ride",
                pocketId = transport.id,
                type = MovementType.EXPENSE,
                accountingAmountMinor = 4_875,
                occurredAtUtcMillis = clock.millis() + 1,
                localDate = LocalDate.of(2026, 8, 24),
                merchant = "Campus ride",
                paymentMethodId = card.id,
                originalAmountMinor = 1_300,
                originalCurrencyCode = "USD",
                conversionStatus = ConversionStatus.ESTIMATED,
            ),
        )
        ledger.execute(LedgerCommand.UpsertTemplate(name = "Weekly groceries", amountMinor = 25_000, pocketId = supermarket.id, paymentMethodId = card.id))
    }

    private fun reviewScrollTarget(containerTag: String, text: String, delayMillis: Long = REVIEW_PAUSE) {
        compose.onNodeWithTag(containerTag).performScrollToNode(hasText(text))
        compose.onNodeWithText(text).assertIsDisplayed()
        pauseForReview(delayMillis)
    }


    private fun scrollMovementTo(text: String) {
        compose.onNodeWithTag("movement_form").performScrollToNode(hasText(text))
    }

    private fun pauseForReview(delayMillis: Long = REVIEW_PAUSE) {
        compose.waitForIdle()
        runBlocking { delay(delayMillis) }
    }

    private class ReviewPreferences : PreferencesStore {
        private val values = MutableStateFlow(AppPreferences())
        override val state = values
        override suspend fun setFuturePeriodStartDay(day: Int) {
            values.value = values.value.copy(futurePeriodStartDay = day)
        }
        override suspend fun setReminder(enabled: Boolean, time: LocalTime) {
            values.value = values.value.copy(reminderEnabled = enabled, reminderTime = time)
        }
        override suspend fun setOnlineFxEnabled(enabled: Boolean) {
            values.value = values.value.copy(onlineFxEnabled = enabled)
        }
        override suspend fun setDefaultExpenseCurrency(currency: com.aif31.pocket.domain.SupportedCurrency) {
            values.value = values.value.copy(defaultExpenseCurrency = currency)
        }
    }

    private companion object {
        const val TIMEOUT = 10_000L
        const val SHORT_PAUSE = 550L
        const val REVIEW_PAUSE = 900L
        const val LONG_PAUSE = 1_600L
    }
}
