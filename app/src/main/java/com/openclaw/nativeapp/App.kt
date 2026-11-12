package com.openclaw.nativeapp

import android.app.Application
import com.openclaw.nativeapp.data.AppPreferences
import com.openclaw.nativeapp.data.LogManager
import com.openclaw.nativeapp.service.AutoHealManager
import com.openclaw.nativeapp.service.WatchdogWorker
import com.openclaw.nativeapp.shizuku.ShizukuManager

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        LogManager.i("App", "Open Jarvis starting up...")

        // 1. Setup crash watchdog and self-healing supervisor
        AutoHealManager.setupCrashWatchdog(this)

        // 2. Initialize Shizuku binder and listeners
        ShizukuManager.init()

        // 3. Schedule periodic watchdog work
        val prefs = AppPreferences.getInstance(this)
        if (prefs.isAutoHealEnabled) {
            WatchdogWorker.schedule(this)
        }
    }
}
