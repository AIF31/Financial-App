package com.aif31.pocket.ui

import com.aif31.pocket.domain.SupportedCurrency
import org.junit.Assert.assertEquals
import org.junit.Test

class AnalysisTextTest {
    private val sar = SupportedCurrency.SAR

    @Test
    fun an_amount_never_breaks_between_its_currency_and_its_number() {
        assertEquals("SAR 1,234.56", MoneyText.unbroken(123_456, sar))
    }

    @Test
    fun a_change_is_spoken_in_words_with_its_percentage() {
        assertEquals("SAR 6.17 más al día, un 15% más", changeSpoken(617, 4_110, sar, unit = "al día"))
        assertEquals("SAR 31.63 menos de lo habitual, un 24% menos", changeSpoken(-3_163, 13_105, sar, than = "de lo habitual"))
    }

    @Test
    fun a_fall_to_zero_and_a_change_from_nothing_carry_no_percentage() {
        assertEquals("SAR 14.85 menos", changeSpoken(-1_485, 1_485, sar))
        assertEquals("SAR 5.00 más", changeSpoken(500, 0, sar))
    }

    @Test
    fun a_change_too_small_for_a_whole_percent_keeps_its_direction_and_drops_the_percentage() {
        assertEquals("SAR\u00A00.01 más", changeSpoken(1, 1_000, sar))
        assertEquals("SAR\u00A00.01 menos", changeSpoken(-1, 1_000, sar))
    }

    @Test
    fun a_percentage_rounds_toward_zero_like_the_comparison_summary() {
        // 2 of 3 is 66.6…%, shown as 66% everywhere in the app.
        assertEquals("SAR\u00A00.02 más, un 66% más", changeSpoken(2, 3, sar))
    }

    @Test
    fun a_refund_line_is_widened_by_its_magnitude_too() {
        val (low, high) = relativeRange(listOf(-10_000, -10_001))
        assertEquals(1_000.1, high - low, 0.001)
    }

    @Test
    fun the_comparison_delta_is_shown_with_an_arrow_and_spoken_without_one() {
        assertEquals("▲ SAR 9.00 más al día (+900%)", spendDeltaText(900, 900, sar))
        assertEquals("SAR 9.00 más al día (+900%)", spendDeltaWords(900, 900, sar))
        assertEquals("Sin cambio en el promedio diario", spendDeltaText(0, 0, sar))
    }

    @Test
    fun no_change_reads_as_the_unchanged_wording() {
        assertEquals("Igual que sept", changeSpoken(0, 1_000, sar, than = "que sept", unchanged = "Igual que sept"))
    }

    @Test
    fun a_share_rounds_toward_zero_without_overflowing_and_needs_a_positive_whole() {
        assertEquals(66, sharePercent(2, 3))
        // Just under half of the largest amount: no overflow, and 49.99… rounds toward zero.
        assertEquals(49, sharePercent(Long.MAX_VALUE / 2, Long.MAX_VALUE))
        assertEquals(null, sharePercent(5, 0))
        assertEquals(null, sharePercent(5, -10))
    }

    @Test
    fun a_relative_line_spans_its_own_lowest_to_highest_value() {
        assertEquals(2_113.0 to 3_140.0, relativeRange(listOf(3_010, 2_870, 3_140, 2_113)))
    }

    @Test
    fun a_nearly_flat_line_is_widened_to_a_tenth_of_its_highest_value() {
        // 5.00 to 5.10 is under a tenth of 5.10 (51 minor units), so the range widens evenly around it.
        val (low, high) = relativeRange(listOf(500, 505, 510))
        assertEquals(51.0, high - low, 0.001)
        assertEquals(505.0, (low + high) / 2, 0.001)
    }
}
