package dev.guilhermeluan.planner.storage

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "routines",
    indices = [Index("accountId"), Index("plannerId")],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PlannerEntity::class,
            parentColumns = ["id"],
            childColumns = ["plannerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class RoutineEntity(
    @androidx.room.PrimaryKey val id: String,
    val accountId: String,
    val plannerId: String,
    val title: String,
    val weekdays: String,
    val startDate: String,
    val time: String?,
    val status: String,
    val version: Long,
    val updatedAt: String,
)

@Entity(
    tableName = "routine_occurrences",
    indices = [Index("accountId"), Index("routineId"), Index(value = ["accountId", "day"])],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class RoutineOccurrenceEntity(
    @androidx.room.PrimaryKey val id: String,
    val accountId: String,
    val routineId: String,
    val title: String,
    val day: String,
    val time: String?,
    val status: String,
    val version: Long,
    val updatedAt: String,
)
