package dev.guilhermeluan.planner.day

import dev.guilhermeluan.planner.tasks.DoseStatus
import dev.guilhermeluan.planner.tasks.PlannedDose
import dev.guilhermeluan.planner.tasks.PlannedRoutineOccurrence
import dev.guilhermeluan.planner.tasks.RoutineOccurrenceStatus
import dev.guilhermeluan.planner.water.WaterDay
import dev.guilhermeluan.planner.water.WaterText
import java.time.LocalDate

/** Um resumo do bloco "Seu dia": o texto ("2 de 4") e a fração da barra de progresso. */
data class SummaryItem(val value: String, val progress: Float) {
    companion object {
        /** Ainda sem dado do Dia selecionado (por exemplo, enquanto ele carrega). */
        val Unknown = SummaryItem("—", 0f)

        fun count(done: Int, total: Int) =
            SummaryItem("$done de $total", if (total == 0) 0f else done.toFloat() / total)
    }
}

/** Rotinas, Água e Remédios do Dia selecionado, como o bloco "Seu dia" os mostra. */
data class DaySummary(val routines: SummaryItem, val water: SummaryItem, val medicines: SummaryItem) {
    companion object {
        val Empty = DaySummary(SummaryItem.count(0, 0), SummaryItem.Unknown, SummaryItem.count(0, 0))

        /**
         * Dados de outro Dia que ainda estejam no estado (a troca de Dia carrega aos poucos) ficam de fora.
         * Doses puladas seguem no total e não contam como tomadas.
         */
        fun of(
            selectedDay: LocalDate,
            routines: List<PlannedRoutineOccurrence>,
            water: WaterDay?,
            doses: List<PlannedDose>,
        ): DaySummary {
            val dayDoses = doses.filter { it.day == selectedDay }
            return DaySummary(
                routines = SummaryItem.count(routines.count { it.status == RoutineOccurrenceStatus.DONE }, routines.size),
                water = water?.takeIf { it.day == selectedDay }?.let {
                    SummaryItem(
                        value = WaterText.consumedOfGoal(it.consumedMl, it.goalMl),
                        progress = if (it.goalMl > 0) (it.consumedMl.toFloat() / it.goalMl).coerceIn(0f, 1f) else 0f,
                    )
                } ?: SummaryItem.Unknown,
                medicines = SummaryItem.count(dayDoses.count { it.status == DoseStatus.TAKEN }, dayDoses.size),
            )
        }
    }
}
