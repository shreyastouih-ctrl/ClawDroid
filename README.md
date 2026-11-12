# 🤖 ClawDroid: Autonomous AI Phone Controller

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Android](https://img.shields.io/badge/Android-9.0%2B%20(API%2028--35)-brightgreen.svg)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-purple.svg)](https://kotlinlang.org/)
[![Shizuku](https://img.shields.io/badge/Shizuku-Rootless%20ADB-orange.svg)](https://shizuku.rikka.app/)
[![Gemini AI](https://img.shields.io/badge/AI-Google%20Gemini%20Multimodal-4285F4.svg)](https://aistudio.google.com/)

> **A 100% Native Android Application (Android 9.0+ / API 28–35)**  
> **Direct Shizuku Binder IPC • Zero Termux / Zero Linux Chroot • Auto-Healing Watchdogs • Telegram Bot Gateway • Gemini Multimodal Vision Agent**

---

## ⚡ Overview: What is ClawDroid?

**ClawDroid** (formerly Open Jarvis / OpenClaw Native) is an open-source, autonomous AI agent that lives on your Android phone and controls it hands-free using **Google Gemini Vision** and **Shizuku**.

Inspired by the groundbreaking ideas of **Tech Jarvis (`Openclaw-Termux-NoRoot`)**, ClawDroid eliminates the need for Termux, complex terminal commands, Python/Node setup scripts, or root access:

* **Zero Termux / Zero Root:** Operates directly as a standalone Android application with a native Material 3 interface.
* **Direct Shizuku Binder IPC:** Directly links to the Shizuku API (`rikka.shizuku:api`) for rootless ADB system control (taps, swipes, keyevents, app launches, screen captures, UI inspection).
* **Auto-Healing & Self-Revival Engine:**
  - Sticky Foreground Service (`START_STICKY`) with a persistent status notification and CPU `WakeLock`.
  - Heartbeat Supervisor monitoring Shizuku IPC and Telegram network sockets every 30 seconds.
  - `AlarmManager` exact timer triggered by an `UncaughtExceptionHandler` to immediately revive the app after any crash.
  - `WorkManager` periodic OS watchdog checking health every 15 minutes to guarantee survival against OEM task killers.
  - System reboot auto-start (`RECEIVE_BOOT_COMPLETED`).
* **Your Own Credentials & Models:** Use your own **Google Gemini API Key** (`gemini-1.5-flash`, `gemini-2.0-flash`, `gemini-1.5-pro`) and **Telegram Bot Token** with Chat ID security filtering.
* **Multimodal Vision & Function Calling:** Captures the screen and feeds live frames to Gemini Vision, enabling the AI to "see" your phone display, locate buttons, and autonomously navigate apps.
* **Floating Watcher HUD (Optional):** Draggable overlay bubble (`SYSTEM_ALERT_WINDOW`) that stays over games or other apps, allowing one-tap screen inspection and instant AI commanding without leaving your game.
* **ClawDroid Workspace & Storage Audit (Optional):** Comprehensive storage diagnostics verifying disk space, read/write/delete integrity, and `/sdcard/ClawDroid/` workspace readiness.
* **1-Tap Diagnostic Self-Test Suite:** Instant on-device verification testing Shizuku IPC, screenshot capture, Gemini AI API, Telegram bot connectivity, and watchdog readiness.

---

## 📱 Architecture & Flow

```
                      +-----------------------------+
                      |   Telegram User (Remote)    |
                      +--------------+--------------+
                                     |
                         HTTP Long Polling / Photo
                                     |
                      +--------------v--------------+
                      |      TelegramBotClient      |
                      +--------------+--------------+
                                     |
                       User Goals / Commands / Status
                                     |
+---------------------+--------------v--------------+---------------------+
|                     |        GeminiAgent          |                     |
|                     | (Multimodal Vision & Tools) |                     |
|                     +--------------+--------------+                     |
|                                    |                                    |
|                      Tool Calls (tap, swipe, app)                       |
|                                    |                                    |
|                     +--------------v--------------+                     |
|                     |      DeviceController       |                     |
|                     +--------------+--------------+                     |
|                                    |                                    |
|                        Shizuku Binder Shell IPC                         |
|                                    |                                    |
|   +--------------------------------v--------------------------------+   |
|   |                         Shizuku Daemon                          |   |
|   |               (Android ADB Wireless Debugging)                  |   |
|   +--------------------------------+--------------------------------+   |
|                                    |                                    |
|                          Android System Core                            |
|                 (input tap, screencap, am start, dumpsys)               |
+------------------------------------+------------------------------------+
                                     ^
                                     |
               +---------------------+---------------------+
               |                 Auto-Heal Engine          |
               | • Foreground Sticky Service & WakeLock    |
               | • AlarmManager Crash Reviver              |
               | • WorkManager 15-Min Periodic Watchdog    |
               | • BootReceiver on Device Restart          |
               +-------------------------------------------+
```

---

## 🚀 Setup & Usage Guide

### 1. Prerequisites (On your Phone)
1. **Shizuku**:
   - Download Shizuku from [GitHub Releases](https://github.com/RikkaApps/Shizuku/releases), Google Play, or F-Droid.
   - Start Shizuku:
     - On **Android 11+**: Enable **Developer Options** -> **Wireless Debugging** -> Pair and Start Shizuku directly on device without a PC.
     - On **Android 9–10**: Connect once to a PC and run `adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh`.
2. **Gemini API Key**:
   - Get a free key at [Google AI Studio](https://aistudio.google.com/).
3. **Telegram Bot**:
   - Open Telegram, search for `@BotFather`, send `/newbot`, and copy the HTTP Bot Token.
   - (Optional security) Find your Telegram User ID using `@userinfobot` to whitelist only your own chat ID.

---

### 2. Configure Open Jarvis
1. Launch **Open Jarvis**.
2. Tap the **Authorize** button under the Shizuku status row to grant rootless ADB permissions.
3. Tap **Unrestricted Battery** to exempt Open Jarvis from Android's background sleep killers.
4. (Optional) Tap **Floating HUD** to enable the overlay widget while gaming or using other apps.
5. (Optional) Tap **Files & Storage** to run the complete storage audit and initialize the `/sdcard/OpenJarvis/` workspace.
6. Tap **🧪 Run Self-Test** to verify that Shizuku, screen capture, Gemini API, Telegram bot, and watchdogs are 100% operational in one click.
7. Tap the **Settings Gear (⚙️)** in the top right:
   - **Gemini API Key**: Paste your `AIzaSy...` key.
   - **Gemini Model**: Choose `gemini-1.5-flash` (recommended for speed), `gemini-2.0-flash`, or `gemini-1.5-pro`.
   - **Telegram Bot Token**: Paste your bot token.
   - **Allowed Chat ID**: Enter your numeric Telegram User ID (blocks unauthorized users).
   - Ensure **Auto-Heal & Watchdog** and **Start on Device Boot** switches are enabled.
   - Tap **Save Settings**.
8. On the main dashboard, flip the **Service Switch** to **ON**.

---

### 3. Remote Commands (Via Telegram Bot)

| Command | Action |
|---|---|
| `/status` | Returns battery level, charging state, Shizuku health, and foreground app |
| `/screenshot` | Captures screen via Shizuku and immediately sends photo to chat |
| `/screen` | Dumps on-screen accessibility text and UI hierarchy |
| `/shell <cmd>` | Executes raw ADB shell command (e.g. `/shell dumpsys wifi`) |
| `<Natural Language Prompt>` | **Autonomous AI Agent Execution!** |

#### Example Prompts:
* *"Open YouTube and search for tech news"*
* *"Open WhatsApp and send a message to mom saying I'll be home soon"*
* *"Take a screenshot, tell me what app is open and close it"*
* *"Turn up the media volume and open Spotify"*
* *"Open Chrome and search for weather in Tokyo"*

---

## 📥 Download & Install the APK App

You don't need Android Studio or a computer to use ClawDroid! You can install the pre-compiled APK directly onto your phone:

1. **Download APK**:
   * Go to the **[Releases](https://github.com/shreyastouih-ctrl/ClawDroid/releases)** section (or the **[Actions tab](https://github.com/shreyastouih-ctrl/ClawDroid/actions)**) of this repository.
   * Download the latest `app-debug.apk` or `ClawDroid.apk`.
2. **Install on Phone**:
   * Tap the downloaded `.apk` file on your Android device (Android 9.0 to Android 15 supported).
   * Allow *"Install from unknown sources"* if prompted.
3. **Launch & Authorize**:
   * Open **ClawDroid**.
   * Tap **Authorize** for Shizuku, paste your Gemini API key in Settings, and start automating!

---

## 🛠️ Building from Source (Developers)

If you wish to modify or build the APK yourself:

### Using Android Studio
1. Clone the repository:
   ```bash
   git clone https://github.com/shreyastouih-ctrl/ClawDroid.git
   ```
2. Open the project in **Android Studio**.
3. Select **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

### Using Terminal
```bash
./gradlew assembleDebug
# Generated APK: app/build/outputs/apk/debug/app-debug.apk
```

---

## 🏆 Credits & Acknowledgements

ClawDroid (Open Jarvis) is proudly inspired by and gives special credit to:
* **Tech Jarvis ([`techjarves/Openclaw-Termux-NoRoot`](https://github.com/techjarves/Openclaw-Termux-NoRoot))**: For pioneering the concept of controlling Android phones through Telegram and Gemini AI using Shizuku.
* **[RikkaApps / Shizuku](https://shizuku.rikka.app/)**: For providing the rootless ADB execution framework.
* **[Google DeepMind / Gemini](https://aistudio.google.com/)**: For the multimodal vision models and function-calling intelligence.
* **OpenClaw Community**: For early explorations in mobile AI agents.

See [CREDITS.md](CREDITS.md) for full details.

---

## 📄 License

This project is licensed under the Apache License 2.0. See the [LICENSE](LICENSE) file for details.
