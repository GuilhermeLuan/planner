package dev.guilhermeluan.planner.day

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class DayGreetingTest {
    @Test
    fun greetsByPeriodOfTheDay() {
        assertEquals("Bom dia", DayGreeting.forTime(LocalTime.of(5, 0)))
        assertEquals("Bom dia", DayGreeting.forTime(LocalTime.of(11, 59)))
        assertEquals("Boa tarde", DayGreeting.forTime(LocalTime.of(12, 0)))
        assertEquals("Boa tarde", DayGreeting.forTime(LocalTime.of(17, 59)))
        assertEquals("Boa noite", DayGreeting.forTime(LocalTime.of(18, 0)))
        assertEquals("Boa noite", DayGreeting.forTime(LocalTime.of(4, 59)))
    }

    @Test
    fun addsTheNameWhenKnown() {
        assertEquals("Boa noite, Guilherme", DayGreeting.text(LocalTime.of(21, 0), "Guilherme"))
        assertEquals("Boa noite", DayGreeting.text(LocalTime.of(21, 0), "  "))
    }
}
