package dev.guilhermeluan.planner.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class RoutineDao {
    @Upsert
    protected abstract suspend fun upsertRoutine(routine: RoutineEntity)

    suspend fun writeLocalRoutine(routine: RoutineEntity) = upsertRoutine(routine)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun upsertOccurrence(occurrence: RoutineOccurrenceEntity)

    suspend fun writeLocalOccurrence(occurrence: RoutineOccurrenceEntity) = upsertOccurrence(occurrence)

    @Query("SELECT * FROM routines WHERE accountId = :accountId")
    abstract fun observeRoutines(accountId: String): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines WHERE accountId = :accountId AND id = :routineId")
    abstract suspend fun routine(accountId: String, routineId: String): RoutineEntity?

    @Query("SELECT * FROM routine_occurrences WHERE accountId = :accountId AND `day` = :day")
    abstract fun observeOccurrences(accountId: String, day: String): Flow<List<RoutineOccurrenceEntity>>

    @Query("SELECT * FROM routine_occurrences WHERE accountId = :accountId AND id = :occurrenceId")
    abstract suspend fun occurrence(accountId: String, occurrenceId: String): RoutineOccurrenceEntity?

    @Query("SELECT * FROM routines WHERE accountId = :accountId")
    abstract suspend fun routinesByAccount(accountId: String): List<RoutineEntity>

    @Query("SELECT * FROM routine_occurrences WHERE accountId = :accountId")
    abstract suspend fun occurrencesByAccount(accountId: String): List<RoutineOccurrenceEntity>

}
