package dev.guilhermeluan.planner.tasks

import dev.guilhermeluan.planner.day.Week
import dev.guilhermeluan.planner.storage.DoseRecordEntity
import dev.guilhermeluan.planner.storage.MedicineEntity
import dev.guilhermeluan.planner.storage.MedicineTimeEntity
import dev.guilhermeluan.planner.storage.PlannerDatabase
import dev.guilhermeluan.planner.storage.RoutineEntity
import dev.guilhermeluan.planner.storage.TaskEntity
import dev.guilhermeluan.planner.storage.RoutineOccurrenceEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class RoomPlannerRepository(
    database: PlannerDatabase,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
) {
    private val dao = database.plannerDao()
    private val routineDao = database.routineDao()
    private val medicineDao = database.medicineDao()

    suspend fun createTask(
        accountId: String,
        plannerId: String,
        draft: TaskDraft,
    ): PlannerTask {
        val title = draft.title.trim()
        require(title.isNotEmpty()) { "A Tarefa precisa de um título" }
        val taskId = idGenerator.nextId()
        val task = PlannerTask(
            id = taskId,
            accountId = accountId,
            plannerId = plannerId,
            title = title,
            day = draft.day,
            time = draft.time,
            status = TaskStatus.PENDING,
            archived = false,
            version = 0,
        )
        return persistTask(task)
    }

    fun observeDay(accountId: String, day: LocalDate): Flow<DayPlan> = combine(
        dao.observeTasks(accountId, day.toString()),
        dao.observeArchivedTasks(accountId, day.toString()),
        routineDao.observeRoutines(accountId),
        routineDao.observeOccurrences(accountId, day.toString()),
    ) { tasks, archivedTasks, routines, occurrences ->
        val projected = routines.mapNotNull { entity ->
            RoutineRecurrence.occurrenceOn(entity.toDomain(), day)
        }
        val persisted = occurrences.map(RoutineOccurrenceEntity::toDomain)
        DayPlan(
            day = day,
            tasks = tasks.map(TaskEntity::toDomain),
            routines = (projected + persisted).associateBy(PlannedRoutineOccurrence::id).values.toList(),
            archivedTasks = archivedTasks.map(TaskEntity::toDomain),
        )
    }

    fun observeMarkedDays(accountId: String, week: ClosedRange<LocalDate>): Flow<Set<LocalDate>> = combine(
        dao.observeTaskDays(accountId, week.start.toString(), week.endInclusive.toString()),
        routineDao.observeRoutines(accountId),
    ) { taskDays, routines ->
        val domainRoutines = routines.map(RoutineEntity::toDomain)
        val routineDays = Week.days(week).filter { day ->
            domainRoutines.any { RoutineRecurrence.occurrenceOn(it, day) != null }
        }
        taskDays.map(LocalDate::parse).toSet() + routineDays
    }

    fun observeScheduledTasks(accountId: String): Flow<List<PlannerTask>> =
        dao.observeScheduledTasks(accountId).map { tasks -> tasks.map(TaskEntity::toDomain) }

    suspend fun setTaskCompleted(
        accountId: String,
        taskId: String,
        completed: Boolean,
    ): PlannerTask {
        val current = currentTask(accountId, taskId)
        val updated = current.copy(status = if (completed) TaskStatus.DONE else TaskStatus.PENDING)
        return persistTask(updated)
    }

    suspend fun rescheduleTask(
        accountId: String,
        taskId: String,
        day: LocalDate,
    ): PlannerTask {
        val current = currentTask(accountId, taskId)
        val updated = current.copy(day = day)
        return persistTask(updated)
    }

    suspend fun archiveTask(accountId: String, taskId: String): PlannerTask =
        setTaskArchived(accountId, taskId, archived = true)

    suspend fun restoreTask(accountId: String, taskId: String): PlannerTask =
        setTaskArchived(accountId, taskId, archived = false)

    suspend fun editTask(
        accountId: String,
        taskId: String,
        title: String,
        time: LocalTime?,
    ): PlannerTask {
        val current = currentTask(accountId, taskId)
        val normalizedTitle = title.trim()
        require(normalizedTitle.isNotEmpty()) { "A Tarefa precisa de um título" }
        val updated = current.copy(title = normalizedTitle, time = time)
        return persistTask(updated)
    }

    suspend fun createRoutine(
        accountId: String,
        plannerId: String,
        draft: RoutineDraft,
    ): PlannerRoutine {
        val title = draft.title.trim()
        require(title.isNotEmpty()) { "A Rotina precisa de um título" }
        require(draft.weekdays.isNotEmpty()) { "Escolha ao menos um dia da semana" }
        val routine = PlannerRoutine(
            id = idGenerator.nextId(),
            accountId = accountId,
            plannerId = plannerId,
            title = title,
            weekdays = draft.weekdays,
            startDate = draft.startDate,
            time = draft.time,
            status = RoutineStatus.ACTIVE,
            version = 0,
        )
        return persistRoutine(routine)
    }

    suspend fun editRoutine(
        accountId: String,
        routineId: String,
        title: String,
        weekdays: Set<DayOfWeek>,
        startDate: LocalDate,
        time: LocalTime?,
    ): PlannerRoutine {
        val current = currentRoutine(accountId, routineId)
        val normalizedTitle = title.trim()
        require(normalizedTitle.isNotEmpty()) { "A Rotina precisa de um título" }
        require(weekdays.isNotEmpty()) { "Escolha ao menos um dia da semana" }
        return persistRoutine(
            current.copy(
                title = normalizedTitle,
                weekdays = weekdays,
                startDate = startDate,
                time = time,
            ),
        )
    }

    suspend fun archiveRoutine(accountId: String, routineId: String): PlannerRoutine =
        setRoutineStatus(accountId, routineId, RoutineStatus.ARCHIVED)

    suspend fun restoreRoutine(accountId: String, routineId: String): PlannerRoutine =
        setRoutineStatus(accountId, routineId, RoutineStatus.ACTIVE)

    suspend fun setRoutineOccurrenceStatus(
        accountId: String,
        routineId: String,
        day: LocalDate,
        status: RoutineOccurrenceStatus,
    ): PlannedRoutineOccurrence {
        val routine = currentRoutine(accountId, routineId)
        val occurrenceId = RoutineRecurrence.occurrenceId(routine.id, day)
        val existing = routineDao.occurrence(accountId, occurrenceId)?.toDomain()
        val occurrence = (existing ?: PlannedRoutineOccurrence(
            id = occurrenceId,
            routineId = routine.id,
            title = routine.title,
            day = day,
            time = routine.time,
        )).copy(status = status)
        val updatedAt = clock.instant().toString()
        routineDao.writeLocalOccurrence(occurrence.toEntity(accountId, updatedAt))
        return occurrence
    }

    suspend fun createMedicine(
        accountId: String,
        plannerId: String,
        draft: MedicineDraft,
    ): PlannerMedicine {
        val name = draft.name.trim()
        require(name.isNotEmpty()) { "O Remédio precisa de um nome" }
        require(draft.amount > 0) { "A dose precisa de uma quantidade" }
        require(draft.times.isNotEmpty()) { "Escolha ao menos um horário" }
        val medicine = PlannerMedicine(
            id = idGenerator.nextId(),
            accountId = accountId,
            plannerId = plannerId,
            name = name,
            amount = draft.amount,
            unit = draft.unit,
            times = draft.times,
            repeat = draft.repeat,
            startDate = (draft.repeat as? MedicineRepeat.Period)?.start ?: draft.startDate,
            status = MedicineStatus.ACTIVE,
            stock = draft.stock?.let { MedicineStock(it, capacity = it, threshold = draft.stockThreshold ?: 0) },
        )
        medicineDao.writeLocalMedicine(
            medicine.toEntity(clock.instant().toString()),
            medicine.times.map { MedicineTimeEntity(medicine.id, it.toString()) },
        )
        return medicine
    }

    fun observeMedicines(accountId: String): Flow<List<PlannerMedicine>> = combine(
        medicineDao.observeMedicines(accountId),
        medicineDao.observeTimes(accountId),
        ::assembleMedicines,
    ).map { medicines -> medicines.sortedBy { it.times.minOrNull() } }

    fun observeDoses(accountId: String, day: LocalDate): Flow<List<PlannedDose>> = combine(
        medicineDao.observeMedicines(accountId),
        medicineDao.observeTimes(accountId),
        medicineDao.observeDoseRecords(accountId, day.toString()),
    ) { medicineEntities, times, records ->
        val recordsByDose = records.associateBy { it.medicineId to LocalTime.parse(it.time) }
        assembleMedicines(medicineEntities, times)
            .filter { MedicineRecurrence.occursOn(it, day) }
            .flatMap { medicine ->
                medicine.times.map { time ->
                    medicine.doseOn(day, time, recordsByDose[medicine.id to time])
                }
            }
            .sortedBy(PlannedDose::time)
    }

    private fun assembleMedicines(
        medicines: List<MedicineEntity>,
        times: List<MedicineTimeEntity>,
    ): List<PlannerMedicine> {
        val timesByMedicine = times.groupBy(MedicineTimeEntity::medicineId)
        return medicines
            .map { it.toDomain(timesByMedicine[it.id].orEmpty().map { t -> LocalTime.parse(t.time) }.toSet()) }
            .filter { it.status == MedicineStatus.ACTIVE }
    }

    /** Uma Dose só é persistida quando sai de pendente; voltar a pendente apaga o registro. */
    suspend fun setDoseStatus(
        accountId: String,
        medicineId: String,
        day: LocalDate,
        time: LocalTime,
        status: DoseStatus,
    ) {
        val now = clock.instant().toString()
        medicineDao.writeDoseStatus(
            accountId = accountId,
            medicineId = medicineId,
            day = day.toString(),
            time = time.toString(),
            status = status.name,
            updatedAt = now,
        ) ?: error("Remédio não encontrado")
    }

    private suspend fun setTaskArchived(
        accountId: String,
        taskId: String,
        archived: Boolean,
    ): PlannerTask {
        val current = currentTask(accountId, taskId)
        val updated = current.copy(archived = archived)
        return persistTask(updated)
    }

    private suspend fun currentTask(accountId: String, taskId: String): PlannerTask =
        dao.task(accountId, taskId)?.toDomain() ?: error("Tarefa não encontrada")

    private suspend fun currentRoutine(accountId: String, routineId: String): PlannerRoutine =
        routineDao.routine(accountId, routineId)?.toDomain() ?: error("Rotina não encontrada")

    private suspend fun persistTask(task: PlannerTask): PlannerTask {
        val updatedAt = clock.instant().toString()
        dao.writeLocalTask(task.toEntity(updatedAt))
        return task
    }

    private suspend fun persistRoutine(routine: PlannerRoutine): PlannerRoutine {
        val updatedAt = clock.instant().toString()
        routineDao.writeLocalRoutine(routine.toEntity(updatedAt))
        return routine
    }

    private suspend fun setRoutineStatus(
        accountId: String,
        routineId: String,
        status: RoutineStatus,
    ): PlannerRoutine = persistRoutine(
        currentRoutine(accountId, routineId).copy(status = status),
    )
}

