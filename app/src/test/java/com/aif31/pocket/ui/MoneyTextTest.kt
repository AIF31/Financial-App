package com.aif31.pocket.ui

import com.aif31.pocket.domain.SupportedCurrency
import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTextTest {
    @Test
    fun formats_grouped_sar_for_display() {
        assertEquals("SAR 1,234.56", MoneyText.sar(123_456))
    }

    @Test
    fun formats_grouped_amount_in_its_supported_currency() {
        assertEquals("USD 1,234.56", MoneyText.format(123_456, SupportedCurrency.USD))
        assertEquals("MXN -0.05", MoneyText.format(-5, SupportedCurrency.MXN))
    }

    @Test
    fun formats_a_rate_in_the_direction_people_quote() {
        assertEquals("1 USD = 3.75 SAR", MoneyText.rate(SupportedCurrency.SAR, SupportedCurrency.USD, "0.2666666666666666666666666666666667"))
        assertEquals("1 SAR = 4.6 MXN", MoneyText.rate(SupportedCurrency.SAR, SupportedCurrency.MXN, "4.6"))
        assertEquals("1 USD = 18.5185 MXN", MoneyText.rate(SupportedCurrency.MXN, SupportedCurrency.USD, "0.054"))
    }

    @Test
    fun formats_ungrouped_major_value_for_editing() {
        assertEquals("1234.56", MoneyText.editable(123_456))
    }
}
