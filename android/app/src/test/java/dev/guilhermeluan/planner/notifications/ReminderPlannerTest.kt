package dev.guilhermeluan.planner.notifications

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderPlannerTest {
    @Test
    fun `uses account timezone when converting a future reminder`() {
        val now = Instant.parse("2026-07-11T10:00:00Z")
        assertEquals(
            Instant.parse("2026-07-11T12:30:00Z"),
            ReminderPlanner.triggerAt(
                day = LocalDate.of(2026, 7, 11),
                time = LocalTime.of(9, 30),
                timezone = "America/Sao_Paulo",
                now = now,
            ),
        )
    }

    @Test
    fun `does not schedule a reminder already in the past`() {
        assertNull(
            ReminderPlanner.triggerAt(
                day = LocalDate.of(2026, 7, 11),
                time = LocalTime.of(8, 0),
                timezone = "America/Sao_Paulo",
                now = Instant.parse("2026-07-11T12:00:00Z"),
            ),
        )
    }
}
