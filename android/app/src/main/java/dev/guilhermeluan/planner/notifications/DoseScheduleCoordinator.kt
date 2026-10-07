package dev.guilhermeluan.planner.notifications

import dev.guilhermeluan.planner.tasks.DoseKey
import dev.guilhermeluan.planner.tasks.DoseStatus
import dev.guilhermeluan.planner.tasks.PlannedDose
import dev.guilhermeluan.planner.tasks.RoomPlannerRepository
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Lembrete de uma Dose, entregue em [triggerAt]. */
data class DoseReminder(
    val key: DoseKey,
    val name: String,
    val dose: String,
    val triggerAt: Instant,
) {
    val day: LocalDate get() = key.day
}

/** Alarme de Dose: toca em [triggerAt] se a Dose [key] ainda estiver pendente. */
data class DoseAlarm(
    val key: DoseKey,
    val name: String,
    val dose: String,
    val triggerAt: Instant,
)

/** O Alarme de Dose que toca agora, com o que a tela e a notificação mostram. [ringAt] está no Fuso da Conta. */
data class RingingDoseAlarm(
    val key: DoseKey,
    val name: String,
    val dose: String,
    val ringAt: LocalDateTime,
) {
    /** O horário da Dose, de onde o Lembrete e o Atraso do alarme contam. */
    val doseTime: LocalTime get() = key.time
}

/** Ações do Lembrete de Dose na notificação. */
enum class DoseReminderAction { TAKE, SNOOZE }

/** Ações da tela e da notificação do Alarme de Dose. */
enum class DoseAlarmAction { TAKE, SNOOZE, SKIP }

/** Onde os Lembretes de Dose ficam agendados; lembra o que agendou para poder cancelar depois. */
interface DoseReminderGateway {
    fun scheduledKeys(): Set<DoseKey>
    fun schedule(reminder: DoseReminder)
    fun cancel(key: DoseKey)

    /** Tira da tela o Lembrete [key] já entregue, se ele ainda estiver lá. */
    fun dismiss(key: DoseKey)
}

/** Onde os Alarmes de Dose ficam agendados (ADR 0024); lembra o que agendou para poder cancelar depois. */
interface DoseAlarmGateway {
    /** Se o app pode agendar alarmes exatos; sem isso não há Alarme de Dose. */
    fun canScheduleExactAlarms(): Boolean
    fun scheduledKeys(): Set<DoseKey>
    fun schedule(alarm: DoseAlarm)
    fun cancel(key: DoseKey)

    /** Para o som e tira da tela o Alarme de Dose [key], se ele estiver tocando ou visível. */
    fun dismiss(key: DoseKey)
}

/**
 * Decide quais Lembretes e Alarmes de Dose devem existir, a partir dos Remédios ativos, das Doses registradas, do
 * Fuso da Conta e do relógio, e reconcilia isso com [reminders] e [alarms].
 */
