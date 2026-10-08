package dev.guilhermeluan.planner.water

import java.time.LocalTime

/** Configuração do Lembrete de água: de quanto em quanto tempo lembrar e em que janela do Dia. */
data class WaterReminderSettings(
    val enabled: Boolean = false,
    val intervalHours: Int = 2,
    val windowStart: LocalTime = LocalTime.of(8, 0),
    val windowEnd: LocalTime = LocalTime.of(20, 0),
) {
    /** Intervalo em uma das opções e janela que termina depois de começar. */
    val isValid: Boolean get() = intervalHours in INTERVAL_HOURS && windowEnd.isAfter(windowStart)

    companion object {
        val INTERVAL_HOURS = listOf(1, 2, 3)
    }
}
