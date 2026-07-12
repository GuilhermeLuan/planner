package dev.guilhermeluan.planner.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PlannerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun upsertTask(task: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun enqueue(operation: OutboxEntity)

    @androidx.room.Transaction
    open suspend fun writeTaskMutation(task: TaskEntity, operation: OutboxEntity) {
        upsertTask(task)
        enqueue(operation)
    }

    @androidx.room.Transaction
    open suspend fun writeAccountSettingsMutation(operation: OutboxEntity) {
        enqueue(operation)
    }

    @Query(
        """
        SELECT * FROM tasks
        WHERE accountId = :accountId AND day = :day AND archived = 0
        ORDER BY CASE WHEN time IS NULL THEN 1 ELSE 0 END, time, title
        """,
    )
    abstract fun observeTasks(accountId: String, day: String): Flow<List<TaskEntity>>

    @Query(
        """
        SELECT * FROM tasks
        WHERE accountId = :accountId AND day = :day AND archived = 1
        ORDER BY CASE WHEN time IS NULL THEN 1 ELSE 0 END, time, title
        """,
    )
    abstract fun observeArchivedTasks(accountId: String, day: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE accountId = :accountId AND archived = 0 AND time IS NOT NULL")
    abstract fun observeScheduledTasks(accountId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE accountId = :accountId AND id = :taskId")
    abstract suspend fun task(accountId: String, taskId: String): TaskEntity?

    @Query("SELECT * FROM outbox WHERE accountId = :accountId ORDER BY clientUpdatedAt, operationId")
    abstract suspend fun pendingOperations(accountId: String): List<OutboxEntity>

    @Query("DELETE FROM outbox WHERE operationId = :operationId")
    abstract suspend fun deleteOperation(operationId: String)

    @Query("UPDATE outbox SET lastError = :message WHERE operationId = :operationId")
    abstract suspend fun markOperationFailed(operationId: String, message: String)

    @Query("SELECT COUNT(*) FROM outbox WHERE accountId = :accountId")
    abstract fun observePendingOperationCount(accountId: String): Flow<Int>

    @Query("SELECT lastError FROM outbox WHERE accountId = :accountId AND lastError IS NOT NULL ORDER BY clientUpdatedAt LIMIT 1")
    abstract fun observeSyncError(accountId: String): Flow<String?>

    @Query("SELECT cursor FROM sync_state WHERE accountId = :accountId")
    abstract suspend fun syncCursor(accountId: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun upsertRemoteTasks(tasks: List<TaskEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun upsertSyncState(state: SyncStateEntity)

    @androidx.room.Transaction
    open suspend fun applyPull(tasks: List<TaskEntity>, state: SyncStateEntity) {
        if (tasks.isNotEmpty()) upsertRemoteTasks(tasks)
        upsertSyncState(state)
    }
}
