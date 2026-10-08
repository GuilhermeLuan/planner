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
        val medicineDao = database.medicineDao()
        val waterDao = database.waterDao()

        val account = sessionDao.account(accountId) ?: error("Conta não encontrada")
        val planner = sessionDao.planner(accountId) ?: error("Planner não encontrado")
        val tasks = plannerDao.tasksByAccount(accountId).sortedBy { it.id }
        val routines = routineDao.routinesByAccount(accountId).sortedBy { it.id }
        val occurrences = routineDao.occurrencesByAccount(accountId).sortedBy { it.id }
        val medicines = medicineDao.medicinesByAccount(accountId).sortedBy { it.id }
        val times = medicineDao.timesByAccount(accountId).groupBy({ it.medicineId }, { it.time })
        val doseRecords = medicineDao.doseRecordsByAccount(accountId)
            .sortedWith(compareBy({ it.medicineId }, { it.day }, { it.time }))
        val previousVersions = medicineDao.previousVersionsByAccount(accountId).groupBy { it.medicineId }
        val archivedPeriods = medicineDao.archivedPeriodsByAccount(accountId).groupBy { it.medicineId }
        val goals = waterDao.goalsByAccount(accountId).sortedBy { it.validFrom }
        val intakes = waterDao.intakesByAccount(accountId).sortedBy { it.day }
        val reminder = waterDao.reminderSettings(accountId)

        return JSONObject()
            .put("versao", 2)
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
                        routine.weekdays.csvValues().map { it.toInt() }
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
            .put("remedios", JSONArray(medicines.map { medicine ->
                scheduleJson(
                    medicine.name, medicine.amount, medicine.unit, times[medicine.id].orEmpty(),
                    medicine.repeatKind, medicine.repeatWeekdays, medicine.startDate, medicine.endDate
                )
                    .put("id", medicine.id)
                    .put("status", medicine.status)
                    .put("estoque", medicine.stockAmount?.let { quantity ->
                        JSONObject()
                            .put("quantidade", quantity)
                            .put("capacidade", medicine.stockCapacity ?: JSONObject.NULL)
                            .put("limiteAviso", medicine.stockThreshold ?: JSONObject.NULL)
                    } ?: JSONObject.NULL)
                    .put("alarme", medicine.alarmDelayMinutes?.let { delay ->
                        JSONObject().put("atrasoMinutos", delay)
                    } ?: JSONObject.NULL)
                    .put("versoesAnteriores", JSONArray(
                        previousVersions[medicine.id].orEmpty().sortedBy { it.until }.map { version ->
                            scheduleJson(
                                version.name, version.amount, version.unit, version.times.csvValues(),
                                version.repeatKind, version.repeatWeekdays, version.startDate, version.endDate
                            )
                                .put("ate", version.until)
                        }
                    ))
                    .put("periodosArquivados", JSONArray(
                        archivedPeriods[medicine.id].orEmpty().sortedBy { it.archivedFrom }.map { period ->
                            JSONObject()
                                .put("de", period.archivedFrom)
                                .put("ate", period.archivedUntil ?: JSONObject.NULL)
                        }
                    ))
            }))
            .put("doses", JSONArray(doseRecords.map { dose ->
                JSONObject()
                    .put("remedioId", dose.medicineId)
                    .put("dia", dose.day)
                    .put("horario", dose.time)
                    .put("status", dose.status)
                    .put("tomadaEm", dose.takenAt ?: JSONObject.NULL)
                    .put("estoqueDescontado", dose.stockDeducted)
            }))
            .put("agua", JSONObject()
                .put("metas", JSONArray(goals.map { goal ->
                    JSONObject()
                        .put("validaDesde", goal.validFrom)
                        .put("metaMl", goal.goalMl)
                }))
                .put("consumo", JSONArray(intakes.map { intake ->
                    JSONObject()
                        .put("dia", intake.day)
                        .put("totalMl", intake.totalMl)
                }))
                .put("lembrete", reminder?.let { settings ->
                    JSONObject()
                        .put("ativo", settings.enabled)
                        .put("intervaloHoras", settings.intervalHours)
                        .put("inicio", settings.windowStart)
                        .put("fim", settings.windowEnd)
                } ?: JSONObject.NULL))
    }
}

private const val LAST_SAVED_KEY = "last_backup_at"

private fun doseJson(amount: Int, unit: String) = JSONObject()
    .put("quantidade", amount)
    .put("unidade", unit)

private fun repeatJson(kind: String, weekdays: String, startDate: String, endDate: String?) = JSONObject()
    .put("tipo", kind)
    .put("diasDaSemana", JSONArray(weekdays.csvValues().map { it.toInt() }))
    .put("dataInicio", startDate)
    .put("dataFim", endDate ?: JSONObject.NULL)

/** Campos comuns a um Remédio e a uma das suas versões anteriores: nome, dose, horários e repetição. */
private fun scheduleJson(
    name: String,
    amount: Int,
    unit: String,
    times: List<String>,
    repeatKind: String,
    repeatWeekdays: String,
    startDate: String,
    endDate: String?,
) = JSONObject()
    .put("nome", name)
    .put("dose", doseJson(amount, unit))
    .put("horarios", JSONArray(times.sorted()))
    .put("repeticao", repeatJson(repeatKind, repeatWeekdays, startDate, endDate))

/** Valores de um campo de texto separado por vírgula, como "1,3,5" ou "08:00,20:00". */
private fun String.csvValues(): List<String> = split(",").filter { it.isNotBlank() }