private fun PlannerTask.toEntity(updatedAt: String) = TaskEntity(
    id = id,
    accountId = accountId,
    plannerId = plannerId,
    title = title,
    day = day.toString(),
    time = time?.toString(),
    status = status.name,
    archived = archived,
    version = version,
    updatedAt = updatedAt,
)

private fun TaskEntity.toDomain() = PlannerTask(
    id = id,
    accountId = accountId,
    plannerId = plannerId,
    title = title,
    day = LocalDate.parse(day),
    time = time?.let(LocalTime::parse),
    status = TaskStatus.valueOf(status),
    archived = archived,
    version = version,
)

private fun PlannerRoutine.toEntity(updatedAt: String) = dev.guilhermeluan.planner.storage.RoutineEntity(
    id = id,
    accountId = accountId,
    plannerId = plannerId,
    title = title,
    weekdays = weekdays.sortedBy(DayOfWeek::getValue).joinToString(",") { it.value.toString() },
    startDate = startDate.toString(),
    time = time?.toString(),
    status = status.name,
    version = version,
    updatedAt = updatedAt,
)

private fun dev.guilhermeluan.planner.storage.RoutineEntity.toDomain() = PlannerRoutine(
    id = id,
    accountId = accountId,
    plannerId = plannerId,
    title = title,
    weekdays = weekdays.split(',').filter(String::isNotBlank).map { DayOfWeek.of(it.toInt()) }.toSet(),
    startDate = LocalDate.parse(startDate),
    time = time?.let(LocalTime::parse),
    status = RoutineStatus.valueOf(status),
    version = version,
)

