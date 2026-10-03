package dev.guilhermeluan.planner.day

import dev.guilhermeluan.planner.tasks.DoseStatus
import dev.guilhermeluan.planner.tasks.PlannedDose
import dev.guilhermeluan.planner.tasks.PlannedRoutineOccurrence
import dev.guilhermeluan.planner.tasks.RoutineOccurrenceStatus
import dev.guilhermeluan.planner.water.WaterDay
import dev.guilhermeluan.planner.water.WaterText

/** Um resumo do bloco "Seu dia": o texto ("2 de 4") e a fração da barra de progresso. */
data class SummaryItem(val value: String, val progress: Float)

/** Rotinas, Água e Remédios do Dia selecionado, como o bloco "Seu dia" os mostra. */
data class DaySummary(val routines: SummaryItem, val water: SummaryItem, val medicines: SummaryItem) {
    companion object {
        val Empty = of(emptyList(), null, emptyList())

        fun of(routines: List<PlannedRoutineOccurrence>, water: WaterDay?, doses: List<PlannedDose>) = DaySummary(
            routines = count(routines.count { it.status == RoutineOccurrenceStatus.DONE }, routines.size),
            water = water?.let {
                SummaryItem(
                    value = "${WaterText.decimalLiters(it.consumedMl)} de ${WaterText.shortLiters(it.goalMl)}",
                    progress = (it.consumedMl.toFloat() / it.goalMl).coerceIn(0f, 1f),
                )
            } ?: SummaryItem("0 de 0", 0f),
            medicines = count(doses.count { it.status == DoseStatus.TAKEN }, doses.size),
        )

        private fun count(done: Int, total: Int) =
            SummaryItem("$done de $total", if (total == 0) 0f else done.toFloat() / total)
    }
}
