# 🏆 Credits & Acknowledgements

**Open Jarvis** is built on the shoulders of giants and inspired by visionary open-source pioneers in the Android automation and AI community.

---

### 🌟 Primary Inspiration & Heritage

* **Tech Jarvis ([`techjarves/Openclaw-Termux-NoRoot`](https://github.com/techjarves/Openclaw-Termux-NoRoot))**
  * Special thanks and immense gratitude to **Tech Jarvis** for pioneering the concept of pairing large language models (Google Gemini) with Android Wireless Debugging and Telegram bots to control Android devices without rooting.
  * Their work inspired the transformation of terminal-based Termux scripts into this 100% native Android application with self-healing watchdogs and a floating overlay HUD.

* **OpenClaw / Clawdbot Community**
  * For exploring autonomous agent ReAct loops, tool execution paradigms, and remote gateway architectures on mobile devices.

---

### ⚡ Core Technologies & Libraries

* **[Shizuku](https://shizuku.rikka.app/) by [RikkaApps](https://github.com/RikkaApps)**
  * Powered by `rikka.shizuku:api` and `rikka.shizuku:provider`.
  * Allows Open Jarvis to execute system-level ADB shell commands, touches, screen captures, and application launches seamlessly without requiring root access.

* **[Google Gemini AI](https://aistudio.google.com/) by Google DeepMind**
  * Powers Open Jarvis's multimodal visual perception and function calling engine (`gemini-1.5-flash`, `gemini-2.0-flash`, `gemini-1.5-pro`).

* **[Telegram Bot API](https://core.telegram.org/bots/api)**
  * Provides the secure, remote, long-polling communication gateway that allows commanding the phone from anywhere in the world.

* **[Square OkHttp](https://square.github.io/okhttp/) & [Google Gson](https://github.com/google/gson)**
  * For resilient, non-blocking HTTP network clients and JSON processing.

* **[AndroidX Jetpack](https://developer.android.com/jetpack)**
  * WorkManager, ViewModel, Coroutines, and Material 3 Design components powering Open Jarvis's auto-healing supervisor and interface.

---

### 🤝 Community & Contributors

A huge thank you to everyone who tests, contributes, and shares feedback to make **Open Jarvis** the most reliable, rootless AI phone controller for Android!
