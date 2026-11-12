package com.openclaw.nativeapp.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.openclaw.nativeapp.data.AppPreferences
import com.openclaw.nativeapp.data.LogManager
import com.openclaw.nativeapp.shizuku.ShizukuManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

object AutoHealManager {
    private const val TAG = "AutoHealManager"
    private var watchdogJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    fun setupCrashWatchdog(context: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            LogManager.e(TAG, "FATAL CRASH on thread ${thread.name}: ${throwable.message}", throwable)

            val prefs = AppPreferences.getInstance(context)
            if (prefs.isAutoHealEnabled && prefs.isServiceEnabled) {
                scheduleAutoRevive(context)
            }

            defaultHandler?.uncaughtException(thread, throwable) ?: exitProcess(1)
        }
    }

    private fun scheduleAutoRevive(context: Context) {
        try {
            val intent = Intent(context, OpenClawService::class.java).apply {
                action = OpenClawService.ACTION_START
            }
            val pendingIntent = PendingIntent.getService(
                context,
                9999,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 2000,
                pendingIntent
            )
            LogManager.i(TAG, "Scheduled auto-revive alarm in 2 seconds.")
        } catch (e: Exception) {
            LogManager.e(TAG, "Failed to schedule auto-revive", e)
        }
    }

    fun startHeartbeatWatchdog(context: Context, onHealthCheck: suspend () -> Unit) {
        stopHeartbeatWatchdog()
        watchdogJob = scope.launch {
            LogManager.i(TAG, "Auto-Heal Heartbeat Supervisor started.")
            while (isActive) {
                delay(30_000L) // Check every 30 seconds
                try {
                    val prefs = AppPreferences.getInstance(context)
                    if (!prefs.isAutoHealEnabled) continue

                    // 1. Shizuku Health Check
                    if (!ShizukuManager.isAvailable()) {
                        LogManager.w(TAG, "Watchdog: Shizuku binder not reachable. Attempting ping...")
                        ShizukuManager.notifyState()
                    }

                    // 2. Custom service health callback
                    onHealthCheck()
                } catch (e: Exception) {
                    LogManager.e(TAG, "Heartbeat check error", e)
                }
            }
        }
    }

    fun stopHeartbeatWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = null
    }
}
