package dev.guilhermeluan.planner.tasks

import java.time.Duration
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

/** Tudo o que está arquivado no Planner, para restaurar de um só lugar. */
data class ArchivedItems(
    val tasks: List<PlannerTask> = emptyList(),
    val routines: List<PlannerRoutine> = emptyList(),
    val medicines: List<PlannerMedicine> = emptyList(),
) {
    val isEmpty: Boolean get() = tasks.isEmpty() && routines.isEmpty() && medicines.isEmpty()
}

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
    /** Atraso do alarme; nulo quando o Remédio não tem Alarme de Dose. */
    val alarmDelay: Duration? = null,
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
 * é limpo. Se a quantidade é a que o formulário mostrava ao abrir, ela não foi mexida e o Estoque atual
 * fica, mesmo que uma Dose tomada enquanto isso o tenha mudado. A mesma quantidade mantém a escala da
 * barra; outra funciona como reposição e a redefine.
 */
fun MedicineDraft.toStock(current: MedicineStock? = null): MedicineStock? {
    val amount = stock ?: return null
    val threshold = stockThreshold ?: 0
    return when {
        current != null && amount == stockAsShown -> current.copy(threshold = threshold)
        current != null && amount == current.amount -> current.copy(threshold = threshold)
        else -> MedicineStock(amount, capacity = amount, threshold = threshold)
    }
}

/** O rascunho pronto para gravar, com o nome aparado; falha com [IllegalArgumentException] se for inválido. */
fun MedicineDraft.validated(): MedicineDraft {
    val trimmed = name.trim()
    require(trimmed.isNotEmpty()) { "O Remédio precisa de um nome" }
    require(amount > 0) { "A dose precisa de uma quantidade" }
    require(times.isNotEmpty()) { "Escolha ao menos um horário" }
    require(alarmDelayMinutes == null || alarmDelayMinutes in AlarmDelay.VALID_MINUTES) {
        "O atraso do alarme vai de ${AlarmDelay.VALID_MINUTES.first} a ${AlarmDelay.VALID_MINUTES.last} minutos"
    }
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
    /** Estoque que o formulário de edição mostrava ao abrir; nulo no cadastro. */
    val stockAsShown: Int? = null,
    /** Atraso do alarme em minutos (veja [AlarmDelay]); nulo deixa o Alarme de Dose desligado. */
    val alarmDelayMinutes: Int? = null,
) {
    fun alarmDelay(): Duration? = alarmDelayMinutes?.let { Duration.ofMinutes(it.toLong()) }
}

/** Atraso do alarme: as opções rápidas e o intervalo aceito em "Outro". */
object AlarmDelay {
    val QUICK_MINUTES = listOf(10, 15, 30, 60)
    const val DEFAULT_MINUTES = 30
    val VALID_MINUTES = 5..180

    /** Como o atraso aparece nas opções e no cartão do Remédio: "30 min", ou "1 hora" para 60. */
    fun label(minutes: Int): String = if (minutes == 60) "1 hora" else "$minutes min"
}

/** Identifica uma Dose: o Remédio, o Dia e o horário. Na forma de texto, é a chave dos agendamentos. */
data class DoseKey(val medicineId: String, val day: LocalDate, val time: LocalTime) {
    override fun toString() = "$medicineId|$day|$time"

    companion object {
        fun parse(text: String): DoseKey {
            val (medicineId, day, time) = text.split('|')
            return DoseKey(medicineId, LocalDate.parse(day), LocalTime.parse(time))
        }
    }
}

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
    /** Atraso do alarme do Remédio; nulo se ele não tem Alarme de Dose. */
    val alarmDelay: Duration? = null,
    /** Quando o Alarme de Dose volta, se ele foi adiado. */
    val alarmSnoozedUntil: Instant? = null,
) {
    val key: DoseKey get() = DoseKey(medicineId, day, time)
}
