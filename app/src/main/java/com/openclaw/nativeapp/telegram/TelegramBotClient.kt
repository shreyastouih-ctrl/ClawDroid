package com.openclaw.nativeapp.telegram

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.openclaw.nativeapp.ai.GeminiAgent
import com.openclaw.nativeapp.data.AppPreferences
import com.openclaw.nativeapp.data.LogManager
import com.openclaw.nativeapp.shizuku.DeviceController
import com.openclaw.nativeapp.shizuku.ShizukuManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

class TelegramBotClient(
    private val context: Context,
    private val geminiAgent: GeminiAgent
) {
    private val TAG = "TelegramBot"
    private val gson = Gson()
    private val preferences = AppPreferences.getInstance(context)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(35, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .writeTimeout(35, TimeUnit.SECONDS)
        .build()

    var isRunning = false
        private set

    suspend fun startPolling(onStatusChange: (Boolean, String) -> Unit) = withContext(Dispatchers.IO) {
        val token = preferences.telegramBotToken
        if (token.isBlank()) {
            val err = "Telegram Bot Token is not set in Settings."
            LogManager.w(TAG, err)
            onStatusChange(false, err)
            return@withContext
        }

        isRunning = true
        LogManager.i(TAG, "Starting Telegram long-polling loop...")
        onStatusChange(true, "Polling active")

        var offset = 0L
        var failureBackoffMs = 1000L

        while (isActive && isRunning) {
            try {
                val url = "https://api.telegram.org/bot$token/getUpdates?offset=$offset&timeout=25"
                val request = Request.Builder().url(url).get().build()

                val responseStr = httpClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        throw Exception("HTTP ${resp.code}: ${resp.body?.string()}")
                    }
                    resp.body?.string() ?: ""
                }

                failureBackoffMs = 1000L // Reset backoff on success
                val root = JsonParser.parseString(responseStr).asJsonObject
                if (!root.get("ok").asBoolean) {
                    val desc = root.get("description")?.asString ?: "Unknown error"
                    throw Exception("Telegram error: $desc")
                }

                val results = root.getAsJsonArray("result")
                if (results != null && results.size() > 0) {
                    for (i in 0 until results.size()) {
                        val update = results[i].asJsonObject
                        val updateId = update.get("update_id").asLong
                        offset = updateId + 1

                        if (update.has("message")) {
                            val message = update.getAsJsonObject("message")
                            handleIncomingMessage(message)
                        }
                    }
                }
            } catch (e: CancellationException) {
                LogManager.i(TAG, "Polling cancelled.")
                break
            } catch (e: Exception) {
                LogManager.w(TAG, "Polling loop error: ${e.message}. Retrying in ${failureBackoffMs / 1000}s")
                onStatusChange(true, "Reconnecting (${e.message})...")
                delay(failureBackoffMs)
                failureBackoffMs = (failureBackoffMs * 2).coerceAtMost(30_000L)
            }
        }

        isRunning = false
        onStatusChange(false, "Stopped")
        LogManager.i(TAG, "Telegram polling stopped.")
    }

    fun stop() {
        isRunning = false
    }

    private suspend fun handleIncomingMessage(message: JsonObject) {
        val chatId = message.getAsJsonObject("chat").get("id").asLong
        val text = message.get("text")?.asString?.trim() ?: return

        LogManager.i(TAG, "Message from Chat $chatId: $text")

        // Security check: Allowed Chat IDs
        val allowedChat = preferences.allowedChatId.trim()
        if (allowedChat.isNotEmpty()) {
            val allowedList = allowedChat.split(",").map { it.trim() }
            if (!allowedList.contains(chatId.toString())) {
                LogManager.w(TAG, "Rejected unauthorized message from chat ID: $chatId")
                sendMessage(chatId, "⛔ Access Denied. Your Chat ID ($chatId) is not authorized in OpenClaw Native settings.")
                return
            }
        }

        when {
            text == "/start" || text == "/help" -> {
                val help = """
                    🤖 *Open Jarvis — AI Phone Controller*
                    _Standalone Android 9+ Controller (No Termux / No Root)_
                    
                    Available Commands:
                    • `/status` - Check battery, Shizuku, and foreground app
                    • `/screenshot` - Capture and send instant screen photo
                    • `/screen` - Dump on-screen accessibility text
                    • `/shell <cmd>` - Run raw ADB command via Shizuku
                    • *Any natural language prompt* - e.g. "Open YouTube and search for tech news", "Turn up volume", "Message on WhatsApp", etc.
                """.trimIndent()
                sendMessage(chatId, help, parseMode = "Markdown")
            }

            text == "/status" -> {
                val battery = DeviceController.getBatteryInfo()
                val fg = DeviceController.getForegroundApp()
                val shizukuOk = ShizukuManager.isPermissionGranted()
                val statusMsg = """
                    📱 *Device Status*
                    • Shizuku: ${if (shizukuOk) "✅ Authorized" else "❌ Disconnected / Denied"}
                    • $battery
                    • Foreground: `$fg`
                    • Auto-Heal: ${if (preferences.isAutoHealEnabled) "🛡️ Active" else "Off"}
                """.trimIndent()
                sendMessage(chatId, statusMsg, parseMode = "Markdown")
            }

            text == "/screenshot" -> {
                sendMessage(chatId, "📸 Capturing screenshot via Shizuku...")
                val file = DeviceController.takeScreenshotFile(context)
                if (file != null && file.exists()) {
                    sendPhoto(chatId, file, caption = "Current Screen")
                    file.delete()
                } else {
                    sendMessage(chatId, "❌ Failed to take screenshot. Make sure Shizuku is authorized.")
                }
            }

            text.startsWith("/shell ") -> {
                val cmd = text.removePrefix("/shell ").trim()
                sendMessage(chatId, "⚡ Executing: `$cmd`...", parseMode = "Markdown")
                val result = ShizukuManager.runCommand(cmd)
                val out = if (result.stdout.isNotEmpty()) result.stdout else result.stderr.ifEmpty { "Command executed (no output)" }
                sendMessage(chatId, "```\n${out.take(3000)}\n```", parseMode = "Markdown")
            }

            else -> {
                // Natural language goal handled by Gemini AI Agent!
                sendMessage(chatId, "🤖 Goal received: \"$text\"\nOpen Jarvis is working...")
                val summary = geminiAgent.executeGoal(text) { stepUpdate ->
                    sendMessage(chatId, stepUpdate)
                }
                sendMessage(chatId, "🏁 Result: $summary")
            }
        }
    }

    suspend fun sendMessage(chatId: Long, text: String, parseMode: String? = null): Boolean = withContext(Dispatchers.IO) {
        val token = preferences.telegramBotToken
        if (token.isBlank()) return@withContext false

        try {
            val url = "https://api.telegram.org/bot$token/sendMessage"
            val json = JsonObject().apply {
                addProperty("chat_id", chatId)
                addProperty("text", text)
                if (parseMode != null) addProperty("parse_mode", parseMode)
            }

            val request = Request.Builder()
                .url(url)
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            LogManager.e(TAG, "Failed to send Telegram message", e)
            false
        }
    }

    suspend fun sendPhoto(chatId: Long, photoFile: File, caption: String = ""): Boolean = withContext(Dispatchers.IO) {
        val token = preferences.telegramBotToken
        if (token.isBlank()) return@withContext false

        try {
            val url = "https://api.telegram.org/bot$token/sendPhoto"
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", chatId.toString())
                .addFormDataPart("caption", caption)
                .addFormDataPart("photo", photoFile.name, photoFile.asRequestBody("image/png".toMediaType()))
                .build()

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            httpClient.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            LogManager.e(TAG, "Failed to upload photo to Telegram", e)
            false
        }
    }
}
