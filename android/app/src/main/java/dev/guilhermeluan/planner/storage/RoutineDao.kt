package dev.guilhermeluan.planner.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun enqueue(operation: OutboxEntity)

    @Transaction
    open suspend fun writeRoutineMutation(routine: RoutineEntity, operation: OutboxEntity) {
        upsertRoutine(routine)
        enqueue(operation)
    }

    @Transaction
    open suspend fun writeOccurrenceMutation(
        occurrence: RoutineOccurrenceEntity,
        operation: OutboxEntity,
    ) {
        upsertOccurrence(occurrence)
        enqueue(operation)
    }

    @Query("SELECT * FROM routines WHERE accountId = :accountId")
    abstract fun observeRoutines(accountId: String): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines WHERE accountId = :accountId AND id = :routineId")
    abstract suspend fun routine(accountId: String, routineId: String): RoutineEntity?

    @Query("SELECT * FROM routine_occurrences WHERE accountId = :accountId AND `day` = :day")
    abstract fun observeOccurrences(accountId: String, day: String): Flow<List<RoutineOccurrenceEntity>>

    @Query("SELECT * FROM routine_occurrences WHERE accountId = :accountId AND id = :occurrenceId")
    abstract suspend fun occurrence(accountId: String, occurrenceId: String): RoutineOccurrenceEntity?

    @Upsert
    protected abstract suspend fun upsertRemoteRoutines(routines: List<RoutineEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun upsertRemoteOccurrences(occurrences: List<RoutineOccurrenceEntity>)

    @Transaction
    open suspend fun applyPull(
        routines: List<RoutineEntity>,
        occurrences: List<RoutineOccurrenceEntity>,
    ) {
        if (routines.isNotEmpty()) upsertRemoteRoutines(routines)
        if (occurrences.isNotEmpty()) upsertRemoteOccurrences(occurrences)
    }
}
