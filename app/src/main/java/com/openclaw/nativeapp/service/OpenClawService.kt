package com.openclaw.nativeapp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.openclaw.nativeapp.R
import com.openclaw.nativeapp.ai.GeminiAgent
import com.openclaw.nativeapp.data.AppPreferences
import com.openclaw.nativeapp.data.LogManager
import com.openclaw.nativeapp.shizuku.ShizukuManager
import com.openclaw.nativeapp.telegram.TelegramBotClient
import com.openclaw.nativeapp.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class OpenClawService : Service() {
    private val TAG = "OpenClawService"
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var preferences: AppPreferences
    private lateinit var geminiAgent: GeminiAgent
    private lateinit var telegramBot: TelegramBotClient
    private var telegramJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        preferences = AppPreferences.getInstance(this)
        geminiAgent = GeminiAgent(this)
        telegramBot = TelegramBotClient(this, geminiAgent)

        acquireWakeLock()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Starting OpenClaw services..."))

        LogManager.i(TAG, "OpenClaw Service created.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        if (action == ACTION_STOP) {
            LogManager.i(TAG, "Stop action received.")
            preferences.isServiceEnabled = false
            stopSelf()
            return START_NOT_STICKY
        }

        preferences.isServiceEnabled = true
        LogManager.i(TAG, "Starting background AI & Telegram workers...")

        startTelegramBot()
        startAutoHealHeartbeat()

        return START_STICKY
    }

    private fun startTelegramBot() {
        telegramJob?.cancel()
        telegramJob = serviceScope.launch(Dispatchers.IO) {
            telegramBot.startPolling { isPolling, statusDesc ->
                updateNotification("Status: $statusDesc")
            }
        }
    }

    private fun startAutoHealHeartbeat() {
        AutoHealManager.startHeartbeatWatchdog(this) {
            // Check if telegram bot died or stalled
            if (!telegramBot.isRunning && preferences.telegramBotToken.isNotBlank()) {
                LogManager.w(TAG, "Auto-Heal: Telegram bot was not running. Reviving...")
                startTelegramBot()
            }
        }
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OpenClaw::ServiceWakeLock")?.apply {
                acquire(12 * 60 * 60 * 1000L) // 12 hours safe timeout
            }
            LogManager.i(TAG, "Acquired partial WakeLock.")
        } catch (e: Exception) {
            LogManager.e(TAG, "Failed to acquire WakeLock", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "OpenClaw Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps OpenClaw AI Phone Controller active in background"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, OpenClawService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val shizukuText = if (ShizukuManager.isPermissionGranted()) "Shizuku OK" else "Shizuku Waiting"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Open Jarvis Active")
            .setContentText("$shizukuText | $statusText")
            .setSmallIcon(R.drawable.ic_claw)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .addAction(R.drawable.ic_claw, "Stop", stopPendingIntent)
            .build()
    }

    private fun updateNotification(statusText: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, buildNotification(statusText))
    }

    override fun onDestroy() {
        super.onDestroy()
        telegramBot.stop()
        telegramJob?.cancel()
        AutoHealManager.stopHeartbeatWatchdog()
        serviceScope.cancel()

        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "Error releasing WakeLock", e)
        }

        LogManager.w(TAG, "OpenClaw Service destroyed.")

        // Auto-heal revive if killed unexpectedly while enabled
        if (preferences.isServiceEnabled && preferences.isAutoHealEnabled) {
            LogManager.i(TAG, "Auto-Heal: Service unexpectedly terminated. Scheduling immediate revive.")
            val restartIntent = Intent(applicationContext, OpenClawService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(restartIntent)
            } else {
                applicationContext.startService(restartIntent)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "openclaw_channel_foreground"
        const val NOTIFICATION_ID = 4001
        const val ACTION_START = "com.openclaw.nativeapp.ACTION_START"
        const val ACTION_STOP = "com.openclaw.nativeapp.ACTION_STOP"
    }
}
