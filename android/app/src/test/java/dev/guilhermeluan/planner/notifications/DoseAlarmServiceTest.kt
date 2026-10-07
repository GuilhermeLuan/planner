package dev.guilhermeluan.planner.notifications

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import dev.guilhermeluan.planner.tasks.DoseKey
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.ShadowPowerManager
import org.robolectric.shadows.util.DataSource
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
class DoseAlarmServiceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val notifications = shadowOf(context.getSystemService(NotificationManager::class.java))
    private val magnesium = ringing("magnesio", "Magnésio")
    private val vitamin = ringing("vitamina", "Vitamina D")
    private lateinit var controller: ServiceController<DoseAlarmService>

    private fun ringing(id: String, name: String) = RingingDoseAlarm(
        key = DoseKey(id, LocalDate.of(2026, 10, 1), LocalTime.of(21, 30)),
        name = name,
        dose = "1 cápsula",
        ringAt = LocalDateTime.of(2026, 10, 1, 22, 0),
    )

    /** O aparelho tem toque de alarme que toca; sem isto, o MediaPlayer do Robolectric falha em todos. */
    private fun withAlarmSound() {
        ShadowMediaPlayer.addMediaInfo(
            DataSource.toDataSource(context, AlarmSounds.first()),
            ShadowMediaPlayer.MediaInfo(1_000, 0),
        )
    }

    private fun intentOf(alarm: RingingDoseAlarm) = Intent(context, DoseAlarmService::class.java).putRingingAlarm(alarm)

    private fun ring(vararg alarms: RingingDoseAlarm) {
        controller = Robolectric.buildService(DoseAlarmService::class.java, intentOf(alarms.first())).create()
        alarms.forEachIndexed { index, alarm -> controller.withIntent(intentOf(alarm)).startCommand(0, index + 1) }
    }

    private val service get() = shadowOf(controller.get())

    private val foregroundTitle get() = service.lastForegroundNotification.extras.getString(Notification.EXTRA_TITLE)

    private fun idle(duration: Duration = Duration.ZERO) = shadowOf(Looper.getMainLooper()).idleFor(duration)

    /** As notificações de alarme fora do serviço, pela tag, que é a chave da Dose. */
    private fun taggedAlarms(): Map<String, Notification> = notifications.activeNotifications
        .filter { it.tag != null && it.notification.category == Notification.CATEGORY_ALARM }
        .associate { it.tag to it.notification }

    @After
    fun tearDown() {
        if (::controller.isInitialized) controller.destroy()
        ShadowPowerManager.clearWakeLocks()
        ShadowMediaPlayer.resetStaticState()
    }

    @Test
    fun ringsInTheForegroundWithAFullScreenAlarmNotification() {
        withAlarmSound()
        ring(magnesium)

        val notification = service.lastForegroundNotification
        assertEquals(Notification.CATEGORY_ALARM, notification.category)
        assertEquals("o som vem do serviço, o canal fica mudo", ALARM_CHANNEL_ID, notification.channelId)
        assertNotNull(notification.fullScreenIntent)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals("Hora do Magnésio", foregroundTitle)
        assertEquals("1 cápsula · lembrete às 21:30 ainda sem registro", notification.extras.getString(Notification.EXTRA_TEXT))
        assertEquals(listOf("Tomei", "Adiar 10 min", "Pular dose"), notification.actions.map { it.title.toString() })
    }

    @Test
    fun withoutAnySoundThatPlaysTheNotificationGoesToTheSoundChannel() {
        ring(magnesium)

        assertEquals(ALARM_SOUND_CHANNEL_ID, service.lastForegroundNotification.channelId)
    }

    @Test
    fun holdsAWakeLockWhileRinging() {
        ring(magnesium)

        assertTrue(ShadowPowerManager.getLatestWakeLock().isHeld)
    }

    @Test
    fun afterTenMinutesTheSoundStopsAndTheNotificationStaysVisible() {
        ring(magnesium)

        idle(Duration.ofMillis(DoseAlarmService.SILENCE_AFTER_MS))

        assertTrue(service.isStoppedBySelf)
        assertTrue(service.isForegroundStopped)
        val quiet = taggedAlarms().getValue(magnesium.key.toString())
        assertEquals("Hora do Magnésio", quiet.extras.getString(Notification.EXTRA_TITLE))
        assertFalse("a notificação fica dispensável", quiet.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertFalse(ShadowPowerManager.getLatestWakeLock().isHeld)
    }

    @Test
    fun beforeTenMinutesItKeepsRinging() {
        ring(magnesium)

        idle(Duration.ofMillis(DoseAlarmService.SILENCE_AFTER_MS - 1_000))

        assertFalse(service.isStoppedBySelf)
    }

    @Test
    fun eachAlarmGetsItsOwnTenMinutes() {
        ring(magnesium)
        idle(Duration.ofMinutes(9))
        controller.withIntent(intentOf(vitamin)).startCommand(0, 2)

        idle(Duration.ofMinutes(1))

        assertFalse("a Vitamina D ainda toca", service.isStoppedBySelf)
        assertEquals("Hora do Vitamina D", foregroundTitle)
        val quiet = taggedAlarms().getValue(magnesium.key.toString())
        assertFalse(quiet.flags and Notification.FLAG_ONGOING_EVENT != 0)
    }

    @Test
    fun stoppingTheRingingAlarmEndsTheService() {
        ring(magnesium)

        DoseAlarmService.stopRinging(context, magnesium.key)
        idle()

        assertTrue(service.isStoppedBySelf)
        assertTrue(service.isForegroundStopped)
        assertEquals(emptyMap<String, Notification>(), taggedAlarms())
    }

    @Test
    fun aSecondAlarmTakesTheForegroundAndTheFirstKeepsItsOwnNotification() {
        ring(magnesium, vitamin)

        assertEquals("Hora do Vitamina D", foregroundTitle)
        val first = taggedAlarms().getValue(magnesium.key.toString())
        assertEquals("Hora do Magnésio", first.extras.getString(Notification.EXTRA_TITLE))
        assertTrue(first.flags and Notification.FLAG_ONGOING_EVENT != 0)
    }

    @Test
    fun answeringTheSecondAlarmBringsTheFirstBackToTheForeground() {
        ring(magnesium, vitamin)

        DoseAlarmService.stopRinging(context, vitamin.key)
        idle()

        assertFalse(service.isStoppedBySelf)
        assertEquals("Hora do Magnésio", foregroundTitle)
        assertEquals(emptyMap<String, Notification>(), taggedAlarms())
    }

    @Test
    fun answeringTheFirstOfTwoAlarmsKeepsTheSecondRinging() {
        ring(magnesium, vitamin)

        DoseAlarmService.stopRinging(context, magnesium.key)
        idle()

        assertFalse(service.isStoppedBySelf)
        assertEquals("Hora do Vitamina D", foregroundTitle)
        assertEquals(emptyMap<String, Notification>(), taggedAlarms())
    }

    @Test
    fun anAlarmDismissedBeforeTheServiceStartsDoesNotRing() {
        DoseAlarmService.start(context, magnesium)
        DoseAlarmService.stopRinging(context, magnesium.key)
        idle()

        ring(magnesium)

        assertTrue(service.isStoppedBySelf)
        assertEquals(emptyMap<String, Notification>(), taggedAlarms())
    }

    @Test
    fun theEndOfAnAlarmIsAnnounced() {
        val ended = mutableListOf<DoseKey>()
        val listener: (DoseKey) -> Unit = { ended += it }
        DoseAlarmService.addEndedListener(listener)
        try {
            ring(magnesium, vitamin)

            DoseAlarmService.stopRinging(context, vitamin.key)
            idle(Duration.ofMillis(DoseAlarmService.SILENCE_AFTER_MS))

            assertEquals(listOf(vitamin.key, magnesium.key), ended)
        } finally {
            DoseAlarmService.removeEndedListener(listener)
        }
    }

    @Test
    fun anIntentWithoutAnAlarmStillEntersTheForegroundBeforeStopping() {
        val empty = Intent(context, DoseAlarmService::class.java)
        controller = Robolectric.buildService(DoseAlarmService::class.java, empty).create()
        // startForegroundService exige startForeground; a falha prova que ele foi chamado.
        service.setThrowInStartForeground(IllegalStateException("startForeground"))
        assertThrows(IllegalStateException::class.java) { controller.startCommand(0, 1) }

        service.setThrowInStartForeground(null)
        controller.startCommand(0, 2)

        assertTrue(service.isForegroundStopped)
        assertTrue(service.isStoppedBySelf)
    }
}
