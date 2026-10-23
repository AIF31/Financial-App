package com.aif31.pocket

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aif31.pocket.domain.SupportedCurrency
import com.aif31.pocket.settings.FxRateAlertFrequency
import com.aif31.pocket.settings.FxRateAlertSettings
import com.aif31.pocket.settings.ReminderStatus
import com.aif31.pocket.fx.FxQuote
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class CurrencySettingsContentTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `consent off keeps foreign quote controls unavailable`() {
        compose.setContent {
            CurrencySettingsContent(
                state = CurrencySettingsUiState(
                    currentCurrency = SupportedCurrency.SAR,
                    onlineFxEnabled = false,
                    defaultExpenseCurrency = SupportedCurrency.SAR,
                ),
                contentPadding = PaddingValues(),
            )
        }

        compose.onNodeWithText("Conversión en línea desactivada").assertIsDisplayed()
        compose.onNodeWithTag("fx_consent").assertIsDisplayed()
        compose.onNodeWithText("Confirmar cambio próximo periodo").assertDoesNotExist()
    }

    @Test
    fun `loading state blocks transition confirmation`() {
        compose.setContent {
            CurrencySettingsContent(
                state = CurrencySettingsUiState(
                    currentCurrency = SupportedCurrency.SAR,
                    onlineFxEnabled = true,
                    defaultExpenseCurrency = SupportedCurrency.USD,
                    targetCurrency = SupportedCurrency.MXN,
                    quoteState = CurrencyQuoteState.Loading,
                ),
                contentPadding = PaddingValues(),
            )
        }

        compose.onNodeWithText("Consultando tipo de cambio…").assertIsDisplayed()
        compose.onNodeWithText("Confirmar cambio próximo periodo").assertIsNotEnabled()
    }

    @Test
    fun `error state blocks transition confirmation`() {
        compose.setContent {
            CurrencySettingsContent(
                state = CurrencySettingsUiState(
                    currentCurrency = SupportedCurrency.SAR,
                    onlineFxEnabled = true,
                    defaultExpenseCurrency = SupportedCurrency.USD,
                    targetCurrency = SupportedCurrency.MXN,
                    quoteState = CurrencyQuoteState.Error("No hay un tipo de cambio disponible para esa fecha"),
                ),
                contentPadding = PaddingValues(),
            )
        }
        compose.onNodeWithText("No hay un tipo de cambio disponible para esa fecha").assertIsDisplayed()
        compose.onNodeWithText("Confirmar cambio próximo periodo").assertIsNotEnabled()
    }

    @Test
    fun `ready quote shows frozen provenance and enables confirmation`() {
        val requested = LocalDate.of(2026, 9, 25)
        compose.setContent {
            CurrencySettingsContent(
                state = CurrencySettingsUiState(
                    currentCurrency = SupportedCurrency.SAR,
                    onlineFxEnabled = true,
                    defaultExpenseCurrency = SupportedCurrency.MXN,
                    targetCurrency = SupportedCurrency.MXN,
                    quoteState = CurrencyQuoteState.Ready(
                        FxQuote(
                            requested,
                            LocalDate.of(2026, 9, 23),
                            SupportedCurrency.SAR,
                            SupportedCurrency.MXN,
                            "4.6",
                            "SAMA_PARITY + BANXICO_FIX_SF43718",
                        )
                    ),
                ),
                contentPadding = PaddingValues(),
            )
        }

        compose.onNodeWithText("1 SAR = 4.6 MXN").assertIsDisplayed()
        compose.onNodeWithText("Efectiva: 2026-09-23").assertIsDisplayed()
        compose.onNodeWithText("Fuente: SAMA_PARITY + BANXICO_FIX_SF43718").assertIsDisplayed()
        compose.onNodeWithText("Confirmar cambio próximo periodo").assertIsEnabled()
    }

    @Test
    fun `rate alert cannot be turned on without online conversion`() {
        compose.setContent {
            CurrencySettingsContent(
                state = CurrencySettingsUiState(
                    currentCurrency = SupportedCurrency.SAR,
                    onlineFxEnabled = false,
                    defaultExpenseCurrency = SupportedCurrency.SAR,
                ),
                contentPadding = PaddingValues(),
            )
        }

        compose.onNodeWithTag("currency_settings_list").performScrollToNode(hasTestTag("fx_rate_alert_switch"))
        compose.onNodeWithTag("fx_rate_alert_switch")
            .assertIsNotEnabled()
            .assertContentDescriptionEquals("Aviso de tipo de cambio")
        compose.onNodeWithText("Requiere la conversión en línea").assertIsDisplayed()
    }

    @Test
    fun `rate alert edits the pair, frequency, weekday and minimum rate`() {
        var settings by mutableStateOf(FxRateAlertSettings(enabled = true))
        compose.setContent {
            CurrencySettingsContent(
                state = CurrencySettingsUiState(
                    currentCurrency = SupportedCurrency.SAR,
                    onlineFxEnabled = true,
                    defaultExpenseCurrency = SupportedCurrency.SAR,
                    fxRateAlert = FxRateAlertUiState(settings, ReminderStatus.Scheduled),
                ),
                contentPadding = PaddingValues(),
                onFxRateAlertChange = { settings = it },
            )
        }
        val list = compose.onNodeWithTag("currency_settings_list")

        list.performScrollToNode(hasTestTag("fx_rate_alert_status"))
        compose.onNodeWithText("Programado · a diario a las 09:00").assertIsDisplayed()
        list.performScrollToNode(hasTestTag("fx_rate_alert_pair_SAR_MXN"))
        settings = settings.copy(minimumRate = BigDecimal("5.2"))
        compose.onNodeWithTag("fx_rate_alert_pair_SAR_MXN").performClick()
        assertEquals(BigDecimal("5.2"), settings.minimumRate)
        list.performScrollToNode(hasTestTag("fx_rate_alert_pair_MXN_SAR"))
        compose.onNodeWithTag("fx_rate_alert_pair_MXN_SAR").performClick()
        list.performScrollToNode(hasTestTag("fx_rate_alert_weekly"))
        compose.onNodeWithTag("fx_rate_alert_weekly").performClick()
        list.performScrollToNode(hasTestTag("fx_rate_alert_day_FRIDAY"))
        compose.onNodeWithTag("fx_rate_alert_day_FRIDAY").performClick()
        list.performScrollToNode(hasTestTag("fx_rate_alert_minimum"))
        compose.onNodeWithTag("fx_rate_alert_minimum").performTextInput("abc")
        list.performScrollToNode(hasTestTag("fx_rate_alert_minimum_save"))
        compose.onNodeWithTag("fx_rate_alert_minimum_save").performClick()
        list.performScrollToNode(hasText("Escribe un tipo de cambio mayor que cero"))
        compose.onNodeWithText("Escribe un tipo de cambio mayor que cero").assertIsDisplayed()
        compose.onNodeWithTag("fx_rate_alert_minimum").performTextClearance()
        compose.onNodeWithTag("fx_rate_alert_minimum").performTextInput("0,21")
        compose.onNodeWithTag("fx_rate_alert_minimum_save").performClick()

        assertEquals(
            FxRateAlertSettings(
                enabled = true,
                base = SupportedCurrency.MXN,
                quote = SupportedCurrency.SAR,
                frequency = FxRateAlertFrequency.WEEKLY,
                weekday = DayOfWeek.FRIDAY,
                minimumRate = BigDecimal("0.21"),
            ),
            settings,
        )
        compose.onNodeWithText("Programado · los viernes a las 09:00").assertExists()
    }
}
