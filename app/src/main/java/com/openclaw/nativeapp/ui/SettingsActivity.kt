package com.openclaw.nativeapp.ui

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

            Snackbar.make(binding.root, "Settings saved successfully!", Snackbar.LENGTH_SHORT).show()
        }
    }
}
