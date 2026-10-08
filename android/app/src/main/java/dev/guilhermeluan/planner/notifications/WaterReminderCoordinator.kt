package dev.guilhermeluan.planner.notifications

import dev.guilhermeluan.planner.water.WaterDay
import dev.guilhermeluan.planner.water.WaterRepository
import dev.guilhermeluan.planner.water.WaterReminderSettings
import dev.guilhermeluan.planner.water.WaterText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Lembrete de água entregue em [triggerAt]. */
data class WaterReminder(val triggerAt: Instant)

/** Onde o próximo Lembrete de água fica agendado; só existe um por vez. */
interface WaterReminderGateway {
    /** Agenda [reminder], substituindo o que estiver agendado. */
    fun schedule(reminder: WaterReminder)

    fun cancel()

    /** Tira da tela o Lembrete de água já entregue, se ele ainda estiver lá. */
    fun dismiss()
}

/**
 * Decide quando o próximo Lembrete de água deve tocar, a partir da configuração, do Consumo de hoje, do Fuso da Conta e
 * do relógio, e agenda isso no [gateway].
 */
class WaterReminderCoordinator(
    private val water: WaterRepository,
    private val gateway: WaterReminderGateway,
    private val clock: Clock,
) {
    /** Serializa [reconcile]; o motivo está no KDoc dele. */
    private val reconcileLock = Mutex()

    /**
     * Agenda o próximo Lembrete de água. Uma passada por vez: sem isso, a que leu o Consumo antes de um copo poderia
     * terminar depois da que leu depois dele e deixar agendado o horário antigo. Não é reentrante: o que roda dentro de
     * [reconcile] não pode chamá-lo.
     */
    suspend fun reconcile(accountId: String, timezone: String) = reconcileLock.withLock { reconcileSchedule(accountId, timezone) }

    /** Ação "+ copo": soma um copo ao Consumo de hoje no Fuso da Conta e reconcilia. */
    suspend fun addGlass(accountId: String, timezone: String) {
        water.add(accountId, todayIn(timezone), WaterText.GLASS_ML)
        reconcile(accountId, timezone)
    }

    /** O Consumo de hoje, se o Lembrete que acabou de tocar deve aparecer; nulo se desligado ou com a meta batida. */
    suspend fun dueReminder(accountId: String, timezone: String): WaterDay? {
        val settings = water.observeReminderSettings(accountId).first()
        if (!settings.enabled || !settings.isValid) return null
        return water.observeDay(accountId, todayIn(timezone)).first().takeUnless { it.goalMet }
    }

    private suspend fun reconcileSchedule(accountId: String, timezone: String) {
        val settings = water.observeReminderSettings(accountId).first()
        // Configuração inválida, como uma janela que termina antes de começar, vale como desligada.
        if (!settings.enabled || !settings.isValid) {
            gateway.cancel()
            gateway.dismiss()
            return
        }
        val now = clock.instant()
        val today = todayIn(timezone)
        val goalMet = water.observeDay(accountId, today).first().goalMet
        if (goalMet) gateway.dismiss()
        val times = reminderTimes(settings)
        // Com a meta batida, nenhum horário de hoje vale.
        val todays = if (goalMet) emptyList() else times.map { instantOf(today, it, timezone) }
        val next = todays.firstOrNull { it.isAfter(now) } ?: instantOf(today.plusDays(1), times.first(), timezone)
        gateway.schedule(WaterReminder(next))
    }

    private fun todayIn(timezone: String): LocalDate = LocalDate.now(clock.withZone(ZoneId.of(timezone)))

    private fun instantOf(day: LocalDate, time: LocalTime, timezone: String): Instant =
        ZonedDateTime.of(day, time, ZoneId.of(timezone)).toInstant()

    /** Horários do Lembrete de água no Dia: do início da janela, de intervalo em intervalo, até o fim dela, inclusive. */
    private fun reminderTimes(settings: WaterReminderSettings): List<LocalTime> {
        val step = settings.intervalHours * SECONDS_PER_HOUR
        val end = settings.windowEnd.toSecondOfDay()
        return generateSequence(settings.windowStart.toSecondOfDay()) { it + step }
            .takeWhile { it <= end }
            .map { LocalTime.ofSecondOfDay(it.toLong()) }
            .toList()
    }

    companion object {
        private const val SECONDS_PER_HOUR = 3_600
    }
}
