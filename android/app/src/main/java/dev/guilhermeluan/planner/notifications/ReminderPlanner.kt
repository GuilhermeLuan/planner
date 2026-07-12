package dev.guilhermeluan.planner.notifications

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object ReminderPlanner {
    fun triggerAt(
        day: LocalDate,
        time: LocalTime,
        timezone: String,
        now: Instant,
    ): Instant? {
        val trigger = ZonedDateTime.of(day, time, ZoneId.of(timezone)).toInstant()
        return trigger.takeIf { it.isAfter(now) }
    }

    fun triggerAt(
        day: LocalDate,
        time: LocalTime,
        timezone: String,
        clock: Clock,
    ): Instant? = triggerAt(day, time, timezone, clock.instant())
}
