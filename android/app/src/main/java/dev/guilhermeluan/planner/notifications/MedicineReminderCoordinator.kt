package dev.guilhermeluan.planner.notifications

import dev.guilhermeluan.planner.tasks.DoseStatus
import dev.guilhermeluan.planner.tasks.PlannedDose
import dev.guilhermeluan.planner.tasks.RoomPlannerRepository
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Lembrete de uma Dose, entregue em [triggerAt]. */
data class DoseReminder(
    val medicineId: String,
    val day: LocalDate,
    val time: LocalTime,
    val name: String,
    val dose: String,
    val triggerAt: Instant,
) {
    val key: String get() = keyOf(medicineId, day, time)

    companion object {
        fun keyOf(medicineId: String, day: LocalDate, time: LocalTime) = "$medicineId|$day|$time"
    }
}

/** Ações do Lembrete de Dose na notificação. */
enum class DoseReminderAction { TAKE, SNOOZE }

/** Onde os Lembretes de Dose ficam agendados; lembra o que agendou para poder cancelar depois. */
interface DoseReminderGateway {
    fun scheduledKeys(): Set<String>
    fun schedule(reminder: DoseReminder)
    fun cancel(key: String)

    /** Tira da tela o Lembrete [key] já entregue, se ele ainda estiver lá. */
    fun dismiss(key: String)
}

/**
 * Decide quais Lembretes de Dose devem existir, a partir dos Remédios ativos, das Doses registradas, do Fuso
 * da Conta e do relógio, e reconcilia isso com o [gateway].
 */
class MedicineReminderCoordinator(
    private val repository: RoomPlannerRepository,
    private val gateway: DoseReminderGateway,
    private val clock: Clock,
) {
    suspend fun reconcile(accountId: String, timezone: String) {
        val now = clock.instant()
        val today = LocalDate.now(clock.withZone(ZoneId.of(timezone)))
        // Desde ontem: uma Dose da noite adiada pode voltar depois da meia-noite.
        val (pending, settled) = (-1L..HORIZON_DAYS).flatMap { offset ->
            repository.observeDoses(accountId, today.plusDays(offset)).first()
        }.partition { it.status == DoseStatus.PENDING }
        // Registrada no app depois de o Lembrete chegar: a notificação não tem mais o que pedir.
        settled.forEach { gateway.dismiss(DoseReminder.keyOf(it.medicineId, it.day, it.time)) }
        val wanted = pending
            .mapNotNull { dose ->
                val trigger = reminderTime(dose, timezone).takeIf { it.isAfter(now) } ?: return@mapNotNull null
                DoseReminder(dose.medicineId, dose.day, dose.time, dose.name, dose.unit.format(dose.amount), trigger)
            }
            .associateBy { it.key }
        (gateway.scheduledKeys() - wanted.keys).forEach(gateway::cancel)
        wanted.values.forEach(gateway::schedule)
    }

    /**
     * Aplica a ação tocada na notificação do Lembrete [key] e reconcilia os agendamentos. Só age sobre Dose
     * ainda pendente: uma notificação antiga não reescreve o que já foi registrado.
     */
    suspend fun applyAction(accountId: String, timezone: String, key: String, action: DoseReminderAction) {
        val (medicineId, dayText, timeText) = key.split('|')
        val day = LocalDate.parse(dayText)
        val time = LocalTime.parse(timeText)
        val dose = repository.observeDoses(accountId, day).first()
            .firstOrNull { it.medicineId == medicineId && it.time == time && it.status == DoseStatus.PENDING }
        if (dose != null) {
            when (action) {
                DoseReminderAction.TAKE -> repository.setDoseStatus(accountId, medicineId, day, time, DoseStatus.TAKEN)
                // Adia da próxima vez que o Lembrete tocaria, ou de agora, se ele já tocou.
                DoseReminderAction.SNOOZE -> repository.snoozeDose(
                    accountId, medicineId, day, time, maxOf(clock.instant(), reminderTime(dose, timezone)).plus(SNOOZE),
                )
            }
        }
        reconcile(accountId, timezone)
    }

    private fun reminderTime(dose: PlannedDose, timezone: String): Instant =
        dose.snoozedUntil ?: ZonedDateTime.of(dose.day, dose.time, ZoneId.of(timezone)).toInstant()

    companion object {
        /** Quanto "Adiar" empurra o Lembrete. */
        val SNOOZE: Duration = Duration.ofMinutes(10)

        /**
         * Até quantos Dias à frente os Lembretes ficam agendados. Cada Lembrete entregue reconcilia de novo,
         * então uma semana cobre qualquer repetição semanal sem abrir o app.
         */
        private const val HORIZON_DAYS = 7L
    }
}
