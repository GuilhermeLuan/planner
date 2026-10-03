package dev.guilhermeluan.planner.day

import dev.guilhermeluan.planner.tasks.DoseStatus
import dev.guilhermeluan.planner.tasks.DoseUnit
import dev.guilhermeluan.planner.tasks.PlannedDose
import dev.guilhermeluan.planner.tasks.PlannedRoutineOccurrence
import dev.guilhermeluan.planner.tasks.RoutineOccurrenceStatus
import dev.guilhermeluan.planner.water.WaterDay
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class DaySummaryTest {
    private val day = LocalDate.of(2026, 10, 1)

    private fun routine(id: String, status: RoutineOccurrenceStatus) = PlannedRoutineOccurrence(
        id = id, title = id, day = day, time = null, status = status,
    )

    private fun dose(hour: Int, status: DoseStatus) = PlannedDose(
        medicineId = "m", name = "Losartana", amount = 1, unit = DoseUnit.TABLET,
        day = day, time = LocalTime.of(hour, 0), status = status,
    )

    @Test
    fun summarisesRoutinesWaterAndDosesOfTheDay() {
        val summary = DaySummary.of(
            selectedDay = day,
            routines = listOf(
                routine("a", RoutineOccurrenceStatus.DONE),
                routine("b", RoutineOccurrenceStatus.DONE),
                routine("c", RoutineOccurrenceStatus.PENDING),
                routine("d", RoutineOccurrenceStatus.PENDING),
            ),
            water = WaterDay(day, consumedMl = 1200, goalMl = 2000),
            doses = listOf(dose(8, DoseStatus.TAKEN), dose(14, DoseStatus.PENDING), dose(22, DoseStatus.SKIPPED)),
        )

        assertEquals("2 de 4", summary.routines.value)
        assertEquals(0.5f, summary.routines.progress, 0.001f)
        assertEquals("1,2 de 2 L", summary.water.value)
        assertEquals(0.6f, summary.water.progress, 0.001f)
        assertEquals("1 de 3", summary.medicines.value)
        assertEquals(1f / 3f, summary.medicines.progress, 0.001f)
    }

    @Test
    fun emptyDayHasZeroProgressAndWaterCapsAtTheGoal() {
        val summary = DaySummary.of(
            selectedDay = day,
            routines = emptyList(),
            water = WaterDay(day, consumedMl = 2500, goalMl = 2000),
            doses = emptyList(),
        )

        assertEquals("0 de 0", summary.routines.value)
        assertEquals(0f, summary.routines.progress, 0f)
        assertEquals("2,5 de 2 L", summary.water.value)
        assertEquals(1f, summary.water.progress, 0f)
        assertEquals("0 de 0", summary.medicines.value)
        assertEquals(0f, summary.medicines.progress, 0f)
    }

    @Test
    fun ignoresDataFromAnotherDayWhileTheSelectedDayLoads() {
        val yesterday = day.minusDays(1)

        val summary = DaySummary.of(
            selectedDay = day,
            routines = emptyList(),
            water = WaterDay(yesterday, consumedMl = 1500, goalMl = 2000),
            doses = listOf(dose(8, DoseStatus.TAKEN).copy(day = yesterday)),
        )

        assertEquals("—", summary.water.value)
        assertEquals(0f, summary.water.progress, 0f)
        assertEquals("0 de 0", summary.medicines.value)
    }

    @Test
    fun skippedDosesStayInTheTotalButCountAsNotTaken() {
        val summary = DaySummary.of(
            selectedDay = day,
            routines = emptyList(),
            water = null,
            doses = listOf(dose(8, DoseStatus.TAKEN), dose(14, DoseStatus.SKIPPED)),
        )

        assertEquals("1 de 2", summary.medicines.value)
    }

    @Test
    fun aZeroGoalDoesNotBreakTheProgress() {
        val summary = DaySummary.of(day, emptyList(), WaterDay(day, consumedMl = 300, goalMl = 0), emptyList())

        assertEquals(0f, summary.water.progress, 0f)
    }

    @Test
    fun ignoresRoutinesOfAnotherDay() {
        val summary = DaySummary.of(
            selectedDay = day,
            routines = listOf(
                routine("a", RoutineOccurrenceStatus.DONE).copy(day = day.minusDays(1)),
                routine("b", RoutineOccurrenceStatus.DONE),
                routine("c", RoutineOccurrenceStatus.PENDING),
            ),
            water = null,
            doses = emptyList(),
        )

        assertEquals("1 de 2", summary.routines.value)
    }
}
