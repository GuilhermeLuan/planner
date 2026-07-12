package dev.guilhermeluan.planner.storage

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tasks",
    indices = [Index("accountId"), Index("plannerId"), Index(value = ["accountId", "day"])],
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
data class TaskEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val plannerId: String,
    val title: String,
    val day: String,
    val time: String?,
    val status: String,
    val archived: Boolean,
    val version: Long,
    val updatedAt: String,
)

