package com.openclaw.nativeapp.service

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.openclaw.nativeapp.data.AppPreferences
import com.openclaw.nativeapp.data.LogManager
import java.util.concurrent.TimeUnit

class WatchdogWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val prefs = AppPreferences.getInstance(appContext)
        LogManager.i("WatchdogWorker", "Periodic OS health check fired. ServiceEnabled=${prefs.isServiceEnabled}, AutoHeal=${prefs.isAutoHealEnabled}")

        if (prefs.isServiceEnabled && prefs.isAutoHealEnabled) {
            val serviceIntent = Intent(appContext, OpenClawService::class.java).apply {
                action = OpenClawService.ACTION_START
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appContext.startForegroundService(serviceIntent)
                } else {
                    appContext.startService(serviceIntent)
                }
                LogManager.i("WatchdogWorker", "Revived OpenClawService via periodic supervisor.")
            } catch (e: Exception) {
                LogManager.e("WatchdogWorker", "Failed to restart service from worker", e)
            }
        }

        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "openclaw_watchdog_work"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WatchdogWorker>(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            LogManager.i("WatchdogWorker", "Scheduled 15-minute persistent watchdog work.")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
