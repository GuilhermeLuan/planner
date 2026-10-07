package dev.guilhermeluan.planner.notifications

import android.app.AlarmManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import dev.guilhermeluan.planner.PlannerApplication
import dev.guilhermeluan.planner.tasks.DoseKey
import dev.guilhermeluan.planner.tasks.DoseStatus
import dev.guilhermeluan.planner.tasks.DoseUnit
import dev.guilhermeluan.planner.tasks.MedicineDraft
import dev.guilhermeluan.planner.tasks.MedicineRepeat
import dev.guilhermeluan.planner.tasks.TaskDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.shadows.ShadowLooper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class DoseReminderReceiversTest {
    private val app = ApplicationProvider.getApplicationContext<PlannerApplication>()
    private val timezone = "America/Sao_Paulo"
    private val today = LocalDate.now(ZoneId.of(timezone))

    private fun createMedicine(alarmDelayMinutes: Int? = null): Pair<String, String> = runBlocking {
        val local = app.localPlannerRepository.createPlanner("Ana", timezone)
        val medicine = app.medicinesRepository.createMedicine(
            local.account.id,
            local.planner.id,
            MedicineDraft("Vitamina D", 1, DoseUnit.CAPSULE, setOf(LocalTime.of(13, 0)), MedicineRepeat.Daily, today, alarmDelayMinutes = alarmDelayMinutes),
        )
        local.account.id to medicine.id
    }

    @Test
    fun afterARebootTheDoseRemindersAreScheduledAgain() {
        createMedicine()
        val alarms = shadowOf(app.getSystemService(AlarmManager::class.java))

        DoseScheduleBootReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))

        waitUntil { alarms.scheduledAlarms.isNotEmpty() }
        assertTrue(alarms.scheduledAlarms.isNotEmpty())
    }

    @Test
    fun theTookItActionOnTheNotificationRegistersTheDose() {
        val (accountId, medicineId) = createMedicine()
        val key = DoseKey(medicineId, today, LocalTime.of(13, 0))
        val tookIt = Intent(app, DoseReminderActionReceiver::class.java)
            .setAction("dev.guilhermeluan.planner.action.TAKE_DOSE")
            .putExtra("doseKey", key.toString())

        DoseReminderActionReceiver().onReceive(app, tookIt)

        val dose = runBlocking {
            withTimeout(5_000) {
                app.medicinesRepository.observeDoses(accountId, today).first { doses ->
                    doses.any { it.status == DoseStatus.TAKEN }
                }
            }.single()
        }
        assertEquals(DoseStatus.TAKEN, dose.status)
        // A reconciliação que segue a ação roda em segundo plano; espera ela agendar a Dose de amanhã.
        val alarms = shadowOf(app.getSystemService(AlarmManager::class.java))
        waitUntil { alarms.scheduledAlarms.isNotEmpty() }
    }

    private fun fireAlarm(medicineId: String) {
        val key = DoseKey(medicineId, today, LocalTime.of(13, 0))
        DoseAlarmReceiver().onReceive(app, Intent(app, DoseAlarmReceiver::class.java).putExtra("doseKey", key.toString()))
    }

    @Test
    fun theAlarmRingsWhenTheDoseIsStillPending() {
        val (_, medicineId) = createMedicine(alarmDelayMinutes = 30)

        fireAlarm(medicineId)

        waitUntil { shadowOf(app).peekNextStartedService() != null }
        val service = shadowOf(app).nextStartedService
        assertEquals(DoseAlarmService::class.java.name, service.component?.className)
    }

    @Test
    fun theAlarmStaysSilentWhenTheDoseWasAlreadyTaken() {
        val (accountId, medicineId) = createMedicine(alarmDelayMinutes = 30)
        runBlocking {
            app.medicinesRepository.setDoseStatus(accountId, medicineId, today, LocalTime.of(13, 0), DoseStatus.TAKEN)
        }

        // O mesmo caminho do receiver, mas esperando a conferência terminar antes de olhar o serviço.
        runBlocking { app.ringDoseAlarm(DoseKey(medicineId, today, LocalTime.of(13, 0))) }

        assertNull(shadowOf(app).peekNextStartedService())
    }

    @Test
    fun answeringTheAlarmRegistersTheDose() {
        val (accountId, medicineId) = createMedicine(alarmDelayMinutes = 30)
        val key = DoseKey(medicineId, today, LocalTime.of(13, 0))

        DoseAlarmActionReceiver().onReceive(app, alarmActionIntent(app, key, DoseAlarmAction.TAKE))

        val dose = runBlocking {
            withTimeout(5_000) {
                app.medicinesRepository.observeDoses(accountId, today).first { doses -> doses.any { it.status == DoseStatus.TAKEN } }
            }.single()
        }
        assertEquals(DoseStatus.TAKEN, dose.status)
        // A reconciliação que segue a ação roda em segundo plano; espera ela agendar a Dose de amanhã.
        val alarms = shadowOf(app.getSystemService(AlarmManager::class.java))
        waitUntil { alarms.scheduledAlarms.isNotEmpty() }
    }

    @Test
    fun afterARebootTheTaskRemindersAreScheduledAgain() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        runBlocking {
            val local = app.localPlannerRepository.createPlanner("Ana", timezone)
            app.medicinesRepository.createTask(local.account.id, local.planner.id, TaskDraft("Pagar conta", today.plusDays(1), LocalTime.of(9, 0)))
        }
        val alarms = shadowOf(app.getSystemService(AlarmManager::class.java))

        DoseScheduleBootReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))

        waitUntil { taskReminders(alarms).isNotEmpty() }
        assertEquals("o Lembrete de Tarefa volta exato", ShadowAlarmManager.WINDOW_EXACT, taskReminders(alarms).single().windowLengthMs)
    }

    @Test
    fun whenThePermissionIsBackEverythingIsRescheduledAsExact() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        createMedicine(alarmDelayMinutes = 30)

        runBlocking {
            val local = app.localPlannerRepository.restorePlanner()!!
            app.medicinesRepository.createTask(local.account.id, local.planner.id, TaskDraft("Pagar conta", today.plusDays(1), LocalTime.of(9, 0)))
            app.rescheduleAll()
        }

        val alarms = shadowOf(app.getSystemService(AlarmManager::class.java))
        assertTrue("o Alarme de Dose volta", alarms.scheduledAlarms.any { it.alarmClockInfo != null })
        assertEquals("o Lembrete de Tarefa volta exato", ShadowAlarmManager.WINDOW_EXACT, taskReminders(alarms).single().windowLengthMs)
    }

    @Test
    fun whenThePermissionChangesTheDoseAlarmsAreScheduledAgain() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        createMedicine(alarmDelayMinutes = 30)
        val alarms = shadowOf(app.getSystemService(AlarmManager::class.java))
        assertTrue(alarms.scheduledAlarms.none { it.alarmClockInfo != null })

        ExactAlarmPermissionReceiver().onReceive(app, Intent(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED))

        waitUntil { alarms.scheduledAlarms.any { it.alarmClockInfo != null } }
    }

    @Test
    fun theManifestDeclaresTheReceiverForThePermissionChange() {
        val declared = app.packageManager
            .queryBroadcastReceivers(Intent(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED), 0)
            .map { it.activityInfo }
            .firstOrNull { it.name == ExactAlarmPermissionReceiver::class.java.name }

        assertNotNull("o manifesto declara o receiver para a mudança de permissão", declared)
        assertTrue(declared!!.exported)
    }

    /** Os alarmes que entregam Lembretes de Tarefa, reconhecidos pelo receiver do PendingIntent. */
    private fun taskReminders(alarms: ShadowAlarmManager) = alarms.scheduledAlarms.filter {
        shadowOf(it.operation).savedIntent.component?.className == PlannerReminderReceiver::class.java.name
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition() && System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(10)
        }
        assertTrue("a condição não se cumpriu em 5 s", condition())
    }
}
