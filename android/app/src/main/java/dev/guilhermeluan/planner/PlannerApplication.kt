package dev.guilhermeluan.planner

import android.app.Application
import androidx.room.Room
import dev.guilhermeluan.planner.backup.BackupExporter
import dev.guilhermeluan.planner.notifications.AndroidDoseAlarmGateway
import dev.guilhermeluan.planner.notifications.AndroidDoseReminderGateway
import dev.guilhermeluan.planner.notifications.DoseAlarmAction
import dev.guilhermeluan.planner.notifications.DoseAlarmService
import dev.guilhermeluan.planner.notifications.DoseReminderAction
import dev.guilhermeluan.planner.notifications.DoseScheduleCoordinator
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
import java.time.Clock
import java.time.ZoneId
import java.util.UUID

class PlannerApplication : Application() {
    val database: PlannerDatabase by lazy {
        Room.databaseBuilder(this, PlannerDatabase::class.java, "planner.db")
            .addMigrations(PlannerDatabase.MIGRATION_1_2)
            .addMigrations(PlannerDatabase.MIGRATION_2_3, PlannerDatabase.MIGRATION_3_4, PlannerDatabase.MIGRATION_4_5, PlannerDatabase.MIGRATION_5_6, PlannerDatabase.MIGRATION_6_7, PlannerDatabase.MIGRATION_7_8, PlannerDatabase.MIGRATION_8_9)
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
    val waterRepository by lazy { WaterRepository(database, Clock.systemUTC()) }
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
        )
    }
}
