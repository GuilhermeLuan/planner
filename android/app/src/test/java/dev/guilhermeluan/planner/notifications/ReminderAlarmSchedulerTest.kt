package dev.guilhermeluan.planner.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderAlarmSchedulerTest {
    @Test
    fun `a future reminder keeps an exact alarm when exact alarms are available`() {
        val gateway = RecordingAlarmGateway(exactAlarmsAvailable = true)

        ReminderAlarmScheduler(gateway).schedule(triggerAtMillis = 1_784_334_600_000)

        assertEquals(listOf("exact"), gateway.scheduled)
    }

    @Test
    fun `a future reminder uses an inexact alarm when exact alarms are unavailable`() {
        val gateway = RecordingAlarmGateway(exactAlarmsAvailable = false)

        ReminderAlarmScheduler(gateway).schedule(triggerAtMillis = 1_784_334_600_000)

        assertEquals(listOf("inexact"), gateway.scheduled)
    }
}

private class RecordingAlarmGateway(
    private val exactAlarmsAvailable: Boolean,
) : ReminderAlarmGateway {
    val scheduled = mutableListOf<String>()

    override fun canScheduleExactAlarms(): Boolean = exactAlarmsAvailable

    override fun scheduleExact(triggerAtMillis: Long) {
        scheduled += "exact"
    }

    override fun scheduleInexact(triggerAtMillis: Long) {
        scheduled += "inexact"
    }
}
