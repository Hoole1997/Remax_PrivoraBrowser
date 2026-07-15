package com.example.browser.ui.tabs

import org.junit.Assert.assertEquals
import org.junit.Test

class TabCountUiTest {

    @Test
    fun format_preservesCountsWithinDisplayRange() {
        assertEquals("0", TabCountUi.format(0))
        assertEquals("1", TabCountUi.format(1))
        assertEquals("99", TabCountUi.format(99))
    }

    @Test
    fun format_clampsCountsOutsideDisplayRange() {
        assertEquals("0", TabCountUi.format(-1))
        assertEquals("99", TabCountUi.format(100))
    }
}
