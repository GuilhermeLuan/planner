package dev.guilhermeluan.planner.session

import dev.guilhermeluan.planner.storage.PlannerDatabase
import dev.guilhermeluan.planner.storage.SessionMetadataEntity
import java.time.ZoneId

class MigrationToLocal(
    private val database: PlannerDatabase,
    private val deviceTimezone: () -> String,
    private val clearServerConfig: () -> Unit = {},
    private val clearSecrets: suspend () -> Unit = {},
) {
    private val sessionDao = database.sessionDao()
    private val plannerDao = database.plannerDao()

    suspend fun runIfNeeded() {
        val accountId = sessionDao.activeAccountId() ?: return
        if (sessionDao.metadata(MIGRATION_MARKER_KEY) != null) return

        val account = sessionDao.account(accountId) ?: return

        val timezone = runCatching { ZoneId.of(account.timezone).id }
            .getOrDefault(deviceTimezone())

        val trimmedName = account.username.trim()
        if (trimmedName.isEmpty()) {
            sessionDao.clearActiveAccess()
            plannerDao.clearOutbox(accountId)
            plannerDao.clearSyncState(accountId)
            sessionDao.clearMetadata(BLOCKED_REASON_KEY)
            clearServerConfig()
            clearSecrets()
            return
        }

        sessionDao.updateAccount(accountId, trimmedName, timezone, mustChangePassword = false)

        plannerDao.clearOutbox(accountId)
        plannerDao.clearSyncState(accountId)
        sessionDao.clearMetadata(BLOCKED_REASON_KEY)

        clearServerConfig()
        clearSecrets()

        sessionDao.saveMetadata(SessionMetadataEntity(MIGRATION_MARKER_KEY, "1"))
    }

    private companion object {
        const val MIGRATION_MARKER_KEY = "migration_local_v1"
        const val BLOCKED_REASON_KEY = "session_blocked_reason"
    }
}
