package dev.guilhermeluan.planner.tasks

import androidx.room.withTransaction
import dev.guilhermeluan.planner.day.Week
import dev.guilhermeluan.planner.storage.DoseRecordEntity
import dev.guilhermeluan.planner.storage.DoseSnoozeEntity
import dev.guilhermeluan.planner.storage.MedicineEntity
import dev.guilhermeluan.planner.storage.MedicineArchivedPeriodEntity
import dev.guilhermeluan.planner.storage.MedicinePreviousVersionEntity
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
    private val database: PlannerDatabase,
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

    fun observeArchivedItems(accountId: String): Flow<ArchivedItems> = combine(
        dao.observeAllArchivedTasks(accountId),
        routineDao.observeRoutines(accountId),
        observeArchivedMedicines(accountId),
    ) { tasks, routines, medicines ->
        ArchivedItems(
            tasks = tasks.map(TaskEntity::toDomain),
            routines = routines.map { it.toDomain() }.filter { it.status == RoutineStatus.ARCHIVED }
                .sortedBy { it.title.lowercase() },
            medicines = medicines,
        )
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
        val valid = draft.validated()
        val medicine = PlannerMedicine(
            id = idGenerator.nextId(),
            accountId = accountId,
            plannerId = plannerId,
            name = valid.name,
            amount = valid.amount,
            unit = valid.unit,
            times = valid.times,
            repeat = valid.repeat,
            startDate = (valid.repeat as? MedicineRepeat.Period)?.start ?: valid.startDate,
            status = MedicineStatus.ACTIVE,
            stock = valid.toStock(),
        )
        persistMedicine(medicine)
        return medicine
    }

    /**
     * Edita o Remédio a partir de [effectiveFrom], ou mais tarde se já houver Dose registrada (veja
     * [editEffectiveFrom]). O que valia até então fica guardado como versão anterior, então Dias anteriores
     * continuam com o nome, a dose, os horários e a repetição de antes.
     */
    suspend fun editMedicine(
        accountId: String,
        medicineId: String,
        draft: MedicineDraft,
        effectiveFrom: LocalDate,
    ): PlannerMedicine = database.withTransaction {
        // Na mesma transação: uma Dose registrada entre ler o último Dia e gravar mudaria de versão.
        val current = currentMedicine(accountId, medicineId)
        val valid = draft.validated()
        val from = editEffectiveFrom(effectiveFrom, medicineDao.lastRegisteredDay(medicineId)?.let(LocalDate::parse))
        val edited = current.copy(
            name = valid.name,
            amount = valid.amount,
            unit = valid.unit,
            times = valid.times,
            repeat = valid.repeat,
            startDate = when {
                valid.repeat is MedicineRepeat.Period -> valid.repeat.start
                current.repeat is MedicineRepeat.Period -> from
                else -> current.startDate
            },
            stock = valid.toStock(current.stock),
        )
        medicineDao.writeEditedMedicine(
            current.toPreviousVersion(from),
            edited.toEntity(clock.instant().toString()),
            edited.times.map { MedicineTimeEntity(edited.id, it.toString()) },
        )
        edited
    }

    /** Arquiva o Remédio a partir de [day]: dali em diante não há Doses novas, e o que veio antes fica. */
    suspend fun archiveMedicine(accountId: String, medicineId: String, day: LocalDate): PlannerMedicine =
        setMedicineStatus(accountId, medicineId, MedicineStatus.ARCHIVED, day)

    /** Restaura o Remédio em [day]: as Doses voltam dali em diante, sem trazer de volta o período arquivado. */
    suspend fun restoreMedicine(accountId: String, medicineId: String, day: LocalDate): PlannerMedicine =
        setMedicineStatus(accountId, medicineId, MedicineStatus.ACTIVE, day)

    private suspend fun setMedicineStatus(
        accountId: String,
        medicineId: String,
        status: MedicineStatus,
        day: LocalDate,
    ): PlannerMedicine = database.withTransaction {
        val medicine = currentMedicine(accountId, medicineId)
        medicineDao.writeStatusChange(medicineId, status, clock.instant().toString(), day.toString())
        medicine.copy(status = status)
    }

    private suspend fun currentMedicine(accountId: String, medicineId: String): PlannerMedicine {
        val entity = medicineDao.medicine(accountId, medicineId) ?: error("Remédio não encontrado")
        return entity.toDomain(medicineDao.times(medicineId).map { LocalTime.parse(it.time) }.toSet())
    }

    private suspend fun persistMedicine(medicine: PlannerMedicine) {
        medicineDao.writeLocalMedicine(
            medicine.toEntity(clock.instant().toString()),
            medicine.times.map { MedicineTimeEntity(medicine.id, it.toString()) },
        )
    }

    fun observeMedicines(accountId: String): Flow<List<PlannerMedicine>> =
        observeMedicinesWith(accountId, MedicineStatus.ACTIVE)
            .map { medicines -> medicines.sortedBy { it.times.minOrNull() } }

    fun observeArchivedMedicines(accountId: String): Flow<List<PlannerMedicine>> =
        observeMedicinesWith(accountId, MedicineStatus.ARCHIVED)
            .map { medicines -> medicines.sortedBy { it.name.lowercase() } }

    private fun observeMedicinesWith(accountId: String, status: MedicineStatus): Flow<List<PlannerMedicine>> = combine(
        medicineDao.observeMedicines(accountId),
        medicineDao.observeTimes(accountId),
    ) { medicines, times -> assembleMedicines(medicines, times).filter { it.status == status } }

    /** Último Dia com Dose registrada de cada Remédio que tem alguma; define a partir de quando uma edição vale. */
    fun observeLastRegisteredDays(accountId: String): Flow<Map<String, LocalDate>> =
        medicineDao.observeLastRegisteredDays(accountId).map { rows ->
            rows.associate { it.medicineId to LocalDate.parse(it.day) }
        }

    fun observeDoses(accountId: String, day: LocalDate): Flow<List<PlannedDose>> = combine(
        medicineDao.observeMedicines(accountId),
        medicineDao.observeTimes(accountId),
        medicineDao.observeDoseRecords(accountId, day.toString()),
        medicineDao.observePreviousVersions(accountId),
        medicineDao.observeArchivedPeriods(accountId),
    ) { medicineEntities, times, records, previousVersions, archivedPeriods ->
        projectDoses(day, assembleMedicines(medicineEntities, times), records, previousVersions, archivedPeriods)
            .sortedBy(PlannedDose::time)
    }.combine(medicineDao.observeDoseSnoozes(accountId, day.toString())) { doses, snoozes ->
        val snoozedUntil = snoozes.associate { (it.medicineId to LocalTime.parse(it.time)) to Instant.parse(it.snoozedUntil) }
        doses.map { it.copy(snoozedUntil = snoozedUntil[it.medicineId to it.time]) }
    }

    /** As Doses de cada Dia de [days], na mesma projeção de [observeDoses], em ordem de Dia e horário. */
    fun observeDosesBetween(accountId: String, days: ClosedRange<LocalDate>): Flow<List<PlannedDose>> = combine(
        medicineDao.observeMedicines(accountId),
        medicineDao.observeTimes(accountId),
        medicineDao.observeDoseRecordsBetween(accountId, days.start.toString(), days.endInclusive.toString()),
        medicineDao.observePreviousVersions(accountId),
        medicineDao.observeArchivedPeriods(accountId),
    ) { medicineEntities, times, records, previousVersions, archivedPeriods ->
        val medicines = assembleMedicines(medicineEntities, times)
        val recordsByDay = records.groupBy { it.day }
        Week.days(days).flatMap { day ->
            projectDoses(day, medicines, recordsByDay[day.toString()].orEmpty(), previousVersions, archivedPeriods)
                .sortedBy(PlannedDose::time)
        }
    }

    /**
     * Doses de um Dia: as projetadas pela versão do Remédio que valia naquele Dia (menos nos Períodos
     * arquivados) e as já registradas. Com o Remédio arquivado, as registradas do Dia do arquivamento e de
     * antes seguem visíveis; as de Dias depois dele ficam escondidas até restaurar.
     */
    private fun projectDoses(
        day: LocalDate,
        medicines: List<PlannerMedicine>,
        records: List<DoseRecordEntity>,
        previousVersions: List<MedicinePreviousVersionEntity>,
        archivedPeriods: List<MedicineArchivedPeriodEntity>,
    ): List<PlannedDose> {
        val recordsByMedicine = records.groupBy { it.medicineId }
        val previousVersionsByMedicine = previousVersions.groupBy { it.medicineId }
        val archivedPeriodsByMedicine = archivedPeriods.groupBy { it.medicineId }
        return medicines.flatMap { medicine ->
            val periods = archivedPeriodsByMedicine[medicine.id].orEmpty().map { it.toDomain() }
            val hidesRegistered = periods.any { it.until == null && day.isAfter(it.from) }
            val registered = recordsByMedicine[medicine.id].orEmpty()
                .takeUnless { hidesRegistered }.orEmpty()
                .associateBy { LocalTime.parse(it.time) }
            val version = medicine.versionOn(day, previousVersionsByMedicine[medicine.id].orEmpty().map { it.toDomain() })
            val archived = periods.any { it.covers(day) }
            val projected = if (!archived && MedicineRecurrence.occursOn(version.copy(status = MedicineStatus.ACTIVE), day)) {
                version.times
            } else {
                emptySet()
            }
            (projected + registered.keys).map { time -> version.doseOn(day, time, registered[time]) }
        }
    }

    private fun assembleMedicines(
        medicines: List<MedicineEntity>,
        times: List<MedicineTimeEntity>,
    ): List<PlannerMedicine> {
        val timesByMedicine = times.groupBy(MedicineTimeEntity::medicineId)
        return medicines.map { it.toDomain(timesByMedicine[it.id].orEmpty().map { t -> LocalTime.parse(t.time) }.toSet()) }
    }

    /** Adia o Lembrete da Dose para [until]; a Dose continua pendente. */
    suspend fun snoozeDose(accountId: String, medicineId: String, day: LocalDate, time: LocalTime, until: Instant) {
        medicineDao.upsertDoseSnooze(
            DoseSnoozeEntity(medicineId, day.toString(), time.toString(), accountId, until.toString()),
        )
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
        database.withTransaction {
            // A dose descontada é a da versão que vale no Dia, a mesma que a Dose mostra.
            val version = currentMedicine(accountId, medicineId)
                .versionOn(day, medicineDao.previousVersions(medicineId).map { it.toDomain() })
            medicineDao.writeDoseStatus(
                accountId = accountId,
                medicineId = medicineId,
                day = day.toString(),
                time = time.toString(),
                status = status.name,
                updatedAt = now,
                amount = version.amount,
            )
        }
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
    repeatKind = repeat.columns().kind,
    repeatWeekdays = repeat.columns().weekdays,
    startDate = startDate.toString(),
    endDate = repeat.columns().endDate,
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
    repeat = RepeatColumns(repeatKind, repeatWeekdays, endDate).decode(startDate),
    startDate = LocalDate.parse(startDate),
    status = MedicineStatus.valueOf(status),
    stock = stockAmount?.let { MedicineStock(it, stockCapacity ?: it, stockThreshold ?: 0) },
)

