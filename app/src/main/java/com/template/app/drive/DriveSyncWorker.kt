package com.template.app.drive

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.template.app.AppConfig
import com.template.app.logging.AppLogger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that syncs all child learning data to Google Drive AppData.
 *
 * Runs in the background [AppConfig.DRIVE_SYNC_DELAY_MINUTES] after being enqueued,
 * which happens at the end of each chat session. This delay allows the session to
 * complete fully (memory extraction, session log finalization) before syncing.
 *
 * Uses [HiltWorker] for dependency injection — requires HiltWorkerFactory in
 * TemplateApplication (already configured).
 *
 * Retry policy: exponential backoff up to 3 attempts before giving up.
 * Drive sync failure is non-critical — local data is always the source of truth.
 */
@HiltWorker
class DriveSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val driveManager: GoogleDriveManager
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "DriveSyncWorker"
        private const val WORK_NAME = "drive_sync"

        /** Enqueues a one-time sync with a short delay. Safe to call after every session. */
        fun enqueue(workManager: WorkManager) {
            val request = OneTimeWorkRequestBuilder<DriveSyncWorker>()
                .setInitialDelay(AppConfig.DRIVE_SYNC_DELAY_MINUTES, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            // Replace any pending sync — no need to queue multiple syncs
            workManager.enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        AppLogger.i(TAG, "Starting Drive sync")
        return driveManager.syncToDrive().fold(
            onSuccess = {
                AppLogger.i(TAG, "Drive sync succeeded")
                Result.success()
            },
            onFailure = { e ->
                AppLogger.e(TAG, "Drive sync failed: ${e.message}")
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        )
    }
}
