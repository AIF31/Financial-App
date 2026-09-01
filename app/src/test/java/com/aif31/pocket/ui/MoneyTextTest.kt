package com.aif31.pocket.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTextTest {
    @Test
    fun formats_grouped_sar_for_display() {
        assertEquals("SAR 1,234.56", MoneyText.sar(123_456))
    }

    @Test
    fun formats_ungrouped_major_value_for_editing() {
        assertEquals("1234.56", MoneyText.editable(123_456))
    }
}
