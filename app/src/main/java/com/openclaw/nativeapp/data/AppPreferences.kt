package com.openclaw.nativeapp.data

import android.content.Context
import android.content.SharedPreferences

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var geminiApiKey: String
        get() = prefs.getString(KEY_GEMINI_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GEMINI_API_KEY, value.trim()).apply()

    var geminiModel: String
        get() = prefs.getString(KEY_GEMINI_MODEL, "gemini-1.5-flash") ?: "gemini-1.5-flash"
        set(value) = prefs.edit().putString(KEY_GEMINI_MODEL, value.trim()).apply()

    var telegramBotToken: String
        get() = prefs.getString(KEY_TELEGRAM_BOT_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TELEGRAM_BOT_TOKEN, value.trim()).apply()

    var allowedChatId: String
        get() = prefs.getString(KEY_ALLOWED_CHAT_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ALLOWED_CHAT_ID, value.trim()).apply()

    var isServiceEnabled: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SERVICE_ENABLED, value).apply()

    var isAutoHealEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_HEAL_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_HEAL_ENABLED, value).apply()

    var isStartOnBootEnabled: Boolean
        get() = prefs.getBoolean(KEY_START_ON_BOOT, true)
        set(value) = prefs.edit().putBoolean(KEY_START_ON_BOOT, value).apply()

    var isFloatingOverlayEnabled: Boolean
        get() = prefs.getBoolean(KEY_FLOATING_OVERLAY, false)
        set(value) = prefs.edit().putBoolean(KEY_FLOATING_OVERLAY, value).apply()

    companion object {
        private const val PREFS_NAME = "openclaw_prefs"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_GEMINI_MODEL = "gemini_model"
        private const val KEY_TELEGRAM_BOT_TOKEN = "telegram_bot_token"
        private const val KEY_ALLOWED_CHAT_ID = "allowed_chat_id"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_AUTO_HEAL_ENABLED = "auto_heal_enabled"
        private const val KEY_START_ON_BOOT = "start_on_boot"
        private const val KEY_FLOATING_OVERLAY = "floating_overlay_enabled"

        @Volatile
        private var instance: AppPreferences? = null

        fun getInstance(context: Context): AppPreferences {
            return instance ?: synchronized(this) {
                instance ?: AppPreferences(context.applicationContext).also { instance = it }
            }
        }
    }
}
