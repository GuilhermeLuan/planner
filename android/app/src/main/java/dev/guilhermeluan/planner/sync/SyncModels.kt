package dev.guilhermeluan.planner.sync

data class SyncSession(val accountId: String, val token: String)

data class SyncOperation(
    val operationId: String,
    val entityType: String,
    val entityId: String,
    val kind: String,
    val payloadJson: String,
    val clientUpdatedAt: String,
)

enum class PushResultStatus { ACCEPTED, DUPLICATE, REJECTED }

data class PushResult(
    val operationId: String,
    val status: PushResultStatus,
    val version: Long? = null,
    val error: String? = null,
)

data class SyncChange(
    val cursor: Long,
    val operationId: String,
    val entityType: String,
    val entityId: String,
    val kind: String,
    val payloadJson: String,
    val version: Long,
    val updatedAt: String,
)

data class PullPage(
    val changes: List<SyncChange>,
    val nextCursor: Long,
    val hasMore: Boolean,
)

sealed interface SyncStatus {
    data object Synced : SyncStatus
    data class Failed(val message: String) : SyncStatus
}

interface SyncApi {
    suspend fun push(token: String, operations: List<SyncOperation>): List<PushResult>
    suspend fun pull(token: String, cursor: Long, limit: Int): PullPage
}

