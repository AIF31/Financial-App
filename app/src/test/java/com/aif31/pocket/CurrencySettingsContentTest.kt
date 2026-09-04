package com.aif31.pocket

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aif31.pocket.domain.SupportedCurrency
import com.aif31.pocket.fx.FxQuote
import java.time.LocalDate
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
}
