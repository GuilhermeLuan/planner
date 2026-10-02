package dev.guilhermeluan.planner.tasks

import java.time.LocalDate

object MedicineRecurrence {
    fun occursOn(medicine: PlannerMedicine, day: LocalDate): Boolean {
        if (medicine.status != MedicineStatus.ACTIVE || day.isBefore(medicine.startDate)) return false
        return when (val repeat = medicine.repeat) {
            MedicineRepeat.Daily -> true
            is MedicineRepeat.Weekdays -> day.dayOfWeek in repeat.days
            is MedicineRepeat.Period -> !day.isAfter(repeat.end)
        }
    }
}
