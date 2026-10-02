package dev.guilhermeluan.planner.day

import java.time.LocalTime

object DayGreeting {
    fun forTime(time: LocalTime): String = when (time.hour) {
        in 5..11 -> "Bom dia"
        in 12..17 -> "Boa tarde"
        else -> "Boa noite"
    }

    fun text(time: LocalTime, name: String): String {
        val trimmed = name.trim()
        return if (trimmed.isEmpty()) forTime(time) else "${forTime(time)}, $trimmed"
    }
}
