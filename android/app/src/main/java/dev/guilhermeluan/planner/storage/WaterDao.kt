package dev.guilhermeluan.planner.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WaterDao {
    @Query("SELECT * FROM water_intakes WHERE accountId = :accountId AND day BETWEEN :from AND :to")
    fun observeIntakes(accountId: String, from: String, to: String): Flow<List<WaterIntakeEntity>>

    /** Soma no próprio SQL, para que toques concorrentes não leiam um total desatualizado. */
    @Query("UPDATE water_intakes SET totalMl = totalMl + :ml, updatedAt = :updatedAt WHERE accountId = :accountId AND day = :day")
    suspend fun addToIntake(accountId: String, day: String, ml: Int, updatedAt: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertIntake(intake: WaterIntakeEntity)

    @Query("SELECT * FROM water_goals WHERE accountId = :accountId ORDER BY validFrom")
    fun observeGoals(accountId: String): Flow<List<WaterGoalEntity>>

    @Query("DELETE FROM water_goals WHERE accountId = :accountId AND validFrom > :from")
    suspend fun deleteGoalsAfter(accountId: String, from: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGoal(goal: WaterGoalEntity)

    @Query("SELECT MIN(day) FROM water_intakes WHERE accountId = :accountId")
    suspend fun earliestIntakeDay(accountId: String): String?

    @Query("SELECT MIN(day) FROM water_intakes WHERE accountId = :accountId")
    fun observeEarliestIntakeDay(accountId: String): Flow<String?>

    @Query("SELECT * FROM water_reminder_settings WHERE accountId = :accountId")
    fun observeReminderSettings(accountId: String): Flow<WaterReminderSettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReminderSettings(settings: WaterReminderSettingsEntity)

    @Query("SELECT * FROM water_intakes WHERE accountId = :accountId")
    suspend fun intakesByAccount(accountId: String): List<WaterIntakeEntity>

    @Query("SELECT * FROM water_goals WHERE accountId = :accountId")
    suspend fun goalsByAccount(accountId: String): List<WaterGoalEntity>

    @Query("SELECT * FROM water_reminder_settings WHERE accountId = :accountId")
    suspend fun reminderSettings(accountId: String): WaterReminderSettingsEntity?
}
