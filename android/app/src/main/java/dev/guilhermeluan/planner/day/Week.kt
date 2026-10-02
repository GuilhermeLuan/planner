package dev.guilhermeluan.planner.day

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** A semana do Planner vai de segunda a domingo. */
object Week {
    fun of(day: LocalDate): ClosedRange<LocalDate> {
        val monday = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return monday..monday.plusDays(6)
    }

    fun days(week: ClosedRange<LocalDate>): List<LocalDate> =
        generateSequence(week.start) { it.plusDays(1) }.takeWhile { it <= week.endInclusive }.toList()
}
