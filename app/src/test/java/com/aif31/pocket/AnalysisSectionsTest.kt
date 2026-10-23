package com.aif31.pocket

import org.junit.Assert.assertEquals
import org.junit.Test

class AnalysisSectionsTest {
    @Test
    fun saved_sections_come_first_and_the_rest_follow_in_default_order() {
        val order = AnalysisSection.ordered(listOf("ROLLOVER", "REMOVED_SECTION", "MERCHANTS", "ROLLOVER"))

        assertEquals(
            listOf(
                AnalysisSection.ROLLOVER,
                AnalysisSection.MERCHANTS,
                AnalysisSection.TREND,
                AnalysisSection.POCKETS,
                AnalysisSection.RECURRING,
                AnalysisSection.WHEN,
                AnalysisSection.PAYMENT_METHODS,
                AnalysisSection.FOREIGN_CURRENCY,
            ),
            order,
        )
    }

    @Test
    fun nothing_saved_keeps_the_default_order() {
        assertEquals(AnalysisSection.entries, AnalysisSection.ordered(emptyList()))
    }

    @Test
    fun a_section_moves_by_its_offset_and_stops_at_either_end() {
        val order = AnalysisSection.entries

        assertEquals(AnalysisSection.POCKETS, order.moved(AnalysisSection.TREND, 1).first())
        assertEquals(order, order.moved(AnalysisSection.TREND, -1))
        assertEquals(AnalysisSection.TREND, order.moved(AnalysisSection.TREND, 99).last())
    }
}
