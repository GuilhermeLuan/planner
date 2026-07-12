package dev.guilhermeluan.planner.storage

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val accountId: String,
    val cursor: Long,
    val lastSyncAt: String,
    val lastError: String? = null,
)

