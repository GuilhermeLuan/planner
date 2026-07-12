package dev.guilhermeluan.planner.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class PlannerSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        return Result.success()
    }
}
