package com.openclaw.nativeapp.shizuku

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.openclaw.nativeapp.data.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream

object DeviceController {
    private const val TAG = "DeviceController"

    suspend fun tap(x: Int, y: Int): Boolean {
        LogManager.action(TAG, "Tap at ($x, $y)")
        val result = ShizukuManager.runCommand("input tap $x $y")
        return result.isSuccess
    }

    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int = 300): Boolean {
        LogManager.action(TAG, "Swipe from ($x1, $y1) to ($x2, $y2) duration: ${durationMs}ms")
        val result = ShizukuManager.runCommand("input swipe $x1 $y1 $x2 $y2 $durationMs")
        return result.isSuccess
    }

    suspend fun inputText(text: String): Boolean {
        LogManager.action(TAG, "Typing text: $text")
        // Android's 'input text' replaces spaces with %s and needs special escaping
        val escaped = text.replace(" ", "%s")
            .replace("&", "\\&")
            .replace("\"", "\\\"")
            .replace("'", "\\'")
            .replace("(", "\\(")
            .replace(")", "\\)")
            .replace(";", "\\;")
            .replace("<", "\\<")
            .replace(">", "\\>")
            .replace("|", "\\|")
            .replace("$", "\\$")
        val result = ShizukuManager.runCommand("input text \"$escaped\"")
        return result.isSuccess
    }

    suspend fun pressKey(keyCode: Int): Boolean {
        LogManager.action(TAG, "Press KeyCode: $keyCode")
        val result = ShizukuManager.runCommand("input keyevent $keyCode")
        return result.isSuccess
    }

    suspend fun pressKeyByName(name: String): Boolean {
        val code = when (name.uppercase()) {
            "HOME" -> 3
            "BACK" -> 4
            "RECENTS", "APP_SWITCH" -> 187
            "POWER" -> 26
            "ENTER" -> 66
            "DEL", "BACKSPACE" -> 67
            "VOLUME_UP" -> 24
            "VOLUME_DOWN" -> 25
            "TAB" -> 61
            "SPACE" -> 62
            "WAKEUP" -> 224
            else -> name.toIntOrNull() ?: 3
        }
        return pressKey(code)
    }

    suspend fun openApp(packageNameOrName: String, context: Context): Boolean {
        val pkg = resolvePackageName(packageNameOrName.trim())
        LogManager.action(TAG, "Opening application: $pkg")

        // Try monkey launch first (safest and doesn't require knowing Main activity)
        val monkeyResult = ShizukuManager.runCommand("monkey -p $pkg -c android.intent.category.LAUNCHER 1")
        if (monkeyResult.isSuccess && !monkeyResult.stdout.contains("No activities that can handle")) {
            return true
        }

        // Fallback: package manager resolve
        val amResult = ShizukuManager.runCommand("am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p $pkg")
        return amResult.isSuccess
    }

    private fun resolvePackageName(query: String): String {
        return when (query.lowercase()) {
            "youtube" -> "com.google.android.youtube"
            "chrome" -> "com.android.chrome"
            "settings" -> "com.android.settings"
            "camera" -> "com.google.android.GoogleCamera"
            "telegram" -> "org.telegram.messenger"
            "whatsapp" -> "com.whatsapp"
            "maps" -> "com.google.android.apps.maps"
            "gmail" -> "com.google.android.gm"
            "playstore", "play store" -> "com.android.vending"
            "spotify" -> "com.spotify.music"
            "calculator" -> "com.google.android.calculator"
            "clock" -> "com.google.android.deskclock"
            "photos" -> "com.google.android.apps.photos"
            "messages" -> "com.google.android.apps.messaging"
            "phone", "dialer" -> "com.google.android.dialer"
            else -> query
        }
    }

    suspend fun takeScreenshotFile(context: Context): File? = withContext(Dispatchers.IO) {
        val targetFile = File(context.cacheDir, "screenshot_${System.currentTimeMillis()}.png")
        val tempDevicePath = "/sdcard/openclaw_screen.png"

        val capResult = ShizukuManager.runCommand("screencap -p $tempDevicePath")
        if (!capResult.isSuccess) {
            LogManager.e(TAG, "Screencap failed: ${capResult.stderr}")
            return@withContext null
        }

        // Copy from sdcard to app cache directory
        val copyResult = ShizukuManager.runCommand("cat $tempDevicePath > ${targetFile.absolutePath}")
        ShizukuManager.runCommand("rm -f $tempDevicePath")

        if (targetFile.exists() && targetFile.length() > 0) {
            targetFile
        } else {
            // Direct byte read fallback if filesystem permission allows
            val directFile = File(tempDevicePath)
            if (directFile.exists()) directFile else null
        }
    }

    suspend fun takeScreenshotBase64(context: Context): String? = withContext(Dispatchers.IO) {
        val file = takeScreenshotFile(context) ?: return@withContext null
        try {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return@withContext null

            // Scale down to max 1024 width/height to save bandwidth and Gemini tokens
            val maxDim = 1024
            val scale = (maxDim.toFloat() / Math.max(bitmap.width, bitmap.height)).coerceAtMost(1f)
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt(),
                    (bitmap.height * scale).toInt(),
                    true
                )
            } else {
                bitmap
            }

            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 75, baos)
            val bytes = baos.toByteArray()
            file.delete()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            LogManager.e(TAG, "Failed to encode screenshot to Base64", e)
            null
        }
    }

    suspend fun getScreenHierarchyDump(): String = withContext(Dispatchers.IO) {
        val dumpPath = "/sdcard/claw_dump.xml"
        ShizukuManager.runCommand("uiautomator dump $dumpPath")
        val readResult = ShizukuManager.runCommand("cat $dumpPath")
        ShizukuManager.runCommand("rm -f $dumpPath")
        if (readResult.isSuccess && readResult.stdout.isNotEmpty()) {
            readResult.stdout.take(8000) // Truncate to reasonable limit
        } else {
            "UI Hierarchy unavailable"
        }
    }

    suspend fun getBatteryInfo(): String {
        val res = ShizukuManager.runCommand("dumpsys battery")
        if (res.isSuccess) {
            val lines = res.stdout.lines()
            val level = lines.find { it.contains("level:") }?.trim() ?: "unknown"
            val temp = lines.find { it.contains("temperature:") }?.trim() ?: ""
            val status = lines.find { it.contains("status:") }?.trim() ?: ""
            return "Battery: $level | $temp | $status"
        }
        return "Battery info unavailable"
    }

    suspend fun getForegroundApp(): String {
        val res = ShizukuManager.runCommand("dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'")
        return if (res.isSuccess && res.stdout.isNotEmpty()) {
            res.stdout.lines().firstOrNull()?.trim() ?: "Unknown"
        } else {
            "Unknown"
        }
    }

    suspend fun expandNotifications(): Boolean {
        return ShizukuManager.runCommand("cmd statusbar expand-notifications").isSuccess
    }

    suspend fun collapseNotifications(): Boolean {
        return ShizukuManager.runCommand("cmd statusbar collapse").isSuccess
    }
}
