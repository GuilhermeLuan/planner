package dev.guilhermeluan.planner.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
abstract class BackupDao {
    @Query("SELECT id FROM tasks")
    abstract suspend fun allTaskIds(): List<String>

    /** Apaga todas as Contas; o CASCADE das tabelas filhas leva junto o Planner inteiro. */
    @Query("DELETE FROM accounts")
    abstract suspend fun deleteAllAccounts()

    @Insert
    abstract suspend fun insertAccount(account: AccountEntity)

    @Insert
    abstract suspend fun insertPlanner(planner: PlannerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun saveMetadata(metadata: SessionMetadataEntity)

    @Insert
    abstract suspend fun insertTasks(tasks: List<TaskEntity>)

    @Insert
    abstract suspend fun insertRoutines(routines: List<RoutineEntity>)

    @Insert
    abstract suspend fun insertOccurrences(occurrences: List<RoutineOccurrenceEntity>)

    @Insert
    abstract suspend fun insertMedicines(medicines: List<MedicineEntity>)

    @Insert
    abstract suspend fun insertMedicineTimes(times: List<MedicineTimeEntity>)

    @Insert
    abstract suspend fun insertPreviousVersions(versions: List<MedicinePreviousVersionEntity>)

    @Insert
    abstract suspend fun insertArchivedPeriods(periods: List<MedicineArchivedPeriodEntity>)

    @Insert
    abstract suspend fun insertDoseRecords(records: List<DoseRecordEntity>)

    @Insert
    abstract suspend fun insertWaterGoals(goals: List<WaterGoalEntity>)

    @Insert
    abstract suspend fun insertWaterIntakes(intakes: List<WaterIntakeEntity>)

    @Insert
    abstract suspend fun insertWaterReminderSettings(settings: WaterReminderSettingsEntity)
}
