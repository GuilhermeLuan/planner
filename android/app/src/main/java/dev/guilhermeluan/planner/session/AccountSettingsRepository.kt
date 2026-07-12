package dev.guilhermeluan.planner.session

import dev.guilhermeluan.planner.storage.OutboxEntity
import dev.guilhermeluan.planner.storage.PlannerDatabase
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject
import java.time.Clock
import java.time.ZoneId

class AccountSettingsRepository(
    database: PlannerDatabase,
    private val idGenerator: dev.guilhermeluan.planner.tasks.IdGenerator,
    private val clock: Clock,
) {
    private val sessionDao = database.sessionDao()
    private val plannerDao = database.plannerDao()

    suspend fun updateTimezone(accountId: String, timezone: String) {
        val normalized = timezone.trim()
        require(normalized.isNotEmpty()) { "Informe o Fuso da Conta" }
        require(AccountTimezones.isValid(normalized)) { "Fuso da Conta inválido" }
        sessionDao.updateTimezone(accountId, normalized)
        val updatedAt = clock.instant().toString()
        plannerDao.writeAccountSettingsMutation(
            OutboxEntity(
                operationId = idGenerator.nextId(),
                accountId = accountId,
                entityType = "account_settings",
                entityId = accountId,
                kind = "upsert",
                payloadJson = JSONObject().put("timezone", normalized).toString(),
                clientUpdatedAt = updatedAt,
            ),
        )
    }
}

object AccountTimezones {
    fun detected(): String = ZoneId.systemDefault().id

    fun isValid(value: String): Boolean = runCatching { ZoneId.of(value) }.isSuccess
}
