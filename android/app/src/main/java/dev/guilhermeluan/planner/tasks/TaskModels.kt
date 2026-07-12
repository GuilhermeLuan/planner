package dev.guilhermeluan.planner.tasks

import java.time.LocalDate
import java.time.LocalTime
import java.time.DayOfWeek

enum class TaskStatus { PENDING, DONE }

data class PlannerTask(
    val id: String,
    val accountId: String,
    val plannerId: String,
    val title: String,
    val day: LocalDate,
    val time: LocalTime?,
    val status: TaskStatus,
    val archived: Boolean,
    val version: Long,
)

data class TaskDraft(
    val title: String,
    val day: LocalDate,
    val time: LocalTime?,
)

enum class RoutineStatus { ACTIVE, ARCHIVED }

enum class RoutineOccurrenceStatus { PENDING, DONE, SKIPPED }

data class PlannerRoutine(
    val id: String,
    val accountId: String,
    val plannerId: String,
    val title: String,
    val weekdays: Set<DayOfWeek>,
    val startDate: LocalDate,
    val time: LocalTime?,
    val status: RoutineStatus,
    val version: Long,
)

data class RoutineDraft(
    val title: String,
    val weekdays: Set<DayOfWeek>,
    val startDate: LocalDate,
    val time: LocalTime?,
)

data class PlannedRoutineOccurrence(
    val id: String,
    val title: String,
    val day: LocalDate,
    val time: LocalTime?,
    val status: RoutineOccurrenceStatus = RoutineOccurrenceStatus.PENDING,
    val routineId: String = id.substringBefore(':', id),
)

data class DayPlan(
    val day: LocalDate,
    val tasks: List<PlannerTask>,
    val routines: List<PlannedRoutineOccurrence>,
    val archivedTasks: List<PlannerTask> = emptyList(),
)

fun interface IdGenerator {
    fun nextId(): String
}
