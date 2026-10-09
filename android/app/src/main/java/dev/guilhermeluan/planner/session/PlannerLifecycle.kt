package dev.guilhermeluan.planner.session

import android.util.Log
import dev.guilhermeluan.planner.backup.Backup
import dev.guilhermeluan.planner.backup.BackupExporter
import dev.guilhermeluan.planner.backup.BackupImporter
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

class PlannerLifecycle(
    private val repository: LocalPlannerRepository,
    private val settings: AccountSettingsRepository,
    private val migration: MigrationToLocal,
    private val backup: BackupExporter,
    private val importer: BackupImporter,
    private val reschedule: suspend (removedTaskIds: List<String>) -> Unit,
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

    /** Substitui o Planner local pelo do [backup] e reagenda Lembretes e Alarmes. */
    suspend fun importBackup(backup: Backup): LocalPlanner {
        val removed = importer.import(backup)
        val restored = checkNotNull(repository.restorePlanner()) { "Planner importado não encontrado" }
        active = restored
        // O Planner já foi substituído: uma falha no reagendamento não desfaz a importação.
        try {
            reschedule(removed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("PlannerLifecycle", "Falha ao reagendar depois de importar", e)
        }
        return restored
    }

    private fun requireActive(): LocalPlanner = checkNotNull(active) { "Planner ativo não encontrado" }
}
