package dev.guilhermeluan.planner.you

import dev.guilhermeluan.planner.tasks.DoseStatus
import dev.guilhermeluan.planner.tasks.PlannedDose
import dev.guilhermeluan.planner.water.WaterDay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Os números de "Sua constância" no mês corrente, no Fuso da Conta. */
data class Consistency(
    val waterStreakDays: Int,
    /** Porcentagem de Doses do mês tomadas no horário; nulo quando ainda não venceu nenhuma Dose. */
    val onTimeDosePercent: Int?,
    val routinesDoneThisMonth: Int,
) {
    companion object {
        /**
         * Dias seguidos, até hoje, em que o Consumo de água alcançou a meta que valia no Dia. Hoje ainda em
         * andamento não quebra a sequência: sem a meta batida ele só não entra na conta.
         */
        fun waterStreak(days: List<WaterDay>, today: LocalDate): Int {
            val metDays = days.filter { it.goalMet }.map { it.day }.toSet()
            var day = if (today in metDays) today else today.minusDays(1)
            var streak = 0
            while (day in metDays) {
                streak++
                day = day.minusDays(1)
            }
            return streak
        }
    }
}

object DoseTimeliness {
    /** Sem Alarme de Dose, "no horário" vai até 30 minutos depois do Lembrete. */
    val GRACE_WITHOUT_ALARM: Duration = Duration.ofMinutes(30)

    /** Tomada até o horário do Alarme de Dose ([alarmDelay] depois do Lembrete) ou, sem alarme, até 30 minutos depois dele. */
    fun isOnTime(dose: PlannedDose, zone: ZoneId, alarmDelay: Duration?): Boolean {
        val takenAt = dose.takenAt?.takeIf { dose.status == DoseStatus.TAKEN } ?: return false
        return !takenAt.isAfter(scheduledAt(dose, zone).plus(alarmDelay ?: GRACE_WITHOUT_ALARM))
    }

    /** Doses já vencidas em [now] tomadas no horário, de 0 a 100; nulo se nenhuma venceu. */
    fun onTimePercent(
        doses: List<PlannedDose>,
        now: Instant,
        zone: ZoneId,
        alarmDelayOf: (PlannedDose) -> Duration?,
    ): Int? {
        val due = doses.filter { !scheduledAt(it, zone).isAfter(now) }
        if (due.isEmpty()) return null
        val onTime = due.count { isOnTime(it, zone, alarmDelayOf(it)) }
        return Math.round(onTime * 100f / due.size)
    }

    private fun scheduledAt(dose: PlannedDose, zone: ZoneId): Instant = dose.day.atTime(dose.time).atZone(zone).toInstant()
}
