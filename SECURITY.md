# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | :white_check_mark: |

## Security Model & Best Practices

OpenClaw Native executes phone automation actions via Shizuku. To ensure safety:
* **Allowed Chat IDs**: Always set your specific Telegram User ID in settings. Never leave it public if running on a primary device.
* **API Keys**: Google Gemini API keys and Telegram bot tokens are stored locally in Android's private `SharedPreferences` and are never transmitted to any third-party telemetry server.
* **Direct IPC**: Shizuku communications use Android Binder IPC with uid permission verification.

## Reporting a Vulnerability

If you discover a security issue or vulnerability, please do NOT create a public issue. Instead, email the maintainers or open a private security advisory on GitHub.
