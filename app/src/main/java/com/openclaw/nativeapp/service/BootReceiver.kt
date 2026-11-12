package com.openclaw.nativeapp.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.openclaw.nativeapp.data.AppPreferences
import com.openclaw.nativeapp.data.LogManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        LogManager.i("BootReceiver", "Received broadcast action: $action")

        val prefs = AppPreferences.getInstance(context)
        if (prefs.isStartOnBootEnabled && prefs.isServiceEnabled) {
            LogManager.i("BootReceiver", "Auto-starting OpenClaw Service on boot...")
            val serviceIntent = Intent(context, OpenClawService::class.java).apply {
                this.action = OpenClawService.ACTION_START
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
                LogManager.e("BootReceiver", "Failed to start service on boot", e)
            }
        }
    }
}
