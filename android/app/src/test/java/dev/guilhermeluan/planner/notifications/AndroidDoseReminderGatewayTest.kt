package dev.guilhermeluan.planner.notifications

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
class AndroidDoseReminderGatewayTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val gateway = AndroidDoseReminderGateway(context)
    private val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
    private val notifications = shadowOf(context.getSystemService(NotificationManager::class.java))

    // "Aa" e "BB" têm o mesmo hashCode, e as chaves inteiras também.
    private val first = reminder("Aa")
    private val second = reminder("BB")

    private fun reminder(medicineId: String) = DoseReminder(
        medicineId, LocalDate.of(2026, 10, 1), LocalTime.of(13, 0), "Vitamina D", "1 cápsula",
        Instant.parse("2099-10-01T16:00:00Z"),
    )

    @Test
    fun remindersWhoseKeysShareAHashCodeAreScheduledAndCancelledSeparately() {
        assertEquals(first.key.hashCode(), second.key.hashCode())

        gateway.schedule(first)
        gateway.schedule(second)
        assertEquals(2, alarms.scheduledAlarms.size)

        gateway.cancel(first.key)
        assertEquals(1, alarms.scheduledAlarms.size)
        assertEquals(setOf(second.key), gateway.scheduledKeys())
    }

    @Test
    fun deliveredRemindersWhoseKeysShareAHashCodeAreShownAndDismissedSeparately() {
        gateway.schedule(first)
        gateway.schedule(second)

        alarms.scheduledAlarms.forEach { alarm ->
            DoseReminderReceiver().onReceive(context, shadowOf(alarm.operation).savedIntent)
        }
        assertEquals(2, notifications.allNotifications.size)

        gateway.dismiss(first.key)
        assertEquals(1, notifications.allNotifications.size)
    }
}
