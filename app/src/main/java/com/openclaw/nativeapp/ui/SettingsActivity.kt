package com.openclaw.nativeapp.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.openclaw.nativeapp.R
import com.openclaw.nativeapp.data.AppPreferences
import com.openclaw.nativeapp.data.OpenClawAssetManager
import com.openclaw.nativeapp.databinding.ActivitySettingsBinding
import com.openclaw.nativeapp.service.FloatingOverlayService
import com.openclaw.nativeapp.service.OpenClawService

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var preferences: AppPreferences

    private val supportedModels = arrayOf(
        "gemini-1.5-flash",
        "gemini-2.0-flash",
        "gemini-1.5-pro"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferences = AppPreferences.getInstance(this)

        setupModelDropdown()
        loadCurrentValues()
        setupListeners()
    }

    private fun setupModelDropdown() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, supportedModels)
        binding.actvGeminiModel.setAdapter(adapter)
    }

    private fun loadCurrentValues() {
        binding.etGeminiApiKey.setText(preferences.geminiApiKey)
        binding.actvGeminiModel.setText(preferences.geminiModel, false)
        binding.etTelegramToken.setText(preferences.telegramBotToken)
        binding.etAllowedChatId.setText(preferences.allowedChatId)
        binding.switchAutoHeal.isChecked = preferences.isAutoHealEnabled
        binding.switchStartOnBoot.isChecked = preferences.isStartOnBootEnabled
        binding.switchFloatingOverlay.isChecked = preferences.isFloatingOverlayEnabled
    }

    private fun setupListeners() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

        binding.btnPasteGeminiKey.setOnClickListener {
            val clip = clipboard?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString()?.trim() ?: ""
                if (text.isNotEmpty()) {
                    binding.etGeminiApiKey.setText(text)
                    Snackbar.make(binding.root, "Pasted Gemini API Key from clipboard!", Snackbar.LENGTH_SHORT).show()
                } else {
                    Snackbar.make(binding.root, "Clipboard is empty.", Snackbar.LENGTH_SHORT).show()
                }
            } else {
                Snackbar.make(binding.root, "Clipboard is empty.", Snackbar.LENGTH_SHORT).show()
            }
        }

        binding.btnPasteTelegramToken.setOnClickListener {
            val clip = clipboard?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString()?.trim() ?: ""
                if (text.isNotEmpty()) {
                    binding.etTelegramToken.setText(text)
                    Snackbar.make(binding.root, "Pasted Telegram Bot Token from clipboard!", Snackbar.LENGTH_SHORT).show()
                } else {
                    Snackbar.make(binding.root, "Clipboard is empty.", Snackbar.LENGTH_SHORT).show()
                }
            } else {
                Snackbar.make(binding.root, "Clipboard is empty.", Snackbar.LENGTH_SHORT).show()
            }
        }

        binding.switchFloatingOverlay.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }

        binding.btnConfigStorage.setOnClickListener {
            OpenClawAssetManager.requestStoragePermission(this)
        }

        binding.btnSaveSettings.setOnClickListener {
            preferences.geminiApiKey = binding.etGeminiApiKey.text?.toString() ?: ""
            preferences.geminiModel = binding.actvGeminiModel.text?.toString() ?: "gemini-1.5-flash"
            preferences.telegramBotToken = binding.etTelegramToken.text?.toString() ?: ""
            preferences.allowedChatId = binding.etAllowedChatId.text?.toString() ?: ""
            preferences.isAutoHealEnabled = binding.switchAutoHeal.isChecked
            preferences.isStartOnBootEnabled = binding.switchStartOnBoot.isChecked
            preferences.isFloatingOverlayEnabled = binding.switchFloatingOverlay.isChecked

            // Auto-refresh running background service with new credentials
            if (preferences.isServiceEnabled) {
                val restartServiceIntent = Intent(this, OpenClawService::class.java).apply {
                    action = OpenClawService.ACTION_START
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(restartServiceIntent)
                } else {
                    startService(restartServiceIntent)
                }
            }

            Snackbar.make(binding.root, "Settings saved & service updated!", Snackbar.LENGTH_SHORT).show()
        }
    }
}
