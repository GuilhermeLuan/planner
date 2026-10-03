package dev.guilhermeluan.planner

import android.app.Application
import androidx.room.Room
import dev.guilhermeluan.planner.backup.BackupExporter
import dev.guilhermeluan.planner.session.AccountSettingsRepository
import dev.guilhermeluan.planner.session.LocalPlannerRepository
import dev.guilhermeluan.planner.session.MigrationToLocal
import dev.guilhermeluan.planner.session.PlannerLifecycle
import dev.guilhermeluan.planner.session.LegacyRemoteCleanup
import dev.guilhermeluan.planner.storage.PlannerDatabase
import java.time.Clock
import java.time.ZoneId

class PlannerApplication : Application() {
    val database: PlannerDatabase by lazy {
        Room.databaseBuilder(this, PlannerDatabase::class.java, "planner.db")
            .addMigrations(PlannerDatabase.MIGRATION_1_2)
            .addMigrations(PlannerDatabase.MIGRATION_2_3, PlannerDatabase.MIGRATION_3_4, PlannerDatabase.MIGRATION_4_5, PlannerDatabase.MIGRATION_5_6)
            .build()
    }
    val localPlannerRepository by lazy { LocalPlannerRepository(database) }
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
