package dev.guilhermeluan.planner.notifications

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.guilhermeluan.planner.tasks.DoseKey
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
class AndroidDoseAlarmGatewayTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val gateway = AndroidDoseAlarmGateway(context)
    private val alarmManager = shadowOf(context.getSystemService(AlarmManager::class.java))

    // "Aa" e "BB" têm o mesmo hashCode, e as chaves inteiras também.
    private val first = alarm("Aa")
    private val second = alarm("BB")

    private fun alarm(medicineId: String) = DoseAlarm(
        DoseKey(medicineId, LocalDate.of(2026, 10, 1), LocalTime.of(21, 30)), "Magnésio", "1 cápsula",
        Instant.parse("2099-10-02T01:00:00Z"),
    )

    @Before
    fun grantExactAlarms() = ShadowAlarmManager.setCanScheduleExactAlarms(true)

    @After
    fun resetExactAlarms() = ShadowAlarmManager.setCanScheduleExactAlarms(false)

    @Test
    fun withThePermissionTheAlarmIsScheduledAsAnAlarmClock() {
        gateway.schedule(first)

        val scheduled = alarmManager.scheduledAlarms.single()
        assertEquals(first.triggerAt.toEpochMilli(), scheduled.alarmClockInfo!!.triggerTime)
        assertEquals(setOf(first.key), gateway.scheduledKeys())
    }

    @Test
    fun withoutThePermissionNothingIsScheduledNorRemembered() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)

        gateway.schedule(first)

        assertEquals(0, alarmManager.scheduledAlarms.size)
        assertEquals(emptySet<String>(), gateway.scheduledKeys())
    }

    @Test
    fun alarmsWhoseKeysShareAHashCodeAreScheduledAndCancelledSeparately() {
        assertEquals(first.key.hashCode(), second.key.hashCode())

        gateway.schedule(first)
        gateway.schedule(second)
        assertEquals(2, alarmManager.scheduledAlarms.size)

        gateway.cancel(first.key)
        assertEquals(1, alarmManager.scheduledAlarms.size)
        assertEquals(setOf(second.key), gateway.scheduledKeys())
    }
}