private fun PlannerMedicine.toPreviousVersion(until: LocalDate) = MedicinePreviousVersionEntity(
    medicineId = id,
    until = until.toString(),
    name = name,
    amount = amount,
    unit = unit.name,
    times = times.sorted().joinToString(",") { it.toString() },
    repeatKind = repeat.columns().kind,
    repeatWeekdays = repeat.columns().weekdays,
    startDate = startDate.toString(),
    endDate = repeat.columns().endDate,
)

private fun MedicinePreviousVersionEntity.toDomain() = MedicinePreviousVersion(
    until = LocalDate.parse(until),
    name = name,
    amount = amount,
    unit = DoseUnit.valueOf(unit),
    times = times.split(',').filter(String::isNotBlank).map(LocalTime::parse).toSet(),
    repeat = RepeatColumns(repeatKind, repeatWeekdays, endDate).decode(startDate),
    startDate = LocalDate.parse(startDate),
)

/** Como a repetição é gravada nas colunas de Remédio e de versão anterior. */
private data class RepeatColumns(val kind: String, val weekdays: String, val endDate: String?) {
    fun decode(startDate: String): MedicineRepeat = when (kind) {
        "WEEKDAYS" -> MedicineRepeat.Weekdays(
            weekdays.split(',').filter(String::isNotBlank).map { DayOfWeek.of(it.toInt()) }.toSet(),
        )
        "PERIOD" -> MedicineRepeat.Period(LocalDate.parse(startDate), LocalDate.parse(endDate))
        "DAILY" -> MedicineRepeat.Daily
        else -> error("Repetição de Remédio desconhecida: $kind")
    }
}

private fun MedicineRepeat.columns() = when (this) {
    MedicineRepeat.Daily -> RepeatColumns("DAILY", "", null)
    is MedicineRepeat.Weekdays -> RepeatColumns(
        "WEEKDAYS",
        days.sortedBy(DayOfWeek::getValue).joinToString(",") { it.value.toString() },
        null,
    )
    is MedicineRepeat.Period -> RepeatColumns("PERIOD", "", end.toString())
}

private fun MedicineArchivedPeriodEntity.toDomain() = MedicineArchivedPeriod(
    from = LocalDate.parse(archivedFrom),
    until = archivedUntil?.let(LocalDate::parse),
)
