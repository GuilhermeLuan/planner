package dev.guilhermeluan.planner.storage

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "outbox",
    indices = [Index("accountId"), Index(value = ["accountId", "clientUpdatedAt"])],
)
data class OutboxEntity(
    @PrimaryKey val operationId: String,
    val accountId: String,
    val entityType: String,
    val entityId: String,
    val kind: String,
    val payloadJson: String,
    val clientUpdatedAt: String,
    val lastError: String? = null,
)

