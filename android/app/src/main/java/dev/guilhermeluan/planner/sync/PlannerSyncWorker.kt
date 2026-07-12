package dev.guilhermeluan.planner.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.guilhermeluan.planner.PlannerApplication
import java.io.IOException
import java.time.Clock

class PlannerSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val application = applicationContext as PlannerApplication
        val active = application.activeSession() ?: return Result.failure()
        val token = application.sessionToken() ?: return Result.failure()
        val configuration = application.serverConfigurationStore.read() ?: return Result.failure()

        return try {
            when (
                val status = SyncEngine(
                    database = application.database,
                    api = HttpSyncApi(configuration.baseUrl),
                    session = SyncSession(accountId = active.first.id, token = token),
                    clock = Clock.systemUTC(),
                ).syncOnce()
            ) {
                SyncStatus.Synced -> Result.success()
                is SyncStatus.Failed -> Result.failure(workDataOf("error" to status.message))
            }
        } catch (error: SyncApiException) {
            if (error.code == "account_disabled" || error.code == "password_change_required") {
                if (error.code == "account_disabled") {
                    application.markSessionBlocked(
                        "Esta Conta foi desativada. Peça a reativação à Conta administradora e entre novamente.",
                    )
                }
                Result.failure(workDataOf("code" to error.code, "error" to error.message))
            } else {
                Result.retry()
            }
        } catch (_: IOException) {
            Result.retry()
        } catch (_: Throwable) {
            Result.retry()
        }
    }
}
