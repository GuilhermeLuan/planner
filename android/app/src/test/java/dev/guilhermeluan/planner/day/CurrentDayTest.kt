package dev.guilhermeluan.planner.day

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class CurrentDayTest {
    @Test
    fun `today follows Fuso da Conta at the date boundary`() {
        val clock = Clock.fixed(Instant.parse("2026-07-13T01:30:00Z"), ZoneOffset.UTC)

        assertEquals(
            LocalDate.of(2026, 7, 12),
            CurrentDay.at(clock, ZoneId.of("America/Sao_Paulo")),
        )
        assertEquals(
            LocalDate.of(2026, 7, 13),
            CurrentDay.at(clock, ZoneId.of("Europe/Lisbon")),
        )
    }
}
