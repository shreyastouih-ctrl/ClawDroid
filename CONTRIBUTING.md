# Contributing to OpenClaw Native

Thank you for your interest in contributing to **OpenClaw Native**! We welcome bug reports, feature suggestions, and pull requests to help make rootless AI phone control seamless and accessible.

---

## 🛠️ Development Setup

1. **Prerequisites**:
   * Android Studio (Hedgehog, Iguana, Jellyfish, Ladybug or newer)
   * JDK 17
   * Android SDK (API 34/35 platform and build-tools installed)
   * A physical Android device with Android 9.0+ and [Shizuku](https://github.com/RikkaApps/Shizuku/releases) installed.

2. **Clone & Open**:
   ```bash
   git clone https://github.com/<your-username>/OpenClawNative.git
   ```
   Open the folder in Android Studio. Gradle will automatically sync dependencies.

3. **Building**:
   ```bash
   ./gradlew assembleDebug
   ```

---

## 📝 Guidelines

* **Code Style**: Follow the official [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html).
* **Architecture**: Keep Shizuku IPC, AI logic, background services, and UI decoupled.
* **Compatibility**: Maintain minimum SDK support at **Android 9.0 (API 28)**. Do not introduce APIs requiring Android 10+ without backward-compatible fallbacks.
* **Auto-Healing**: Any new background task or listener must be resilient to OS process termination and incorporate retry/backoff mechanisms.
* **Pull Requests**:
  1. Fork the repo and create a feature branch (`git checkout -b feature/awesome-feature`).
  2. Commit your changes with clear commit messages.
  3. Push to your branch and open a Pull Request describing your changes and test results.
