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

    suspend fun writeLocalTask(task: TaskEntity) = upsertTask(task)

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

    @Query(
        """
        SELECT DISTINCT day FROM tasks
        WHERE accountId = :accountId AND archived = 0 AND day BETWEEN :from AND :to
        """,
    )
    abstract fun observeTaskDays(accountId: String, from: String, to: String): Flow<List<String>>

    @Query("SELECT * FROM tasks WHERE accountId = :accountId AND archived = 0 AND time IS NOT NULL")
    abstract fun observeScheduledTasks(accountId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE accountId = :accountId AND id = :taskId")
    abstract suspend fun task(accountId: String, taskId: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE accountId = :accountId ORDER BY day, time, title")
    abstract suspend fun tasksByAccount(accountId: String): List<TaskEntity>

    @Query("SELECT MIN(day) FROM tasks WHERE accountId = :accountId")
    abstract suspend fun earliestTaskDay(accountId: String): String?

    @Query("SELECT * FROM tasks WHERE accountId = :accountId AND archived = 1 ORDER BY day, time, title")
    abstract fun observeAllArchivedTasks(accountId: String): Flow<List<TaskEntity>>
}
