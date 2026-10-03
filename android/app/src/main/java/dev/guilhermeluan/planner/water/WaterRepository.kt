package dev.guilhermeluan.planner.water

import androidx.room.withTransaction
import dev.guilhermeluan.planner.day.Week
import dev.guilhermeluan.planner.storage.PlannerDatabase
import dev.guilhermeluan.planner.storage.WaterGoalEntity
import dev.guilhermeluan.planner.storage.WaterIntakeEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/** Consumo de água do Dia contra a Meta de água vigente naquele Dia. */
data class WaterDay(val day: LocalDate, val consumedMl: Int, val goalMl: Int) {
    val goalMet: Boolean get() = consumedMl >= goalMl

    /** Fração da meta já bebida, de 0 a 1. */
    val progress: Float get() = waterProgress(consumedMl, goalMl)
}

fun waterProgress(consumedMl: Int, goalMl: Int): Float =
    if (goalMl > 0) (consumedMl.toFloat() / goalMl).coerceIn(0f, 1f) else 0f

class WaterRepository(
    private val database: PlannerDatabase,
    private val clock: Clock,
) {
    private val dao = database.waterDao()

    fun observeDay(accountId: String, day: LocalDate): Flow<WaterDay> =
        observeWeek(accountId, day..day).map { it.single() }

    /** Cada Dia de [week], inclusive os sem Consumo, com a meta vigente nele. */
    fun observeWeek(accountId: String, week: ClosedRange<LocalDate>): Flow<List<WaterDay>> = combine(
        dao.observeIntakes(accountId, week.start.toString(), week.endInclusive.toString()),
        dao.observeGoals(accountId),
    ) { intakes, goals ->
        val totals = intakes.associate { it.day to it.totalMl }
        Week.days(week).map { day -> WaterDay(day, totals[day.toString()] ?: 0, goals.goalOn(day)) }
    }

    suspend fun add(accountId: String, day: LocalDate, ml: Int) {
        require(ml > 0) { "Some uma quantidade positiva de água" }
        database.withTransaction {
            val now = Instant.now(clock).toString()
            if (dao.addToIntake(accountId, day.toString(), ml, now) == 0) {
                dao.upsertIntake(WaterIntakeEntity(accountId, day.toString(), ml, now))
            }
        }
    }

    /** Corrige o total do Dia para um valor exato, para desfazer enganos. */
    suspend fun adjustTotal(accountId: String, day: LocalDate, totalMl: Int) {
        require(totalMl >= 0) { "O total de água não pode ser negativo" }
        dao.upsertIntake(WaterIntakeEntity(accountId, day.toString(), totalMl, Instant.now(clock).toString()))
    }

    /** A nova meta vale de [from] em diante; Dias anteriores mantêm a meta que tinham. */
    suspend fun setGoal(accountId: String, goalMl: Int, from: LocalDate) {
        require(goalMl > 0) { "A Meta de água precisa ser positiva" }
        database.withTransaction {
            dao.deleteGoalsAfter(accountId, from.toString())
            dao.upsertGoal(WaterGoalEntity(accountId, from.toString(), goalMl))
        }
    }

    companion object {
        const val DEFAULT_GOAL_ML = 2000
    }
}

private fun List<WaterGoalEntity>.goalOn(day: LocalDate): Int =
    lastOrNull { LocalDate.parse(it.validFrom) <= day }?.goalMl ?: WaterRepository.DEFAULT_GOAL_ML
