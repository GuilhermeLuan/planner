package dev.guilhermeluan.planner.session

import dev.guilhermeluan.planner.storage.PlannerDatabase
import java.time.ZoneId

class AccountSettingsRepository(
    database: PlannerDatabase,
) {
    private val sessionDao = database.sessionDao()

    suspend fun updateTimezone(accountId: String, timezone: String) {
        val normalized = timezone.trim()
        require(normalized.isNotEmpty()) { "Informe o Fuso da Conta" }
        require(AccountTimezones.isValid(normalized)) { "Fuso da Conta inválido" }
        sessionDao.updateTimezone(accountId, normalized)
    }

    suspend fun updateName(accountId: String, name: String) {
        val normalized = name.trim()
        require(normalized.isNotEmpty()) { "Informe o nome" }
        sessionDao.updateName(accountId, normalized)
    }
}

object AccountTimezones {
    fun detected(): String = ZoneId.systemDefault().id

    fun isValid(value: String): Boolean = runCatching { ZoneId.of(value) }.isSuccess
}
