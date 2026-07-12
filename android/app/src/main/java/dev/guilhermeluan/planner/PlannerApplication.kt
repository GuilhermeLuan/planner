package dev.guilhermeluan.planner

import android.app.Application
import androidx.room.Room
import dev.guilhermeluan.planner.session.AccountSettingsRepository
import dev.guilhermeluan.planner.session.AndroidKeystoreSecretStore
import dev.guilhermeluan.planner.session.LocalPlannerRepository
import dev.guilhermeluan.planner.session.MigrationToLocal
import dev.guilhermeluan.planner.session.ServerConfigurationStore
import dev.guilhermeluan.planner.storage.PlannerDatabase
import java.time.ZoneId

class PlannerApplication : Application() {
    val database: PlannerDatabase by lazy {
        Room.databaseBuilder(this, PlannerDatabase::class.java, "planner.db")
            .addMigrations(PlannerDatabase.MIGRATION_1_2)
            .build()
    }
    val localPlannerRepository by lazy { LocalPlannerRepository(database) }
    val accountSettingsRepository by lazy { AccountSettingsRepository(database) }
    val migrationToLocal by lazy {
        MigrationToLocal(
            database = database,
            deviceTimezone = { ZoneId.systemDefault().id },
            clearServerConfig = { ServerConfigurationStore(this).clear() },
            clearSecrets = { AndroidKeystoreSecretStore(this).clearToken() },
        )
    }
}
