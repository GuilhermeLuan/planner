package dev.guilhermeluan.planner.session

import dev.guilhermeluan.planner.storage.AccountEntity
import dev.guilhermeluan.planner.storage.PlannerDatabase
import dev.guilhermeluan.planner.storage.PlannerEntity

class RoomSessionStateStore(database: PlannerDatabase) : SessionStateStore {
    private val dao = database.sessionDao()

    override suspend fun saveActive(account: Account, planner: Planner) {
        dao.saveActive(
            account = AccountEntity(
                id = account.id,
                username = account.username,
                timezone = account.timezone,
                mustChangePassword = account.mustChangePassword,
            ),
            planner = PlannerEntity(id = planner.id, accountId = planner.accountId),
        )
    }

    override suspend fun readActive(): Pair<Account, Planner>? {
        val accountId = dao.activeAccountId() ?: return null
        val account = dao.account(accountId) ?: return null
        val planner = dao.planner(accountId) ?: return null
        return Account(
            id = account.id,
            username = account.username,
            timezone = account.timezone,
            mustChangePassword = account.mustChangePassword,
        ) to Planner(id = planner.id, accountId = planner.accountId)
    }

    override suspend fun clearActiveAccess() {
        dao.clearActiveAccess()
    }

    override suspend fun markBlocked(reason: String) {
        dao.saveMetadata(dev.guilhermeluan.planner.storage.SessionMetadataEntity(BLOCKED_REASON_KEY, reason))
    }

    override suspend fun blockedReason(): String? = dao.metadata(BLOCKED_REASON_KEY)

    override suspend fun clearBlocked() {
        dao.clearMetadata(BLOCKED_REASON_KEY)
    }

    override fun observeBlockedReason() = dao.observeMetadata(BLOCKED_REASON_KEY)

    private companion object {
        const val BLOCKED_REASON_KEY = "session_blocked_reason"
    }
}
