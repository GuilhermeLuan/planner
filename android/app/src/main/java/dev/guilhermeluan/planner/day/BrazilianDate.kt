package dev.guilhermeluan.planner.day

import java.time.LocalDate
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

object BrazilianDate {
    private val formatter = DateTimeFormatter.ofPattern("dd/MM/uuuu")
        .withResolverStyle(ResolverStyle.STRICT)

    fun format(day: LocalDate): String = day.format(formatter)

    fun parse(value: String): LocalDate? = runCatching {
        LocalDate.parse(value.trim(), formatter)
    }.getOrNull()
}

object RoutineWeekdays {
    val options: List<DayOfWeek> = DayOfWeek.entries
}
