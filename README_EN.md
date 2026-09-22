# Personal Medication Assistant · v0.4.0

[English](README_EN.md) | [简体中文](README.md)

Personal Medication Assistant is a local-first Android medication record, reminder, and AI assistance app. It separates Today, Medications, Documents, Health, and an auditable Assistant into five focused pages. The existing package remains `com.lunamax.medassistant`; the current Debug build is versionCode 4 / versionName 0.4.0.

## v0.4.0 screenshots

These screenshots come from an API 36.1 AVD with anonymous demo data and no API key.

<p align="center">
  <a href="docs/screenshots/v0.4-assistant.png"><img src="docs/screenshots/v0.4-assistant.png" width="280" alt="Assistant page"></a>
  <a href="docs/screenshots/v0.4-ai-services.png"><img src="docs/screenshots/v0.4-ai-services.png" width="280" alt="AI Services page"></a>
</p>

## What changed in v0.4.0

- The built-in DeepSeek preset is displayed as “DeepSeek V4.1 Flash” and uses the API model ID `deepseek-flash`.
- Text answers and image recognition use the same selected Provider and model; there is no separate vision model.
- Users can add, edit, select, test, and delete custom Providers.
- The app supports native OpenAI Chat Completions, OpenAI Responses, and Anthropic Messages adapters.
- Search is an independent Search Profile: built-in DeepSeek search reuses the built-in DeepSeek key; other models require an explicitly configured and tested search profile.
- Provider and Search keys are isolated by configuration ID in Android Keystore. The UI reports credential status but never echoes a key.
- SQLite migrates from v6 to v7 while retaining the local medication, reminder, health, and document boundaries.

## Provider and multimodal boundary

Custom API does not mean arbitrary private-protocol compatibility. Each Provider declares its protocol, base URL, model ID, authentication mode, output-field convention, and image capability. Saving a profile and testing the connection are separate actions. Image requests are enabled only after the profile declares image input and passes an anonymous tiny-image capability test. The send flow still previews minimal context and requires confirmation.

Search uses native protocol tools only: OpenAI Responses `web_search`, Anthropic Messages `web_search_20250305`, and the built-in DeepSeek official search. A search profile must be saved, tested independently, and return structured URLs before the Assistant can use it.

## Privacy and medical boundary

- Plans, status, inventory, health data, document drafts, and sessions remain in local SQLite by default; sensitive fields use Android Keystore AES/GCM.
- API keys are excluded from SQLite backups, logs, source, screenshots, APK resources, and exported sessions; error messages are redacted.
- Images and PDFs first stay in the app-private directory and are sent to the selected Provider only after confirmation.
- AI output is evidence-aware assistance with uncertainty; it is not a doctor, pharmacist, label, emergency service, diagnostic system, or prescribing system.

This repository contains no real API key. Offline features remain available without a key, and documents can be saved while waiting for recognition. No real DeepSeek, search, or image request was made for this delivery.

## Build, test, and artifacts

The verified build environment uses Android Studio JDK 17, Android SDK platform 35, Build Tools 35.0.0, Gradle wrapper 8.7, and Android Gradle Plugin 8.6.1. The project entry point is `android-app`. On Windows, the Chinese workspace path can trigger a test-worker class-loading issue; the temporary ASCII X: mapping is documented in [构建说明](docs/构建说明.md).

Automated tests cover all three Provider protocols, unified text/image requests, independent search, safe error mapping, endpoint security, Provider defaults, and OccurrenceEngine. The API 36.1 AVD was checked at light/dark themes, approximately 412dp and 360dp widths, and 1.3x font scale. API 26/API 35, physical devices, and real API traffic are not claimed as verified.

Debug APK: `android-app/app/build/outputs/apk/debug/app-debug.apk`

Versioned delivery copies: `个人用药助手-v0.4.0-debug.apk` and `APK/个人用药助手-v0.4.0-debug.apk`. APK files are ignored by Git and are not ordinary source files.

See the [v0.4.0 acceptance report](19_v0.4.0_自定义API与统一多模态验收报告.md), [user guide](docs/用户使用说明.md), [architecture notes](docs/架构说明.md), and [test limitations](docs/测试与已知限制.md) for the full matrix.

Repository: [freminet-pers/personal-medication-assistant-android](https://github.com/freminet-pers/personal-medication-assistant-android). If the external push and Release are complete, the download page is [v0.4.0 Release](https://github.com/freminet-pers/personal-medication-assistant-android/releases/tag/v0.4.0); the acceptance report and final delivery status are authoritative.

## License

This project is released under the [MIT License](LICENSE). Third-party interface boundaries are recorded in [Third-party notice](docs/第三方声明.md). This is a personal health-record tool, not a medical diagnostic product.
