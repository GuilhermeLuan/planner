package dev.guilhermeluan.planner.day

import dev.guilhermeluan.planner.tasks.PlannerTask

object DayTasks {
    fun ordered(tasks: List<PlannerTask>): List<PlannerTask> = tasks.sortedWith(
        compareBy<PlannerTask> { it.time == null }.thenBy { it.time },
    )
}
