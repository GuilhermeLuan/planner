package dev.guilhermeluan.planner.tasks

import java.time.Instant
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

enum class DoseUnit(val label: String, private val singular: String, private val plural: String) {
    TABLET("comprimido", "comprimido", "comprimidos"),
    CAPSULE("cápsula", "cápsula", "cápsulas"),
    DROPS("gotas", "gota", "gotas"),
    ML("ml", "ml", "ml");

    fun format(amount: Int): String = "$amount ${if (amount == 1) singular else plural}"
}

sealed interface MedicineRepeat {
    data object Daily : MedicineRepeat
    data class Weekdays(val days: Set<DayOfWeek>) : MedicineRepeat
    data class Period(val start: LocalDate, val end: LocalDate) : MedicineRepeat
}

enum class MedicineStatus { ACTIVE, ARCHIVED }

enum class DoseStatus { PENDING, TAKEN, SKIPPED }

data class PlannerMedicine(
    val id: String,
    val accountId: String,
    val plannerId: String,
    val name: String,
    val amount: Int,
    val unit: DoseUnit,
    val times: Set<LocalTime>,
    val repeat: MedicineRepeat,
    val startDate: LocalDate,
    val status: MedicineStatus,
)

data class MedicineDraft(
    val name: String,
    val amount: Int,
    val unit: DoseUnit,
    val times: Set<LocalTime>,
    val repeat: MedicineRepeat,
    val startDate: LocalDate,
)

data class PlannedDose(
    val medicineId: String,
    val name: String,
    val amount: Int,
    val unit: DoseUnit,
    val day: LocalDate,
    val time: LocalTime,
    val status: DoseStatus = DoseStatus.PENDING,
    val takenAt: Instant? = null,
)
