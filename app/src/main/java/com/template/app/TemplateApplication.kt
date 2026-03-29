package com.template.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.template.app.logging.GlobalExceptionHandler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point for Buddy.
 *
 * Implements [Configuration.Provider] so WorkManager uses Hilt's [HiltWorkerFactory]
 * instead of its default reflection-based factory. This lets [DriveSyncWorker] (and any
 * future workers) receive @Inject constructor parameters through Hilt.
 *
 * WorkManager's default auto-initializer is disabled in AndroidManifest.xml so that
 * our custom configuration is picked up at startup.
 */
@HiltAndroidApp
class TemplateApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        Thread.setDefaultUncaughtExceptionHandler(
            GlobalExceptionHandler(this, Thread.getDefaultUncaughtExceptionHandler())
        )
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                AppConfig.NOTIFICATION_CHANNEL_BACKUP,
                "Backup",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for completed backups that are ready to save"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                AppConfig.NOTIFICATION_CHANNEL_REMINDERS,
                "תזכורות Buddy",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "תזכורות יומיות לתרגול אנגלית"
            }
        )
    }
}
