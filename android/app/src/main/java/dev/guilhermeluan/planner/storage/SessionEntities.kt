package dev.guilhermeluan.planner.storage

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val username: String,
    val timezone: String,
    val mustChangePassword: Boolean,
)

@Entity(
    tableName = "planners",
    indices = [Index("accountId")],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PlannerEntity(
    @PrimaryKey val id: String,
    val accountId: String,
)

@Entity(tableName = "session_metadata")
data class SessionMetadataEntity(
    @PrimaryKey val key: String,
    val value: String,
)
