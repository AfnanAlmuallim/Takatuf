package com.z.takatuf.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DatesTest {

    @Test
    fun `a deadline covers the whole of its last day`() {
        val day = LocalDate.of(2026, 10, 30)
        val end = Dates.endOf(day)
        assertEquals(LocalDate.of(2026, 10, 31), Dates.startDate(end))
        assertEquals(day, Dates.lastDay(end))
    }

    @Test
    fun `picker values round-trip`() {
        val day = LocalDate.of(2026, 2, 28)
        assertEquals(day, Dates.fromPicker(Dates.toPicker(day)))
    }

    @Test
    fun `days left counts from today to the last day`() {
        val today = LocalDate.of(2026, 9, 26)
        assertEquals(0L, Dates.daysLeft(Dates.endOf(today), today))
        assertEquals(1L, Dates.daysLeft(Dates.endOf(today.plusDays(1)), today))
        assertEquals(14L, Dates.daysLeft(Dates.endOf(today.plusDays(14)), today))
    }
}
