# Personal Medication Assistant · v0.3.0

[English](README_EN.md) | [简体中文](README.md)

Personal Medication Assistant is a local-first Android medication record and reminder app. It separates Today, Medications, Documents, Health, and an auditable AI Assistant into five focused pages. The existing `applicationId` remains `com.lunamax.medassistant`; the current Debug build is `versionCode 3` / `versionName 0.3.0`.

## Screenshots

The screenshots come from an API 36.1 AVD with anonymous demo medication records. They contain no real API key or personal health information. Click any image to open the original file.

<p align="center">
  <a href="docs/screenshots/v0.3-today.png"><img src="docs/screenshots/v0.3-today.png" width="200" alt="Today plan and reminders"></a>
  <a href="docs/screenshots/v0.3-medications.png"><img src="docs/screenshots/v0.3-medications.png" width="200" alt="Medications, batches, and inventory"></a>
  <a href="docs/screenshots/v0.3-documents.png"><img src="docs/screenshots/v0.3-documents.png" width="200" alt="Document drafts and recognition queue"></a>
</p>
<p align="center">
  <a href="docs/screenshots/v0.3-health.png"><img src="docs/screenshots/v0.3-health.png" width="200" alt="Health profile and measurements"></a>
  <a href="docs/screenshots/v0.3-assistant.png"><img src="docs/screenshots/v0.3-assistant.png" width="200" alt="AI assistant and API key management"></a>
</p>

## Product pages and core features

- **Today**: review scheduled doses, mark occurrences, and see the next reminder state.
- **Medications**: maintain medication records, batches, stock, dosing plans, and reminders.
- **Documents**: import a local image or PDF, create a private recognition draft, review uncertain fields, and explicitly confirm before saving.
- **Health**: keep a local health profile and measurements that can be selectively summarized for an answer.
- **Assistant**: manage the runtime key, test the connection without sending personal data, ask evidence-aware questions, and optionally use controlled research search.

The app is local-first. Plans, status, inventory, health data, vision drafts, and assistant sessions stay in the local SQLite database by default. Sensitive fields use Android Keystore-backed AES/GCM encryption. The app supports backup/export boundaries that exclude the API key.

## DeepSeek Flash, search, and vision

- Text answers and research search are pinned to `deepseek-v4-flash`.
- Vision drafts use `deepseek-v4-flash-vision-exp` and are treated as untrusted drafts until the user reviews and confirms them.
- Research search uses the Anthropic-compatible Messages protocol with `web_search_20250305`; web material remains evidence, not an automatic medical conclusion.
- The assistant prompt requires evidence levels, sources, uncertainty, injection resistance, data minimization, and an explicit “when to seek care” boundary.

## Privacy and runtime API key

This repository contains no real API key. Enter a key only at runtime in the app or provide it through a secure runtime environment variable for the local gateway. Never put a key in Git, documentation, logs, screenshots, APK source/resources, or test fixtures. The key is stored through a dedicated Keystore alias and is excluded from backup and session export. Without a key, offline features remain available and documents can be saved as drafts awaiting recognition.

## Download and Release

- Repository: [freminet-pers/personal-medication-assistant-android](https://github.com/freminet-pers/personal-medication-assistant-android)
- Release download: [v0.3.0 Release](https://github.com/freminet-pers/personal-medication-assistant-android/releases/tag/v0.3.0)
- Versioned local artifact used for the release attachment: `个人用药助手-v0.3.0-debug.apk`
- Expected SHA-256: `8157FBE2CDC437C6118E0500D04BF5C1502FD6672359EE8086F0976E925011D7`

The APK is intentionally ignored by Git as a build/release artifact rather than committed as ordinary source. The release notes must retain the same hash and state the unverified environments below.

## Build and test

```powershell
Set-Location 'G:\项目\药\android-app'
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

For the Windows Chinese-path unit-test workaround, gateway tests, installation, and the full test matrix, see [构建说明](docs/构建说明.md), [测试与已知限制](docs/测试与已知限制.md), and the [v0.3.0 acceptance report](17_第四轮产品化重构验收报告.md).

## Verified and not verified

Verified locally: Android Debug compilation, Android unit tests, the research gateway contract/security tests (6/6), occurrence contracts, installation and cold start on the API 36.1 AVD, five-page navigation, Keystore temporary-string regression, and the final APK hash.

Not claimed as verified: a real DeepSeek request, real search or vision traffic, API 26/API 35 devices, or a physical device. No real key was available or used for this delivery. The Debug APK uses a debug signing configuration; production release signing, authentication, rate limiting, egress control, and regulatory review remain separate requirements.

## Medical disclaimer

This is a personal health-record and medication-assistance tool, not a doctor, pharmacist, emergency service, diagnostic system, or prescribing system. AI output can be wrong or incomplete. Check official instructions and qualified medical professionals; seek local emergency help for severe allergic reactions, breathing difficulty, altered consciousness, chest pain, or other urgent symptoms.

## License and third-party materials

This project is released under the [MIT License](LICENSE). The DeepSeek Harness Web Search/Web Fetch boundary and the pinned third-party audit reference are documented in [Third-party notice](docs/第三方声明.md). The project does not include the Harness executor in the Android APK.
