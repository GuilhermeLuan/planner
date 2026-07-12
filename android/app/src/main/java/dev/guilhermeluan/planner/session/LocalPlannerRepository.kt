package dev.guilhermeluan.planner.session

import dev.guilhermeluan.planner.storage.AccountEntity
import dev.guilhermeluan.planner.storage.PlannerDatabase
import dev.guilhermeluan.planner.storage.PlannerEntity
import java.time.ZoneId
import java.util.UUID

data class LocalPlanner(val account: Account, val planner: Planner)

class LocalPlannerRepository(
    database: PlannerDatabase,
    private val nextId: () -> String = { UUID.randomUUID().toString() },
) {
    private val dao = database.sessionDao()

    suspend fun restorePlanner(): LocalPlanner? {
        val accountId = dao.activeAccountId() ?: return null
        val account = dao.account(accountId) ?: return null
        val planner = dao.planner(accountId) ?: return null
        return LocalPlanner(account.toDomain(), planner.toDomain())
    }

    suspend fun createPlanner(name: String, timezone: String): LocalPlanner {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "Informe seu nome" }
        val normalizedTimezone = ZoneId.of(timezone).id
        val account = Account(
            id = nextId(),
            username = normalizedName,
            timezone = normalizedTimezone,
            mustChangePassword = false,
        )
        val planner = Planner(id = nextId(), accountId = account.id)
        dao.createLocalPlanner(account.toEntity(), planner.toEntity())
        return LocalPlanner(account, planner)
    }
}

private fun AccountEntity.toDomain() = Account(id, username, timezone, mustChangePassword)
private fun PlannerEntity.toDomain() = Planner(id, accountId)
private fun Account.toEntity() = AccountEntity(id, username, timezone, mustChangePassword)
private fun Planner.toEntity() = PlannerEntity(id, accountId)
