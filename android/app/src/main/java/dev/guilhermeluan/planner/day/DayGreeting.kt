package dev.guilhermeluan.planner.day

import java.time.Clock
import java.time.LocalTime
import java.time.ZoneId

object DayGreeting {
    fun forTime(time: LocalTime): String = when (time.hour) {
        in 5..11 -> "Bom dia"
        in 12..17 -> "Boa tarde"
        else -> "Boa noite"
    }

    fun localTime(clock: Clock, timezone: String): LocalTime = LocalTime.now(clock.withZone(ZoneId.of(timezone)))

    fun text(time: LocalTime, name: String): String {
        val trimmed = name.trim()
        return if (trimmed.isEmpty()) forTime(time) else "${forTime(time)}, $trimmed"
    }
}
