package dev.guilhermeluan.planner.sync

import dev.guilhermeluan.planner.storage.OutboxEntity
import dev.guilhermeluan.planner.storage.PlannerDatabase
import dev.guilhermeluan.planner.storage.SyncStateEntity
import dev.guilhermeluan.planner.storage.TaskEntity
import dev.guilhermeluan.planner.storage.RoutineEntity
import dev.guilhermeluan.planner.storage.RoutineOccurrenceEntity
import org.json.JSONObject
import java.time.Clock
import java.time.ZoneId

class SyncEngine(
    private val database: PlannerDatabase,
    private val api: SyncApi,
    private val session: SyncSession,
    private val clock: Clock,
) {
    private val dao = database.plannerDao()
    private val routineDao = database.routineDao()

    suspend fun syncOnce(): SyncStatus {
        val pending = dao.pendingOperations(session.accountId)
        var rejected: String? = null
        if (pending.isNotEmpty()) {
            val results = api.push(session.token, pending.map(OutboxEntity::toOperation))
            val byId = results.associateBy(PushResult::operationId)
            for (operation in pending) {
                when (val result = byId[operation.operationId]) {
                    null -> rejected = "O servidor não confirmou todas as alterações."
                    else -> when (result.status) {
                        PushResultStatus.ACCEPTED, PushResultStatus.DUPLICATE ->
                            dao.deleteOperation(operation.operationId)
                        PushResultStatus.REJECTED -> {
                            val message = result.error ?: "Alteração rejeitada pelo servidor."
                            dao.markOperationFailed(operation.operationId, message)
                            rejected = message
                        }
                    }
                }
            }
        }
        var cursor = dao.syncCursor(session.accountId) ?: 0L
        do {
            val page = api.pull(session.token, cursor, PULL_LIMIT)
            val tasks = page.changes
                .filter { it.entityType == "task" }
                .map { it.toTaskEntity(session.accountId) }
            val routines = page.changes
                .filter { it.entityType == "routine" }
                .map { it.toRoutineEntity(session.accountId) }
            val occurrences = page.changes
                .filter { it.entityType == "routine_occurrence" }
                .map { it.toRoutineOccurrenceEntity(session.accountId) }
            page.changes
                .filter { it.entityType == "account_settings" }
                .forEach { change ->
                    val timezone = JSONObject(change.payloadJson).optString("timezone")
                    if (timezone.isNotBlank()) {
                        ZoneId.of(timezone)
                        database.sessionDao().updateTimezone(session.accountId, timezone)
                    }
                }
            dao.applyPull(
                tasks = tasks,
                state = SyncStateEntity(
                    accountId = session.accountId,
                    cursor = page.nextCursor,
                    lastSyncAt = clock.instant().toString(),
                ),
            )
            routineDao.applyPull(routines, occurrences)
            cursor = page.nextCursor
        } while (page.hasMore)
        return rejected?.let(SyncStatus::Failed) ?: SyncStatus.Synced
    }

    private companion object {
        const val PULL_LIMIT = 100
    }
}

private fun OutboxEntity.toOperation() = SyncOperation(
    operationId = operationId,
    entityType = entityType,
    entityId = entityId,
    kind = kind,
    payloadJson = payloadJson,
    clientUpdatedAt = clientUpdatedAt,
)

private fun SyncChange.toTaskEntity(accountId: String): TaskEntity {
    val payload = JSONObject(payloadJson)
    return TaskEntity(
        id = entityId,
        accountId = accountId,
        plannerId = payload.getString("planner_id"),
        title = payload.getString("title"),
        day = payload.getString("day"),
        time = if (payload.isNull("time")) null else payload.getString("time"),
        status = payload.optString("status", "pending").uppercase(),
        archived = kind == "archive" || payload.optBoolean("archived", false),
        version = version,
        updatedAt = updatedAt,
    )
}

private fun SyncChange.toRoutineEntity(accountId: String): RoutineEntity {
    val payload = JSONObject(payloadJson)
    val weekdays = payload.optJSONArray("weekdays") ?: org.json.JSONArray()
    return RoutineEntity(
        id = entityId,
        accountId = accountId,
        plannerId = payload.getString("planner_id"),
        title = payload.getString("title"),
        weekdays = (0 until weekdays.length()).joinToString(",") { weekdays.getInt(it).toString() },
        startDate = payload.getString("start_date"),
        time = if (payload.isNull("time")) null else payload.getString("time"),
        status = if (kind == "archive" || payload.optBoolean("archived", false)) "ARCHIVED" else "ACTIVE",
        version = version,
        updatedAt = updatedAt,
    )
}

private fun SyncChange.toRoutineOccurrenceEntity(accountId: String): RoutineOccurrenceEntity {
    val payload = JSONObject(payloadJson)
    return RoutineOccurrenceEntity(
        id = entityId,
        accountId = accountId,
        routineId = payload.getString("routine_id"),
        title = payload.getString("title"),
        day = payload.getString("day"),
        time = if (payload.isNull("time")) null else payload.getString("time"),
        status = payload.optString("status", "pending").uppercase(),
        version = version,
        updatedAt = updatedAt,
    )
}
