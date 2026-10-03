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

    fun noun(amount: Int): String = if (amount == 1) singular else plural

    fun format(amount: Int): String = "$amount ${noun(amount)}"
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
    val stock: MedicineStock? = null,
)

/**
 * Estoque de um Remédio. [capacity] é o que foi informado no cadastro e só serve de escala para a barra.
 */
data class MedicineStock(val amount: Int, val capacity: Int, val threshold: Int) {
    /** Estoque igual ou abaixo do limite de aviso. */
    val low: Boolean get() = amount <= threshold

    /** Quanto da barra está cheio, entre 0 e 1. */
    val fraction: Float get() = amount.toFloat() / maxOf(capacity, amount, 1)
}

/**
 * Estoque informado no formulário, dado o Estoque [current] (nulo no cadastro). Sem quantidade, o Estoque
 * é limpo. A mesma quantidade mantém a escala da barra; outra funciona como reposição e a redefine.
 */
fun MedicineDraft.toStock(current: MedicineStock? = null): MedicineStock? =
    stock?.let {
        MedicineStock(it, capacity = if (current != null && it == current.amount) current.capacity else it, threshold = stockThreshold ?: 0)
    }

/** O rascunho pronto para gravar, com o nome aparado; falha com [IllegalArgumentException] se for inválido. */
fun MedicineDraft.validated(): MedicineDraft {
    val trimmed = name.trim()
    require(trimmed.isNotEmpty()) { "O Remédio precisa de um nome" }
    require(amount > 0) { "A dose precisa de uma quantidade" }
    require(times.isNotEmpty()) { "Escolha ao menos um horário" }
    when (repeat) {
        MedicineRepeat.Daily -> Unit
        is MedicineRepeat.Weekdays -> require(repeat.days.isNotEmpty()) { "Escolha ao menos um dia da semana" }
        is MedicineRepeat.Period -> require(!repeat.end.isBefore(repeat.start)) { "O período termina antes de começar" }
    }
    return copy(name = trimmed)
}

/** Agendamento de um Remédio que valia nos Dias antes de [until]. */
data class MedicinePreviousVersion(
    val until: LocalDate,
    val name: String,
    val amount: Int,
    val unit: DoseUnit,
    val times: Set<LocalTime>,
    val repeat: MedicineRepeat,
    val startDate: LocalDate,
) {
    fun applyTo(medicine: PlannerMedicine) = medicine.copy(
        name = name, amount = amount, unit = unit, times = times, repeat = repeat, startDate = startDate,
    )
}

/** Período, a partir de [from] (inclusive) até [until] (exclusive, nulo se segue aberto), em que o Remédio esteve arquivado. */
data class MedicineArchivedPeriod(val from: LocalDate, val until: LocalDate?) {
    fun covers(day: LocalDate) = !day.isBefore(from) && (until == null || day.isBefore(until))
}

/**
 * Primeiro Dia em que vale uma edição feita em [today]. Se já há Dose registrada hoje ou depois, a edição
 * começa no Dia seguinte à última delas, para nenhuma Dose registrada mudar nem aparecer em duplicidade.
 */
fun editEffectiveFrom(today: LocalDate, lastRegisteredDay: LocalDate?): LocalDate =
    lastRegisteredDay?.plusDays(1)?.takeIf { it.isAfter(today) } ?: today

/** O Remédio como era em [day]: a versão anterior mais próxima que ainda vale para o Dia, ou ele mesmo. */
fun PlannerMedicine.versionOn(day: LocalDate, previousVersions: List<MedicinePreviousVersion>): PlannerMedicine =
    previousVersions.filter { day.isBefore(it.until) }.minByOrNull { it.until }
        ?.applyTo(this)
        ?: this

data class MedicineDraft(
    val name: String,
    val amount: Int,
    val unit: DoseUnit,
    val times: Set<LocalTime>,
    val repeat: MedicineRepeat,
    val startDate: LocalDate,
    val stock: Int? = null,
    val stockThreshold: Int? = null,
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
    /** Quando o Lembrete da Dose volta, se ela foi adiada. */
    val snoozedUntil: Instant? = null,
)
