package dev.guilhermeluan.planner.you

import dev.guilhermeluan.planner.storage.PlannerDatabase
import dev.guilhermeluan.planner.storage.SessionMetadataEntity
import dev.guilhermeluan.planner.tasks.RoomPlannerRepository
import dev.guilhermeluan.planner.water.WaterRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Números da aba Você: constância no mês corrente e desde quando a pessoa usa o Planner. */
class ConsistencyRepository(
    private val database: PlannerDatabase,
    private val planner: RoomPlannerRepository,
    private val water: WaterRepository,
    private val clock: Clock,
) {
    /** A constância do mês em que [zone] está agora; reabra o fluxo para virar de mês. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(accountId: String, zone: ZoneId): Flow<Consistency> {
        val now = clock.instant()
        val today = now.atZone(zone).toLocalDate()
        val month = YearMonth.from(today)
        val streak = database.waterDao().observeEarliestIntakeDay(accountId).flatMapLatest { earliest ->
            val from = earliest?.let(LocalDate::parse) ?: today
            water.observeWeek(accountId, from..today).map { Consistency.waterStreak(it, today) }
        }
        val onTimePercent = planner.observeDosesBetween(accountId, month.atDay(1)..today).map { doses ->
            // Ainda sem o campo de Alarme de Dose no Remédio, "no horário" é só a tolerância do Lembrete.
            DoseTimeliness.onTimePercent(doses, now, zone) { null }
        }
        val routinesDone = database.routineDao()
            .observeDoneCount(accountId, month.atDay(1).toString(), month.atEndOfMonth().toString())
        return combine(streak, onTimePercent, routinesDone, ::Consistency)
    }

    /**
     * O mês da coisa mais antiga do Planner (Tarefa, Rotina ou Consumo de água), ou o mês de hoje num Planner
     * vazio. Fica gravado na primeira vez, para não andar junto com o calendário.
     */
    suspend fun memberSince(accountId: String, zone: ZoneId): YearMonth {
        val session = database.sessionDao()
        session.metadata(MEMBER_SINCE_KEY)?.let { return YearMonth.parse(it) }
        val today = clock.instant().atZone(zone).toLocalDate()
        val earliest = listOfNotNull(
            database.plannerDao().earliestTaskDay(accountId),
            database.routineDao().earliestStartDate(accountId),
        ).map(LocalDate::parse).minOrNull() ?: today
        return YearMonth.from(minOf(earliest, today)).also {
            session.saveMetadata(SessionMetadataEntity(MEMBER_SINCE_KEY, it.toString()))
        }
    }

    private companion object {
        const val MEMBER_SINCE_KEY = "member_since"
    }
}
