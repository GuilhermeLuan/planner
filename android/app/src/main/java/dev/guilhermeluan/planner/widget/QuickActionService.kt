package dev.guilhermeluan.planner.widget

import dev.guilhermeluan.planner.notifications.DoseReminderAction
import dev.guilhermeluan.planner.notifications.DoseScheduleCoordinator
import dev.guilhermeluan.planner.notifications.WaterReminderCoordinator
import dev.guilhermeluan.planner.tasks.DoseStatus
import dev.guilhermeluan.planner.tasks.RoomPlannerRepository
import dev.guilhermeluan.planner.water.WaterRepository
import dev.guilhermeluan.planner.water.WaterText
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/** As ações dos botões Água e Remédio do widget; devolvem o que o botão confirma, ou nulo se não há o que fazer. */
class QuickActionService(
    private val water: WaterRepository,
    private val waterReminders: WaterReminderCoordinator,
    private val medicines: RoomPlannerRepository,
    private val doseSchedule: DoseScheduleCoordinator,
    private val clock: Clock,
) {
    /** Soma o copo padrão ao Consumo de hoje (mesmo caminho do "+ copo" do Lembrete) e confirma com o total do dia. */
    suspend fun addWater(accountId: String, timezone: String): QuickConfirmation {
        waterReminders.addGlass(accountId, timezone)
        val total = water.observeDay(accountId, today(timezone)).first().consumedMl
        return QuickText.waterConfirmation(WaterText.GLASS_ML, total, confirmationUntil(clock.instant()))
    }

    /** Marca como tomada a próxima Dose pendente de hoje; nulo se não há nenhuma. */
    suspend fun takeNextDose(accountId: String, timezone: String): QuickConfirmation? {
        val day = today(timezone)
        val dose = nextPendingDose(medicines.observeDoses(accountId, day).first()) ?: return null
        doseSchedule.applyAction(accountId, timezone, dose.key, DoseReminderAction.TAKE)
        val recorded = medicines.observeDoses(accountId, day).first().firstOrNull { it.key == dose.key }
        if (recorded?.status != DoseStatus.TAKEN) return null
        return QuickText.doseConfirmation(dose, confirmationUntil(clock.instant()))
    }

    private fun today(timezone: String): LocalDate = LocalDate.now(clock.withZone(ZoneId.of(timezone)))
}
