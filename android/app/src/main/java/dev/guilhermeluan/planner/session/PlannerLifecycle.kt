package dev.guilhermeluan.planner.session

import dev.guilhermeluan.planner.backup.BackupExporter
import org.json.JSONObject

class PlannerLifecycle(
    private val repository: LocalPlannerRepository,
    private val settings: AccountSettingsRepository,
    private val migration: MigrationToLocal,
    private val backup: BackupExporter,
) {
    private var active: LocalPlanner? = null

    suspend fun restore(): LocalPlanner? {
        migration.runIfNeeded()
        return repository.restorePlanner().also { active = it }
    }

    suspend fun create(name: String, timezone: String): LocalPlanner =
        repository.createPlanner(name, timezone).also { active = it }

    suspend fun updateName(name: String): LocalPlanner {
        val current = requireActive()
        settings.updateName(current.account.id, name)
        return current.copy(account = current.account.copy(username = name.trim())).also { active = it }
    }

    suspend fun updateTimezone(timezone: String): LocalPlanner {
        val current = requireActive()
        settings.updateTimezone(current.account.id, timezone)
        return current.copy(account = current.account.copy(timezone = timezone)).also { active = it }
    }

    suspend fun exportBackup(): JSONObject = backup.export(requireActive().account.id)

    private fun requireActive(): LocalPlanner = checkNotNull(active) { "Planner ativo não encontrado" }
}
