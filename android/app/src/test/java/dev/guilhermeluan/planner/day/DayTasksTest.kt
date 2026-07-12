package dev.guilhermeluan.planner.day

import dev.guilhermeluan.planner.tasks.PlannerTask
import dev.guilhermeluan.planner.tasks.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class DayTasksTest {
    @Test
    fun `one Task list orders timed items before untimed items`() {
        val untimed = task("untimed", null)
        val late = task("late", LocalTime.of(18, 0))
        val early = task("early", LocalTime.of(8, 30))

        assertEquals(listOf(early, late, untimed), DayTasks.ordered(listOf(untimed, late, early)))
    }

    private fun task(id: String, time: LocalTime?) = PlannerTask(
        id = id,
        accountId = "account-1",
        plannerId = "planner-1",
        title = id,
        day = LocalDate.of(2026, 7, 12),
        time = time,
        status = TaskStatus.PENDING,
        archived = false,
        version = 1,
    )
}
