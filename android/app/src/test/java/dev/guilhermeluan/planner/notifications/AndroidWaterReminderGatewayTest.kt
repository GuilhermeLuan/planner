package dev.guilhermeluan.planner.notifications

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import dev.guilhermeluan.planner.water.WaterDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class AndroidWaterReminderGatewayTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val gateway = AndroidWaterReminderGateway(context)
    private val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
    private val notifications = shadowOf(context.getSystemService(NotificationManager::class.java))

    private val morning = WaterReminder(Instant.parse("2099-10-01T13:00:00Z"))
    private val afternoon = WaterReminder(Instant.parse("2099-10-01T16:00:00Z"))

    @Test
    fun scheduleLeavesOneAlarmAtTheRequestedTime() {
        gateway.schedule(morning)

        assertEquals(morning.triggerAt.toEpochMilli(), alarms.scheduledAlarms.single().triggerAtMs)
    }

    @Test
    fun schedulingAgainLeavesOneAlarmAtTheNewTime() {
        gateway.schedule(morning)

        gateway.schedule(afternoon)

        assertEquals(afternoon.triggerAt.toEpochMilli(), alarms.scheduledAlarms.single().triggerAtMs)
    }

    @Test
    fun cancelLeavesNoAlarm() {
        gateway.schedule(morning)

        gateway.cancel()

        assertTrue(alarms.scheduledAlarms.isEmpty())
    }

    @Test
    fun theNotificationSaysWhatIsMissingAndOffersAddingAGlass() {
        showWaterReminder(context, WaterDay(LocalDate.of(2026, 10, 1), 1200, 2000))

        val notification = notifications.allNotifications.single()
        assertEquals("Hora de beber água", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("Faltam 800 ml — cerca de quatro copos.", notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        val addGlass = notification.actions.single()
        assertEquals("+ copo", addGlass.title.toString())
        assertEquals(WaterReminderActionReceiver::class.java.name, shadowOf(addGlass.actionIntent).savedIntent.component?.className)
    }

    @Test
    fun anUnknownActionOnTheReminderNotificationDoesNothing() {
        showWaterReminder(context, WaterDay(LocalDate.of(2026, 10, 1), 200, 2000))

        WaterReminderActionReceiver().onReceive(context, Intent().setAction("dev.guilhermeluan.planner.action.UNKNOWN"))

        assertEquals(1, notifications.allNotifications.size)
    }
}
