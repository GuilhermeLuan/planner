package dev.guilhermeluan.planner

import android.app.Application
import androidx.room.Room
import dev.guilhermeluan.planner.session.AndroidKeystoreSecretStore
import dev.guilhermeluan.planner.session.HttpSessionApi
import dev.guilhermeluan.planner.session.RoomSessionStateStore
import dev.guilhermeluan.planner.session.ServerConfiguration
import dev.guilhermeluan.planner.session.ServerConfigurationStore
import dev.guilhermeluan.planner.session.SessionRepository
import dev.guilhermeluan.planner.storage.PlannerDatabase
import kotlinx.coroutines.flow.Flow

class PlannerApplication : Application() {
    val database: PlannerDatabase by lazy {
        Room.databaseBuilder(this, PlannerDatabase::class.java, "planner.db")
            .addMigrations(PlannerDatabase.MIGRATION_1_2)
            .build()
    }
    val serverConfigurationStore by lazy { ServerConfigurationStore(this) }
    private val sessionStateStore by lazy { RoomSessionStateStore(database) }
    private val sessionSecretStore by lazy { AndroidKeystoreSecretStore(this) }

    suspend fun activeSession() = sessionStateStore.readActive()

    suspend fun sessionToken() = sessionSecretStore.readToken()

    suspend fun markSessionBlocked(reason: String) {
        sessionStateStore.markBlocked(reason)
        sessionSecretStore.clearToken()
    }

    fun observeBlockedReason(): Flow<String?> = sessionStateStore.observeBlockedReason()
        ?: kotlinx.coroutines.flow.flowOf(null)

    suspend fun clearSessionBlocked() {
        sessionStateStore.clearBlocked()
    }

    fun sessionRepository(configuration: ServerConfiguration) = SessionRepository(
        api = HttpSessionApi(configuration.baseUrl),
        stateStore = sessionStateStore,
        secretStore = sessionSecretStore,
    )
}
