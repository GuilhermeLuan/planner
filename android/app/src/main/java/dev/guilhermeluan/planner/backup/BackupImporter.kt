package dev.guilhermeluan.planner.backup

import androidx.room.withTransaction
import dev.guilhermeluan.planner.storage.AccountEntity
import dev.guilhermeluan.planner.storage.DoseRecordEntity
import dev.guilhermeluan.planner.storage.MedicineArchivedPeriodEntity
import dev.guilhermeluan.planner.storage.MedicineEntity
import dev.guilhermeluan.planner.storage.MedicinePreviousVersionEntity
import dev.guilhermeluan.planner.storage.MedicineTimeEntity
import dev.guilhermeluan.planner.storage.PlannerDatabase
import dev.guilhermeluan.planner.storage.PlannerEntity
import dev.guilhermeluan.planner.storage.RoutineEntity
import dev.guilhermeluan.planner.storage.RoutineOccurrenceEntity
import dev.guilhermeluan.planner.storage.SessionDao
import dev.guilhermeluan.planner.storage.SessionMetadataEntity
import dev.guilhermeluan.planner.storage.TaskEntity
import dev.guilhermeluan.planner.storage.WaterGoalEntity
import dev.guilhermeluan.planner.storage.WaterIntakeEntity
import dev.guilhermeluan.planner.storage.WaterReminderSettingsEntity
import java.time.Clock

class BackupImporter(
    private val database: PlannerDatabase,
    private val clock: Clock,
) {
    /**
     * Substitui todo o Planner local pelo do [backup], numa única transação.
     * Devolve os ids das Tarefas do Planner apagado, para quem chama cancelar os Lembretes delas.
     */
    suspend fun import(backup: Backup): List<String> = database.withTransaction {
        val backupDao = database.backupDao()
        val accountId = backup.account.id
        val plannerId = backup.plannerId
        val now = clock.instant().toString()

        val removedTaskIds = backupDao.allTaskIds()
        backupDao.deleteAllAccounts()
        backupDao.insertAccount(
            AccountEntity(
                id = accountId,
                username = backup.account.name,
                timezone = backup.account.timezone,
                mustChangePassword = false,
            ),
        )
        backupDao.insertPlanner(PlannerEntity(id = plannerId, accountId = accountId))
        backupDao.saveMetadata(SessionMetadataEntity(SessionDao.ACTIVE_ACCOUNT_KEY, accountId))
        backupDao.insertTasks(backup.tasks.map { it.toEntity(accountId, plannerId, now) })
        backupDao.insertRoutines(backup.routines.map { it.toEntity(accountId, plannerId, now) })
        backupDao.insertOccurrences(backup.occurrences.map { it.toEntity(accountId, now) })
        backupDao.insertMedicines(backup.medicines.map { it.toEntity(accountId, plannerId, now) })
        backupDao.insertMedicineTimes(backup.medicines.flatMap { medicine ->
            medicine.times.map { MedicineTimeEntity(medicine.id, it.toString()) }
        })
        backupDao.insertPreviousVersions(backup.medicines.flatMap { medicine ->
            medicine.previousVersions.map { it.toEntity(medicine.id) }
        })
        backupDao.insertArchivedPeriods(backup.medicines.flatMap { medicine ->
            medicine.archivedPeriods.map { it.toEntity(medicine.id) }
        })
        backupDao.insertDoseRecords(backup.doses.map { it.toEntity(accountId, now) })
        backupDao.insertWaterGoals(backup.water.goals.map { it.toEntity(accountId) })
        backupDao.insertWaterIntakes(backup.water.intakes.map { it.toEntity(accountId, now) })
        backup.water.reminder?.let { backupDao.insertWaterReminderSettings(it.toEntity(accountId)) }
        removedTaskIds
    }
}

private fun BackupTask.toEntity(accountId: String, plannerId: String, now: String) = TaskEntity(
    id = id,
    accountId = accountId,
    plannerId = plannerId,
    title = title,
    day = day.toString(),
    time = time?.toString(),
    status = status,
    archived = archived,
    version = 0,
    updatedAt = now,
)

private fun BackupRoutine.toEntity(accountId: String, plannerId: String, now: String) = RoutineEntity(
    id = id,
    accountId = accountId,
    plannerId = plannerId,
    title = title,
    weekdays = weekdays.joinToString(","),
    startDate = startDate.toString(),
    time = time?.toString(),
    status = status,
    version = 0,
    updatedAt = now,
)

private fun BackupOccurrence.toEntity(accountId: String, now: String) = RoutineOccurrenceEntity(
    id = id,
    accountId = accountId,
    routineId = routineId,
    title = title,
    day = day.toString(),
    time = time?.toString(),
    status = status,
    version = 0,
    updatedAt = now,
)

private fun BackupMedicine.toEntity(accountId: String, plannerId: String, now: String) = MedicineEntity(
    id = id,
    accountId = accountId,
    plannerId = plannerId,
    name = name,
    amount = amount,
    unit = unit,
    repeatKind = repeat.kind,
    repeatWeekdays = repeat.weekdays.joinToString(","),
    startDate = repeat.startDate.toString(),
    endDate = repeat.endDate?.toString(),
    status = status,
    updatedAt = now,
    stockAmount = stock?.amount,
    stockCapacity = stock?.capacity,
    stockThreshold = stock?.threshold,
    alarmDelayMinutes = alarmDelayMinutes,
)

private fun BackupMedicineVersion.toEntity(medicineId: String) = MedicinePreviousVersionEntity(
    medicineId = medicineId,
    until = until.toString(),
    name = name,
    amount = amount,
    unit = unit,
    times = times.joinToString(","),
    repeatKind = repeat.kind,
    repeatWeekdays = repeat.weekdays.joinToString(","),
    startDate = repeat.startDate.toString(),
    endDate = repeat.endDate?.toString(),
)

private fun BackupArchivedPeriod.toEntity(medicineId: String) = MedicineArchivedPeriodEntity(
    medicineId = medicineId,
    archivedFrom = from.toString(),
    archivedUntil = until?.toString(),
)

private fun BackupDose.toEntity(accountId: String, now: String) = DoseRecordEntity(
    medicineId = medicineId,
    day = day.toString(),
    time = time.toString(),
    accountId = accountId,
    status = status,
    takenAt = takenAt?.toString(),
    updatedAt = now,
    stockDeducted = stockDeducted,
)

private fun BackupWaterGoal.toEntity(accountId: String) = WaterGoalEntity(
    accountId = accountId,
    validFrom = validFrom.toString(),
    goalMl = goalMl,
)

private fun BackupWaterIntake.toEntity(accountId: String, now: String) = WaterIntakeEntity(
    accountId = accountId,
    day = day.toString(),
    totalMl = totalMl,
    updatedAt = now,
)

private fun BackupWaterReminder.toEntity(accountId: String) = WaterReminderSettingsEntity(
    accountId = accountId,
    enabled = enabled,
    intervalHours = intervalHours,
    windowStart = windowStart.toString(),
    windowEnd = windowEnd.toString(),
)
