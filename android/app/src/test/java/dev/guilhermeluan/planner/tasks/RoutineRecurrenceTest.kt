package dev.guilhermeluan.planner.tasks

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoutineRecurrenceTest {
    private val routine = PlannerRoutine(
        id = "routine-1",
        accountId = "account-1",
        plannerId = "planner-1",
        title = "Caminhar",
        weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
        startDate = LocalDate.of(2026, 7, 6),
        time = LocalTime.of(7, 30),
        status = RoutineStatus.ACTIVE,
        version = 1,
    )

    @Test
    fun `projects only selected weekdays from the start date`() {
        assertEquals(
            PlannedRoutineOccurrence(
                id = "routine-1:2026-07-08",
                routineId = "routine-1",
                title = "Caminhar",
                day = LocalDate.of(2026, 7, 8),
                time = LocalTime.of(7, 30),
            ),
            RoutineRecurrence.occurrenceOn(routine, LocalDate.of(2026, 7, 8)),
        )
        assertNull(RoutineRecurrence.occurrenceOn(routine, LocalDate.of(2026, 7, 7)))
        assertNull(RoutineRecurrence.occurrenceOn(routine, LocalDate.of(2026, 7, 1)))
    }

    @Test
    fun `archived routine stops future projections`() {
        assertNull(
            RoutineRecurrence.occurrenceOn(
                routine.copy(status = RoutineStatus.ARCHIVED),
                LocalDate.of(2026, 7, 8),
            ),
        )
    }
}
