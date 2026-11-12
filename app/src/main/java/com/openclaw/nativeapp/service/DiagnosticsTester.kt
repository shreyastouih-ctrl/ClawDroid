package com.openclaw.nativeapp.service

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.google.gson.JsonParser
import com.openclaw.nativeapp.data.AppPreferences
import com.openclaw.nativeapp.data.LogManager
import com.openclaw.nativeapp.data.OpenClawAssetManager
import com.openclaw.nativeapp.shizuku.DeviceController
import com.openclaw.nativeapp.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object DiagnosticsTester {
    private const val TAG = "Diagnostics"
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    data class DiagnosticResult(
        val testName: String,
        val passed: Boolean,
        val isOptional: Boolean = false,
        val details: String
    )

    suspend fun runFullDiagnostic(context: Context, onProgress: (String) -> Unit): List<DiagnosticResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<DiagnosticResult>()
        val prefs = AppPreferences.getInstance(context)

        LogManager.i(TAG, "Starting full system diagnostic self-test...")
        onProgress("🚀 Starting Open Jarvis Diagnostic Self-Test...")

        // 1. Shizuku Test
        onProgress("Checking Shizuku status...")
        val shizukuAvailable = ShizukuManager.isAvailable()
        val shizukuGranted = ShizukuManager.isPermissionGranted()
        if (shizukuGranted) {
            val cmdTest = ShizukuManager.runCommand("echo 'claw_ok'")
            if (cmdTest.stdout.contains("claw_ok")) {
                results.add(DiagnosticResult("Shizuku ADB IPC", true, false, "Connected, Authorized, shell execution OK"))
            } else {
                results.add(DiagnosticResult("Shizuku ADB IPC", false, false, "Permission granted but shell test failed: ${cmdTest.stderr}"))
            }
        } else if (shizukuAvailable) {
            results.add(DiagnosticResult("Shizuku ADB IPC", false, false, "Shizuku running but permission NOT granted. Tap 'Authorize'."))
        } else {
            results.add(DiagnosticResult("Shizuku ADB IPC", false, false, "Shizuku daemon not running or not paired."))
        }

        // 2. Screen Capture Test
        onProgress("Testing screen capture...")
        val screenshotFile = DeviceController.takeScreenshotFile(context)
        if (screenshotFile != null && screenshotFile.exists() && screenshotFile.length() > 0) {
            val kb = screenshotFile.length() / 1024
            results.add(DiagnosticResult("Screen Capture", true, false, "Captured successfully ($kb KB)"))
            screenshotFile.delete()
        } else {
            results.add(DiagnosticResult("Screen Capture", false, false, "Screencap failed. Requires Shizuku authorization."))
        }

        // 3. Gemini API Test
        onProgress("Testing Google Gemini API...")
        val geminiKey = prefs.geminiApiKey
        if (geminiKey.isBlank()) {
            results.add(DiagnosticResult("Gemini AI API", false, false, "API Key is empty. Configure in Settings."))
        } else {
            try {
                val model = prefs.geminiModel.ifBlank { "gemini-1.5-flash" }
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$geminiKey"
                val body = """{"contents":[{"parts":[{"text":"ping"}]}]}"""
                val req = Request.Builder().url(url).post(body.toRequestBody("application/json".toMediaType())).build()
                val resp = httpClient.newCall(req).execute()
                val respBody = resp.body?.string() ?: ""
                if (resp.isSuccessful) {
                    results.add(DiagnosticResult("Gemini AI API", true, false, "Connected to $model (HTTP 200)"))
                } else {
                    results.add(DiagnosticResult("Gemini AI API", false, false, "HTTP ${resp.code}: $respBody"))
                }
            } catch (e: Exception) {
                results.add(DiagnosticResult("Gemini AI API", false, false, "Connection failed: ${e.message}"))
            }
        }

        // 4. Telegram Bot API Test
        onProgress("Testing Telegram Bot connectivity...")
        val tgToken = prefs.telegramBotToken
        if (tgToken.isBlank()) {
            results.add(DiagnosticResult("Telegram Bot", false, false, "Bot Token is empty. Configure in Settings."))
        } else {
            try {
                val url = "https://api.telegram.org/bot$tgToken/getMe"
                val req = Request.Builder().url(url).get().build()
                val resp = httpClient.newCall(req).execute()
                val respBody = resp.body?.string() ?: ""
                if (resp.isSuccessful) {
                    val root = JsonParser.parseString(respBody).asJsonObject
                    val botUser = root.getAsJsonObject("result")?.get("username")?.asString ?: "Unknown"
                    results.add(DiagnosticResult("Telegram Bot", true, false, "Valid token! Bot: @$botUser"))
                } else {
                    results.add(DiagnosticResult("Telegram Bot", false, false, "Invalid token or network error: $respBody"))
                }
            } catch (e: Exception) {
                results.add(DiagnosticResult("Telegram Bot", false, false, "Connection error: ${e.message}"))
            }
        }

        // 5. Floating Overlay Permission (Optional)
        onProgress("Checking Floating Overlay permission...")
        val overlayOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
        results.add(
            DiagnosticResult(
                "Floating Overlay (Optional)",
                overlayOk,
                true,
                if (overlayOk) "Active / Allowed (Displays over games & apps)" else "Not granted (Enable in Settings to use overlay while gaming)"
            )
        )

        // 6. Comprehensive Storage & Workspace Audit (Optional)
        onProgress("Performing comprehensive storage & workspace audit...")
        val storageAudit = OpenClawAssetManager.performComprehensiveStorageAudit(context)
        results.add(
            DiagnosticResult(
                "Storage & File System",
                storageAudit.readWritePassed,
                true,
                "Disk: ${storageAudit.formatGB(storageAudit.freeBytes)} free of ${storageAudit.formatGB(storageAudit.totalBytes)} | RW Integrity: ${if (storageAudit.readWritePassed) "Passed (${storageAudit.readWriteLatencyMs}ms)" else "Failed"} | ${storageAudit.summary}"
            )
        )

        // 7. Auto-Heal & Watchdog State
        val autoHealReady = prefs.isAutoHealEnabled
        results.add(
            DiagnosticResult(
                "Auto-Heal & Watchdogs",
                true,
                false,
                if (autoHealReady) "Shield Active (AlarmManager + WorkManager + Sticky Service)" else "Disabled by user"
            )
        )

        LogManager.i(TAG, "Diagnostic complete. Results count: ${results.size}")
        results
    }
}
