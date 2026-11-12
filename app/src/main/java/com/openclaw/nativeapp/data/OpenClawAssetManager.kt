package com.openclaw.nativeapp.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.openclaw.nativeapp.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

object OpenClawAssetManager {
    private const val TAG = "AssetManager"
    private val httpClient = OkHttpClient()

    val workspaceDir: File
        get() = File(Environment.getExternalStorageDirectory(), "OpenJarvis")

    data class StorageAuditReport(
        val androidVersion: String,
        val isExternalMounted: Boolean,
        val hasAllFilesAccess: Boolean,
        val hasLegacyAccess: Boolean,
        val hasShizukuAccess: Boolean,
        val freeBytes: Long,
        val totalBytes: Long,
        val readWritePassed: Boolean,
        val readWriteLatencyMs: Long,
        val workspacePath: String,
        val workspaceExists: Boolean,
        val fileCount: Int,
        val summary: String
    ) {
        fun formatGB(bytes: Long): String {
            val gb = bytes.toDouble() / (1024 * 1024 * 1024)
            return String.format(Locale.US, "%.1f GB", gb)
        }

        fun formattedReport(): String {
            val sb = StringBuilder()
            sb.append("=== 💾 COMPREHENSIVE STORAGE AUDIT ===\n")
            sb.append("• Android OS: $androidVersion\n")
            sb.append("• External Storage Mount: ${if (isExternalMounted) "✅ MOUNTED" else "❌ NOT MOUNTED"}\n")
            sb.append("• All Files Access (MANAGE_EXTERNAL_STORAGE): ${if (hasAllFilesAccess) "✅ Granted" else "ℹ️ Not Granted (Optional)"}\n")
            sb.append("• Legacy Storage (Android 9/10): ${if (hasLegacyAccess) "✅ Granted" else "ℹ️ N/A or Denied"}\n")
            sb.append("• Shizuku Shell Storage Fallback: ${if (hasShizukuAccess) "✅ Accessible (Rootless ADB)" else "❌ Unavailable"}\n")
            sb.append("• Device Disk Space: ${formatGB(freeBytes)} free of ${formatGB(totalBytes)}\n")
            sb.append("• Workspace Path: $workspacePath\n")
            sb.append("• Workspace Status: ${if (workspaceExists) "✅ Ready ($fileCount items)" else "⚠️ Not Initialized"}\n")
            sb.append("• Read/Write/Delete Integrity: ${if (readWritePassed) "✅ PASSED (${readWriteLatencyMs}ms)" else "❌ FAILED"}\n")
            sb.append("• Summary: $summary\n")
            sb.append("======================================\n")
            return sb.toString()
        }
    }