class DoseScheduleCoordinator(
    private val repository: RoomPlannerRepository,
    private val reminders: DoseReminderGateway,
    private val alarms: DoseAlarmGateway,
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
        settled.forEach {
            reminders.dismiss(it.key)
            alarms.dismiss(it.key)
        }
        val wanted = pending
            .mapNotNull { dose ->
                val trigger = reminderTime(dose, timezone).takeIf { it.isAfter(now) } ?: return@mapNotNull null
                DoseReminder(dose.key, dose.name, dose.unit.format(dose.amount), trigger)
            }
            .associateBy { it.key }
        (reminders.scheduledKeys() - wanted.keys).forEach(reminders::cancel)
        wanted.values.forEach(reminders::schedule)

        val wantedAlarms = if (alarms.canScheduleExactAlarms()) {
            pending
                .mapNotNull { dose -> alarmOf(dose, timezone)?.takeIf { it.triggerAt.isAfter(now) } }
                .associateBy { it.key }
        } else {
            emptyMap()
        }
        (alarms.scheduledKeys() - wantedAlarms.keys).forEach(alarms::cancel)
        wantedAlarms.values.forEach(alarms::schedule)
    }

    /** O alarme conta do horário da Dose, não do Lembrete: adiar o Lembrete não empurra o alarme. */
    private fun alarmOf(dose: PlannedDose, timezone: String): DoseAlarm? {
        val delay = dose.alarmDelay ?: return null
        return DoseAlarm(
            dose.key, dose.name, dose.unit.format(dose.amount),
            triggerAt = dose.alarmSnoozedUntil ?: scheduledTime(dose, timezone).plus(delay),
        )
    }

    /** Aplica a ação tocada na notificação do Lembrete [key] e reconcilia os agendamentos. */
    suspend fun applyAction(accountId: String, timezone: String, key: DoseKey, action: DoseReminderAction) =
        answer(accountId, timezone, key) { dose ->
            when (action) {
                DoseReminderAction.TAKE -> record(accountId, dose, DoseStatus.TAKEN)
                // Adia da próxima vez que o Lembrete tocaria, ou de agora, se ele já tocou.
                DoseReminderAction.SNOOZE -> repository.snoozeDose(
                    accountId, dose.medicineId, dose.day, dose.time, maxOf(clock.instant(), reminderTime(dose, timezone)).plus(SNOOZE),
                )
            }
        }

    /**
     * Aplica a ação do Alarme de Dose [key] e reconcilia. Parar o som vale para qualquer ação. "Adiar" reagenda o
     * alarme para dali a [SNOOZE] e mantém a Dose pendente.
     */
    suspend fun applyAlarmAction(accountId: String, timezone: String, key: DoseKey, action: DoseAlarmAction) {
        alarms.dismiss(key)
        answer(accountId, timezone, key) { dose ->
            when (action) {
                DoseAlarmAction.TAKE -> record(accountId, dose, DoseStatus.TAKEN)
                DoseAlarmAction.SKIP -> record(accountId, dose, DoseStatus.SKIPPED)
                DoseAlarmAction.SNOOZE -> repository.snoozeDoseAlarm(accountId, dose.key, clock.instant().plus(SNOOZE))
            }
        }
    }

    /**
     * Grava a resposta a um Lembrete ou Alarme de Dose e reconcilia. Só age sobre Dose ainda pendente: uma
     * notificação antiga não reescreve o que já foi registrado.
     */
    private suspend fun answer(accountId: String, timezone: String, key: DoseKey, write: suspend (PlannedDose) -> Unit) {
        pendingDose(accountId, key)?.let { write(it) }
        reconcile(accountId, timezone)
    }

    private suspend fun record(accountId: String, dose: PlannedDose, status: DoseStatus) =
        repository.setDoseStatus(accountId, dose.medicineId, dose.day, dose.time, status)

    /**
     * O Alarme de Dose [key] que deve tocar agora: nulo se a Dose já foi registrada, se o Remédio não tem
     * alarme ou se o alarme foi adiado para depois.
     */
    suspend fun alarmToRing(accountId: String, timezone: String, key: DoseKey): RingingDoseAlarm? {
        val dose = pendingDose(accountId, key) ?: return null
        if (dose.alarmDelay == null) return null
        val now = clock.instant()
        if (dose.alarmSnoozedUntil?.isAfter(now) == true) return null
        return RingingDoseAlarm(key, dose.name, dose.unit.format(dose.amount), LocalDateTime.ofInstant(now, ZoneId.of(timezone)))
    }

    private suspend fun pendingDose(accountId: String, key: DoseKey): PlannedDose? =
        repository.observeDoses(accountId, key.day).first()
            .firstOrNull { it.key == key && it.status == DoseStatus.PENDING }

    private fun scheduledTime(dose: PlannedDose, timezone: String): Instant =
        ZonedDateTime.of(dose.day, dose.time, ZoneId.of(timezone)).toInstant()

    private fun reminderTime(dose: PlannedDose, timezone: String): Instant =
        dose.snoozedUntil ?: scheduledTime(dose, timezone)

    companion object {
        /** Quanto "Adiar" empurra o Lembrete ou o Alarme de Dose. */
        val SNOOZE: Duration = Duration.ofMinutes(10)

        /**
         * Até quantos Dias à frente os Lembretes ficam agendados. Cada Lembrete entregue reconcilia de novo,
         * então uma semana cobre qualquer repetição semanal sem abrir o app.
         */
        private const val HORIZON_DAYS = 7L
    }
}
