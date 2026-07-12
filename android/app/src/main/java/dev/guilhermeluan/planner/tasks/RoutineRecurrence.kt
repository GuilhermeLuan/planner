package dev.guilhermeluan.planner.tasks

import java.time.LocalDate

object RoutineRecurrence {
    fun occurrenceOn(routine: PlannerRoutine, day: LocalDate): PlannedRoutineOccurrence? {
        if (routine.status != RoutineStatus.ACTIVE) return null
        if (day.isBefore(routine.startDate)) return null
        if (day.dayOfWeek !in routine.weekdays) return null
        return PlannedRoutineOccurrence(
            id = occurrenceId(routine.id, day),
            routineId = routine.id,
            title = routine.title,
            day = day,
            time = routine.time,
        )
    }

    fun occurrenceId(routineId: String, day: LocalDate): String = "$routineId:$day"
}
