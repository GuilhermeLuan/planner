package dev.guilhermeluan.planner.notifications

import android.app.AlarmManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import dev.guilhermeluan.planner.PlannerApplication
import dev.guilhermeluan.planner.tasks.DoseStatus
import dev.guilhermeluan.planner.tasks.DoseUnit
import dev.guilhermeluan.planner.tasks.MedicineDraft
import dev.guilhermeluan.planner.tasks.MedicineRepeat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class DoseReminderReceiversTest {
    private val app = ApplicationProvider.getApplicationContext<PlannerApplication>()
    private val timezone = "America/Sao_Paulo"
    private val today = LocalDate.now(ZoneId.of(timezone))

    private fun createMedicine(): Pair<String, String> = runBlocking {
        val local = app.localPlannerRepository.createPlanner("Ana", timezone)
        val medicine = app.medicinesRepository.createMedicine(
            local.account.id,
            local.planner.id,
            MedicineDraft("Vitamina D", 1, DoseUnit.CAPSULE, setOf(LocalTime.of(13, 0)), MedicineRepeat.Daily, today),
        )
        local.account.id to medicine.id
    }

    @Test
    fun afterARebootTheDoseRemindersAreScheduledAgain() {
        createMedicine()
        val alarms = shadowOf(app.getSystemService(AlarmManager::class.java))

        DoseRemindersBootReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))

        waitUntil { alarms.scheduledAlarms.isNotEmpty() }
        assertTrue(alarms.scheduledAlarms.isNotEmpty())
    }

    @Test
    fun theTookItActionOnTheNotificationRegistersTheDose() {
        val (accountId, medicineId) = createMedicine()
        val key = DoseReminder.keyOf(medicineId, today, LocalTime.of(13, 0))
        val tookIt = Intent(app, DoseReminderActionReceiver::class.java)
            .setAction("dev.guilhermeluan.planner.action.TAKE_DOSE")
            .putExtra("doseKey", key)

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

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition() && System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(10)
        }
    }
}
