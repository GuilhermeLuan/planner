package dev.guilhermeluan.planner

import android.app.Application
import androidx.room.Room
import dev.guilhermeluan.planner.backup.BackupExporter
import dev.guilhermeluan.planner.backup.BackupImporter
import dev.guilhermeluan.planner.notifications.AndroidDoseAlarmGateway
import dev.guilhermeluan.planner.notifications.AndroidDoseReminderGateway
import dev.guilhermeluan.planner.notifications.AndroidWaterReminderGateway
import dev.guilhermeluan.planner.notifications.DoseAlarmAction
import dev.guilhermeluan.planner.notifications.DoseAlarmService
import dev.guilhermeluan.planner.notifications.DoseReminderAction
import dev.guilhermeluan.planner.notifications.DoseScheduleCoordinator
import dev.guilhermeluan.planner.notifications.PlannerNotificationScheduler
import dev.guilhermeluan.planner.notifications.WaterReminderCoordinator
import dev.guilhermeluan.planner.notifications.showWaterReminder
import dev.guilhermeluan.planner.session.AccountSettingsRepository
import dev.guilhermeluan.planner.session.LocalPlannerRepository
import dev.guilhermeluan.planner.session.MigrationToLocal
import dev.guilhermeluan.planner.session.PlannerLifecycle
import dev.guilhermeluan.planner.session.LegacyRemoteCleanup
import dev.guilhermeluan.planner.storage.PlannerDatabase
import dev.guilhermeluan.planner.tasks.DoseKey
import dev.guilhermeluan.planner.tasks.IdGenerator
import dev.guilhermeluan.planner.tasks.RoomPlannerRepository
import dev.guilhermeluan.planner.water.WaterRepository
import dev.guilhermeluan.planner.you.ConsistencyRepository
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.ZoneId
import java.util.UUID

class PlannerApplication : Application() {
    val database: PlannerDatabase by lazy {
        Room.databaseBuilder(this, PlannerDatabase::class.java, "planner.db")
            .addMigrations(PlannerDatabase.MIGRATION_1_2)
            .addMigrations(PlannerDatabase.MIGRATION_2_3, PlannerDatabase.MIGRATION_3_4, PlannerDatabase.MIGRATION_4_5, PlannerDatabase.MIGRATION_5_6, PlannerDatabase.MIGRATION_6_7, PlannerDatabase.MIGRATION_7_8, PlannerDatabase.MIGRATION_8_9, PlannerDatabase.MIGRATION_9_10)
            .build()
    }
    val localPlannerRepository by lazy { LocalPlannerRepository(database) }
    val medicinesRepository by lazy {
        RoomPlannerRepository(database, IdGenerator { UUID.randomUUID().toString() }, Clock.systemUTC())
    }
    val doseSchedule by lazy {
        DoseScheduleCoordinator(
            medicinesRepository, AndroidDoseReminderGateway(this), AndroidDoseAlarmGateway(this), Clock.systemUTC(),
        )
    }

    /** Reconcilia os Lembretes e Alarmes de Dose da Conta local, para quem roda fora da tela (receivers). */
    suspend fun reconcileDoseSchedule() {
        val local = localPlannerRepository.restorePlanner() ?: return
        doseSchedule.reconcile(local.account.id, local.account.timezone)
    }

    /** Reconcilia os Lembretes de água da Conta local, para quem roda fora da tela (receivers). */
    suspend fun reconcileWaterReminders() {
        val local = localPlannerRepository.restorePlanner() ?: return
        waterReminders.reconcile(local.account.id, local.account.timezone)
    }

    /** Reagenda Lembretes de Tarefas, Lembretes/Alarmes de Dose e Lembretes de água da Conta local; usado quando a permissão de alarme exato volta. */
    suspend fun rescheduleAll() {
        reconcileDoseSchedule()
        reconcileWaterReminders()
        val local = localPlannerRepository.restorePlanner() ?: return
        val tasks = medicinesRepository.observeScheduledTasks(local.account.id).first()
        PlannerNotificationScheduler.rebuild(this, tasks, local.account.timezone)
    }

    /** Cancela os Lembretes das Tarefas apagadas pela importação e reagenda tudo da Conta importada. */
    suspend fun rescheduleAfterImport(removedTaskIds: List<String>) {
        removedTaskIds.forEach { PlannerNotificationScheduler.cancelTask(this, it) }
        rescheduleAll()
    }

    suspend fun applyDoseReminderAction(key: DoseKey, action: DoseReminderAction) {
        val local = localPlannerRepository.restorePlanner() ?: return
        doseSchedule.applyAction(local.account.id, local.account.timezone, key, action)
    }

    /** Toca o Alarme de Dose [key] se a Dose ainda estiver pendente; um alarme tardio de Dose já registrada fica mudo. */
    suspend fun ringDoseAlarm(key: DoseKey) {
        val local = localPlannerRepository.restorePlanner() ?: return
        val alarm = doseSchedule.alarmToRing(local.account.id, local.account.timezone, key) ?: return
        DoseAlarmService.start(this, alarm)
    }

    suspend fun applyDoseAlarmAction(key: DoseKey, action: DoseAlarmAction) {
        val local = localPlannerRepository.restorePlanner() ?: return
        doseSchedule.applyAlarmAction(local.account.id, local.account.timezone, key, action)
    }

    /** Mostra o Lembrete de água da Conta local se a meta ainda não foi batida, e agenda o seguinte. */
    suspend fun deliverWaterReminder() {
        val local = localPlannerRepository.restorePlanner() ?: return
        waterReminders.dueReminder(local.account.id, local.account.timezone)?.let { showWaterReminder(this, it) }
        waterReminders.reconcile(local.account.id, local.account.timezone)
    }

    /** Ação "+ copo" do Lembrete de água: soma o copo ao Consumo de hoje e reagenda. */
    suspend fun addWaterGlass() {
        val local = localPlannerRepository.restorePlanner() ?: return
        waterReminders.addGlass(local.account.id, local.account.timezone)
    }

    val waterRepository by lazy { WaterRepository(database, Clock.systemUTC()) }
    val waterReminders by lazy {
        WaterReminderCoordinator(waterRepository, AndroidWaterReminderGateway(this), Clock.systemUTC())
    }
    val consistencyRepository by lazy {
        ConsistencyRepository(database, medicinesRepository, waterRepository, Clock.systemUTC())
    }
    val accountSettingsRepository by lazy { AccountSettingsRepository(database) }
    val backupExporter by lazy { BackupExporter(database, Clock.systemUTC()) }
    val migrationToLocal by lazy {
        val legacyCleanup = LegacyRemoteCleanup(this)
        MigrationToLocal(
            database = database,
            deviceTimezone = { ZoneId.systemDefault().id },
            clearServerConfig = legacyCleanup::clearServerConfiguration,
            clearSecrets = legacyCleanup::clearProtectedSession,
        )
    }
    val plannerLifecycle by lazy {
        PlannerLifecycle(
            repository = localPlannerRepository,
            settings = accountSettingsRepository,
            migration = migrationToLocal,
            backup = backupExporter,
            importer = BackupImporter(database, Clock.systemUTC()),
            reschedule = ::rescheduleAfterImport,
        )
    }
}