private fun PlannedRoutineOccurrence.toEntity(
    accountId: String,
    updatedAt: String,
) = dev.guilhermeluan.planner.storage.RoutineOccurrenceEntity(
    id = id,
    accountId = accountId,
    routineId = routineId,
    title = title,
    day = day.toString(),
    time = time?.toString(),
    status = status.name,
    version = 0,
    updatedAt = updatedAt,
)

private fun dev.guilhermeluan.planner.storage.RoutineOccurrenceEntity.toDomain() = PlannedRoutineOccurrence(
    id = id,
    routineId = routineId,
    title = title,
    day = LocalDate.parse(day),
    time = time?.let(LocalTime::parse),
    status = RoutineOccurrenceStatus.valueOf(status),
)

private fun PlannerMedicine.doseOn(day: LocalDate, time: LocalTime, record: DoseRecordEntity?) = PlannedDose(
    medicineId = id,
    name = name,
    amount = amount,
    unit = unit,
    day = day,
    time = time,
    status = record?.let { DoseStatus.valueOf(it.status) } ?: DoseStatus.PENDING,
    takenAt = record?.takenAt?.let(Instant::parse),
)

private fun PlannerMedicine.toEntity(updatedAt: String) = MedicineEntity(
    id = id,
    accountId = accountId,
    plannerId = plannerId,
    name = name,
    amount = amount,
    unit = unit.name,
    repeatKind = when (repeat) {
        MedicineRepeat.Daily -> "DAILY"
        is MedicineRepeat.Weekdays -> "WEEKDAYS"
        is MedicineRepeat.Period -> "PERIOD"
    },
    repeatWeekdays = (repeat as? MedicineRepeat.Weekdays)?.days
        ?.sortedBy(DayOfWeek::getValue)?.joinToString(",") { it.value.toString() }.orEmpty(),
    startDate = startDate.toString(),
    endDate = (repeat as? MedicineRepeat.Period)?.end?.toString(),
    status = status.name,
    updatedAt = updatedAt,
    stockAmount = stock?.amount,
    stockCapacity = stock?.capacity,
    stockThreshold = stock?.threshold,
)

private fun MedicineEntity.toDomain(times: Set<LocalTime>) = PlannerMedicine(
    id = id,
    accountId = accountId,
    plannerId = plannerId,
    name = name,
    amount = amount,
    unit = DoseUnit.valueOf(unit),
    times = times,
    repeat = when (repeatKind) {
        "WEEKDAYS" -> MedicineRepeat.Weekdays(
            repeatWeekdays.split(',').filter(String::isNotBlank).map { DayOfWeek.of(it.toInt()) }.toSet(),
        )
        "PERIOD" -> MedicineRepeat.Period(LocalDate.parse(startDate), LocalDate.parse(endDate))
        "DAILY" -> MedicineRepeat.Daily
        else -> error("Repetição de Remédio desconhecida: $repeatKind")
    },
    startDate = LocalDate.parse(startDate),
    status = MedicineStatus.valueOf(status),
    stock = stockAmount?.let { MedicineStock(it, stockCapacity ?: it, stockThreshold ?: 0) },
)
