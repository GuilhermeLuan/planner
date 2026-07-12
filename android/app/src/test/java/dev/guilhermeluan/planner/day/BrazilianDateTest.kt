package dev.guilhermeluan.planner.day

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.DayOfWeek

class BrazilianDateTest {
    @Test
    fun `Routine date is presented and read in Brazilian format`() {
        val day = LocalDate.of(2026, 7, 12)

        assertEquals("12/07/2026", BrazilianDate.format(day))
        assertEquals(day, BrazilianDate.parse("12/07/2026"))
    }

    @Test
    fun `invalid Brazilian date cannot become a Day`() {
        assertNull(BrazilianDate.parse("31/02/2026"))
        assertNull(BrazilianDate.parse("2026-07-12"))
    }

    @Test
    fun `Routine offers every weekday from Monday through Sunday`() {
        assertEquals(DayOfWeek.entries, RoutineWeekdays.options)
    }
}
