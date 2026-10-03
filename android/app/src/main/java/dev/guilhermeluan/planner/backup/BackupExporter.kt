package dev.guilhermeluan.planner.backup

import dev.guilhermeluan.planner.storage.PlannerDatabase
import dev.guilhermeluan.planner.storage.SessionMetadataEntity
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant

class BackupExporter(
    private val database: PlannerDatabase,
    private val clock: Clock,
) {
    /** Guarda quando um backup foi gravado, para a aba Você mostrar "Último em ...". */
    suspend fun recordSaved() {
        database.sessionDao().saveMetadata(SessionMetadataEntity(LAST_SAVED_KEY, clock.instant().toString()))
    }

    fun observeLastSaved(): Flow<Instant?> =
        database.sessionDao().observeMetadata(LAST_SAVED_KEY).map { it?.let(Instant::parse) }

    suspend fun export(accountId: String): JSONObject {
        val sessionDao = database.sessionDao()
        val plannerDao = database.plannerDao()
        val routineDao = database.routineDao()

        val account = sessionDao.account(accountId) ?: error("Conta não encontrada")
        val planner = sessionDao.planner(accountId) ?: error("Planner não encontrado")
        val tasks = plannerDao.tasksByAccount(accountId).sortedBy { it.id }
        val routines = routineDao.routinesByAccount(accountId).sortedBy { it.id }
        val occurrences = routineDao.occurrencesByAccount(accountId).sortedBy { it.id }

        return JSONObject()
            .put("versao", 1)
            .put("exportadoEm", clock.instant().toString())
            .put("conta", JSONObject()
                .put("id", account.id)
                .put("nome", account.username)
                .put("fuso", account.timezone))
            .put("planner", JSONObject()
                .put("id", planner.id))
            .put("tarefas", JSONArray(tasks.map { task ->
                JSONObject()
                    .put("id", task.id)
                    .put("titulo", task.title)
                    .put("dia", task.day)
                    .put("horario", task.time ?: JSONObject.NULL)
                    .put("status", task.status)
                    .put("arquivada", task.archived)
            }))
            .put("rotinas", JSONArray(routines.map { routine ->
                JSONObject()
                    .put("id", routine.id)
                    .put("titulo", routine.title)
                    .put("diasDaSemana", JSONArray(
                        routine.weekdays.split(",").filter { it.isNotBlank() }.map { it.toInt() }
                    ))
                    .put("dataInicio", routine.startDate)
                    .put("horario", routine.time ?: JSONObject.NULL)
                    .put("status", routine.status)
            }))
            .put("ocorrencias", JSONArray(occurrences.map { occurrence ->
                JSONObject()
                    .put("id", occurrence.id)
                    .put("rotinaId", occurrence.routineId)
                    .put("titulo", occurrence.title)
                    .put("dia", occurrence.day)
                    .put("horario", occurrence.time ?: JSONObject.NULL)
                    .put("status", occurrence.status)
            }))
    }
}

private const val LAST_SAVED_KEY = "last_backup_at"
