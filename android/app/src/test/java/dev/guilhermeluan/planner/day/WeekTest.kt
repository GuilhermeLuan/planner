package dev.guilhermeluan.planner.day

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class WeekTest {
    @Test
    fun weekRunsFromMondayToSundayAroundAnyDay() {
        val expected = LocalDate.of(2026, 9, 28)..LocalDate.of(2026, 10, 4)

        assertEquals(expected, Week.of(LocalDate.of(2026, 9, 28)))
        assertEquals(expected, Week.of(LocalDate.of(2026, 10, 1)))
        assertEquals(expected, Week.of(LocalDate.of(2026, 10, 4)))
    }
}