    fun hasStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            val write = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            val read = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
            write == PackageManager.PERMISSION_GRANTED && read == PackageManager.PERMISSION_GRANTED
        }
    }

    fun requestStoragePermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        } else {
            LogManager.i(TAG, "Legacy storage permission handled via standard runtime prompt.")
        }
    }

    suspend fun performComprehensiveStorageAudit(context: Context): StorageAuditReport = withContext(Dispatchers.IO) {
        val androidVer = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        val isMounted = Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED

        val hasAllFiles = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            false
        }

        val hasLegacy = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            val write = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            write == PackageManager.PERMISSION_GRANTED
        } else {
            true // Handled via scoped/internal on newer versions unless granted
        }

        // Test Shizuku storage fallback
        var shizukuStorageOk = false
        if (ShizukuManager.isPermissionGranted()) {
            val res = ShizukuManager.runCommand("ls -d /sdcard/")
            shizukuStorageOk = res.isSuccess && res.stdout.contains("/sdcard")
        }

        // StatFs free and total space
        var freeBytes = 0L
        var totalBytes = 0L
        try {
            val stat = StatFs(Environment.getExternalStorageDirectory().path)
            freeBytes = stat.availableBytes
            totalBytes = stat.totalBytes
        } catch (e: Exception) {
            try {
                val stat = StatFs(context.filesDir.path)
                freeBytes = stat.availableBytes
                totalBytes = stat.totalBytes
            } catch (e2: Exception) {
                LogManager.e(TAG, "StatFs failed", e2)
            }
        }

        // Target workspace
        val targetDir = if (hasStoragePermission(context)) {
            workspaceDir
        } else {
            File(context.filesDir, "OpenJarvis")
        }

        val workspaceExists = targetDir.exists()
        var fileCount = 0
        if (workspaceExists) {
            fileCount = targetDir.walkTopDown().count() - 1 // Exclude root directory itself
        }

        // Test Read / Write / Delete cycle with latency
        var rwPassed = false
        var latencyMs = 0L
        try {
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
            val startTime = System.currentTimeMillis()
            val testFile = File(targetDir, "jarvis_audit_test_${System.currentTimeMillis()}.tmp")
            val sampleData = "OpenJarvis storage test verification token: ${System.nanoTime()}"
            testFile.writeText(sampleData)

            val readBack = testFile.readText()
            val deleteOk = testFile.delete()

            if (readBack == sampleData && deleteOk) {
                rwPassed = true
                latencyMs = System.currentTimeMillis() - startTime
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "Storage RW integrity test failed", e)
        }

        val summary = when {
            rwPassed && (hasAllFiles || hasLegacy) -> "Full External Storage read/write operational."
            rwPassed && shizukuStorageOk -> "Internal storage operational + Shizuku ADB storage fallback active."
            rwPassed -> "Internal app storage operational. External access optional."
            else -> "Storage write check failed. Check permissions."
        }

        StorageAuditReport(
            androidVersion = androidVer,
            isExternalMounted = isMounted,
            hasAllFilesAccess = hasAllFiles,
            hasLegacyAccess = hasLegacy,
            hasShizukuAccess = shizukuStorageOk,
            freeBytes = freeBytes,
            totalBytes = totalBytes,
            readWritePassed = rwPassed,
            readWriteLatencyMs = latencyMs,
            workspacePath = targetDir.absolutePath,
            workspaceExists = targetDir.exists(),
            fileCount = fileCount,
            summary = summary
        )
    }

    suspend fun setupOpenClawWorkspace(context: Context): String = withContext(Dispatchers.IO) {
        try {
            val dir = if (hasStoragePermission(context)) {
                workspaceDir
            } else {
                File(context.filesDir, "OpenJarvis")
            }

            if (!dir.exists()) {
                dir.mkdirs()
            }

            // Create subdirectories for skills, logs, and downloads
            File(dir, "skills").mkdirs()
            File(dir, "logs").mkdirs()
            File(dir, "downloads").mkdirs()

            // Write initial README & sample configuration
            val configFile = File(dir, "jarvis_config.json")
            if (!configFile.exists()) {
                configFile.writeText(
                    """
                    {
                        "version": "1.0.0",
                        "name": "Open Jarvis",
                        "autoHeal": true,
                        "workspacePath": "${dir.absolutePath}",
                        "description": "Autonomous AI Phone Controller workspace for skills, logs, and custom downloads."
                    }
                    """.trimIndent()
                )
            }

            LogManager.i(TAG, "Initialized Open Jarvis workspace at ${dir.absolutePath}")
            "Workspace ready at: ${dir.absolutePath}"
        } catch (e: Exception) {
            LogManager.e(TAG, "Failed to initialize workspace", e)
            "Error: ${e.message}"
        }
    }

    suspend fun downloadAsset(url: String, fileName: String, context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val targetDir = if (hasStoragePermission(context)) {
                File(workspaceDir, "downloads").apply { mkdirs() }
            } else {
                File(context.filesDir, "downloads").apply { mkdirs() }
            }

            val targetFile = File(targetDir, fileName)
            LogManager.i(TAG, "Downloading $url -> ${targetFile.absolutePath}")

            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                val stream = response.body?.byteStream() ?: throw Exception("Empty body")
                FileOutputStream(targetFile).use { fos ->
                    stream.copyTo(fos)
                }
            }

            LogManager.i(TAG, "Downloaded successfully (${targetFile.length() / 1024} KB)")
            true
        } catch (e: Exception) {
            LogManager.e(TAG, "Failed to download asset", e)
            false
        }
    }
}
