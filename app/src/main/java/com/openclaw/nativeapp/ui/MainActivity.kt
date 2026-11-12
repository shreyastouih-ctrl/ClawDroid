package com.openclaw.nativeapp.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.openclaw.nativeapp.R
import com.openclaw.nativeapp.ai.GeminiAgent
import com.openclaw.nativeapp.data.AppPreferences
import com.openclaw.nativeapp.data.LogManager
import com.openclaw.nativeapp.databinding.ActivityMainBinding
import com.openclaw.nativeapp.data.OpenClawAssetManager
import com.openclaw.nativeapp.service.DiagnosticsTester
import com.openclaw.nativeapp.service.FloatingOverlayService
import com.openclaw.nativeapp.service.OpenClawService
import com.openclaw.nativeapp.shizuku.DeviceController
import com.openclaw.nativeapp.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity(), ShizukuManager.StateListener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var preferences: AppPreferences
    private lateinit var geminiAgent: GeminiAgent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferences = AppPreferences.getInstance(this)
        geminiAgent = GeminiAgent(this)

        setupUI()
        setupLogCollector()
        ShizukuManager.addListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        ShizukuManager.removeListener(this)
    }

    override fun onResume() {
        super.onResume()
        updateServiceStateUI()
        updateShizukuStateUI(ShizukuManager.isAvailable(), ShizukuManager.isPermissionGranted())
    }

    private fun setupUI() {
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.switchService.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                startOpenClawService()
            } else {
                stopOpenClawService()
            }
        }

        binding.btnAuthorizeShizuku.setOnClickListener {
            if (!ShizukuManager.isAvailable()) {
                Snackbar.make(binding.root, "Shizuku app is not running. Please launch Shizuku first.", Snackbar.LENGTH_LONG)
                    .setAction("Open Shizuku") {
                        val launchIntent = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                        if (launchIntent != null) startActivity(launchIntent)
                    }.show()
            } else {
                ShizukuManager.requestPermission()
            }
        }

        binding.btnBatteryIgnore.setOnClickListener {
            requestIgnoreBatteryOptimization()
        }

        // Diagnostics One-Tap Self-Test ("and once, test it")
        binding.btnRunDiagnostics.setOnClickListener {
            runDiagnosticsSuite()
        }

        // Floating Overlay HUD Toggle ("while they play, even while opening other apps")
        binding.btnToggleFloatingOverlay.setOnClickListener {
            toggleFloatingOverlay()
        }

        // Optional Files & Storage Manager ("ask for file and management to download OpenCL if they want")
        binding.btnStorageManager.setOnClickListener {
            manageStorageAndWorkspace()
        }

        binding.btnClearLogs.setOnClickListener {
            LogManager.clear()
            binding.tvConsoleLogs.text = "[Console cleared]\n"
        }

        // Test Sandbox Send Button
        binding.btnSendPrompt.setOnClickListener {
            triggerSandboxPrompt()
        }

        binding.etTestPrompt.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                triggerSandboxPrompt()
                true
            } else {
                false
            }
        }
    }

    private fun triggerSandboxPrompt() {
        val prompt = binding.etTestPrompt.text?.toString()?.trim() ?: ""
        if (prompt.isBlank()) return

        if (!ShizukuManager.isPermissionGranted()) {
            Snackbar.make(binding.root, "Please authorize Shizuku first!", Snackbar.LENGTH_SHORT).show()
            return
        }

        binding.etTestPrompt.setText("")
        LogManager.i("Sandbox", "Executing manual prompt: \"$prompt\"")

        lifecycleScope.launch {
            binding.btnSendPrompt.isEnabled = false
            val summary = geminiAgent.executeGoal(prompt) { update ->
                LogManager.ai("Sandbox", update)
            }
            LogManager.i("Sandbox", "Result: $summary")
            binding.btnSendPrompt.isEnabled = true
        }
    }

    private fun startOpenClawService() {
        preferences.isServiceEnabled = true
        val serviceIntent = Intent(this, OpenClawService::class.java).apply {
            action = OpenClawService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        updateServiceStateUI()
    }

    private fun stopOpenClawService() {
        preferences.isServiceEnabled = false
        val serviceIntent = Intent(this, OpenClawService::class.java).apply {
            action = OpenClawService.ACTION_STOP
        }
        startService(serviceIntent)
        updateServiceStateUI()
    }

    private fun updateServiceStateUI() {
        val isRunning = preferences.isServiceEnabled
        binding.switchService.isChecked = isRunning

        if (isRunning) {
            binding.tvServiceStatus.text = getString(R.string.status_service_running)
            binding.indicatorService.backgroundTintList = ContextCompat.getColorStateList(this, R.color.claw_success)
        } else {
            binding.tvServiceStatus.text = getString(R.string.status_service_stopped)
            binding.indicatorService.backgroundTintList = ContextCompat.getColorStateList(this, R.color.claw_warning)
        }

        binding.tvAutoHealBadge.text = if (preferences.isAutoHealEnabled) "🛡️ Auto-Heal Active" else "Auto-Heal Off"
        binding.tvTelegramStatus.text = if (preferences.telegramBotToken.isNotBlank()) {
            if (isRunning) "Telegram: Polling Active" else "Telegram: Ready (Service Paused)"
        } else {
            "Telegram: Token Missing (Open Settings)"
        }
    }

    private fun updateShizukuStateUI(isAvailable: Boolean, isGranted: Boolean) {
        runOnUiThread {
            when {
                isGranted -> {
                    binding.tvShizukuStatus.text = "Shizuku: Authorized"
                    binding.tvShizukuStatus.setTextColor(ContextCompat.getColor(this, R.color.claw_success))
                    binding.btnAuthorizeShizuku.isEnabled = false
                    binding.btnAuthorizeShizuku.text = "Authorized"
                }
                isAvailable -> {
                    binding.tvShizukuStatus.text = "Shizuku: Running (Needs Auth)"
                    binding.tvShizukuStatus.setTextColor(ContextCompat.getColor(this, R.color.claw_warning))
                    binding.btnAuthorizeShizuku.isEnabled = true
                    binding.btnAuthorizeShizuku.text = "Authorize"
                }
                else -> {
                    binding.tvShizukuStatus.text = "Shizuku: Disconnected / Not Running"
                    binding.tvShizukuStatus.setTextColor(ContextCompat.getColor(this, R.color.claw_danger))
                    binding.btnAuthorizeShizuku.isEnabled = true
                    binding.btnAuthorizeShizuku.text = "Launch Shizuku"
                }
            }
        }
    }

    override fun onShizukuStateChanged(isAvailable: Boolean, isGranted: Boolean) {
        updateShizukuStateUI(isAvailable, isGranted)
    }

    private fun requestIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    startActivity(intent)
                }
            } else {
                Toast.makeText(this, "Battery optimization already unrestricted!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun runDiagnosticsSuite() {
        binding.btnRunDiagnostics.isEnabled = false
        binding.tvConsoleLogs.append("\n=== 🧪 RUNNING OPEN JARVIS DIAGNOSTIC TEST SUITE ===\n")

        lifecycleScope.launch {
            val results = DiagnosticsTester.runFullDiagnostic(this@MainActivity) { progress ->
                binding.tvConsoleLogs.append("\n> $progress")
                binding.scrollConsole.post { binding.scrollConsole.fullScroll(android.view.View.FOCUS_DOWN) }
            }

            val summaryBuilder = StringBuilder("\n\n=== 📋 DIAGNOSTIC RESULTS ===\n")
            for (res in results) {
                val icon = if (res.passed) "✅" else if (res.isOptional) "ℹ️" else "❌"
                summaryBuilder.append("$icon ${res.testName}: ${res.details}\n")
            }
            summaryBuilder.append("=====================================\n")

            binding.tvConsoleLogs.append(summaryBuilder.toString())
            binding.scrollConsole.post { binding.scrollConsole.fullScroll(android.view.View.FOCUS_DOWN) }
            binding.btnRunDiagnostics.isEnabled = true
            Snackbar.make(binding.root, "Diagnostics completed. See console.", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun toggleFloatingOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Snackbar.make(binding.root, "Optional: Grant 'Display over other apps' to use overlay while gaming.", Snackbar.LENGTH_LONG)
                .setAction("Settings") {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }.show()
            return
        }

        val overlayIntent = Intent(this, FloatingOverlayService::class.java)
        try {
            startService(overlayIntent)
            Snackbar.make(binding.root, "Floating HUD activated over games & apps!", Snackbar.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Snackbar.make(binding.root, "Failed to start overlay: ${e.message}", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun manageStorageAndWorkspace() {
        lifecycleScope.launch {
            binding.tvConsoleLogs.append("\n=== 💾 CHECKING STORAGE & FILESYSTEM ===\n")
            val audit = OpenClawAssetManager.performComprehensiveStorageAudit(this@MainActivity)
            binding.tvConsoleLogs.append(audit.formattedReport())
            binding.scrollConsole.post { binding.scrollConsole.fullScroll(android.view.View.FOCUS_DOWN) }

            if (!OpenClawAssetManager.hasStoragePermission(this@MainActivity)) {
                Snackbar.make(binding.root, "Storage access optional. Tap 'Grant' to enable /sdcard/OpenClaw/", Snackbar.LENGTH_LONG)
                    .setAction("Grant") {
                        OpenClawAssetManager.requestStoragePermission(this@MainActivity)
                    }.show()
            } else {
                val setupRes = OpenClawAssetManager.setupOpenClawWorkspace(this@MainActivity)
                Snackbar.make(binding.root, setupRes, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun setupLogCollector() {
        // Load initial logs
        val initialLogs = LogManager.getAllLogs().joinToString("\n") { it.formatted() }
        if (initialLogs.isNotEmpty()) {
            binding.tvConsoleLogs.text = initialLogs
        }

        lifecycleScope.launch {
            LogManager.logsFlow.collectLatest { entry ->
                binding.tvConsoleLogs.append("\n" + entry.formatted())
                binding.scrollConsole.post {
                    binding.scrollConsole.fullScroll(android.view.View.FOCUS_DOWN)
                }
            }
        }
    }
}
