package dev.guilhermeluan.planner.day

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

object CurrentDay {
    fun at(clock: Clock, timezone: ZoneId): LocalDate = LocalDate.now(clock.withZone(timezone))
}
