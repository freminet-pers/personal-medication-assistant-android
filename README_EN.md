# Personal Medication Assistant

[English](README_EN.md) | [简体中文](README.md)

> **Technical Preview**: this is a local-first personal medication record and reminder tool. AI is used only as optional assistance for organizing or checking materials after the user confirms the request, with traceable sources where available. It is not a doctor, pharmacist, diagnostic system, prescribing system, or emergency service.

## Start here

The current public baseline is v0.4.0 on main: versionCode 4 / versionName 0.4.0. The historical Android applicationId remains com.lunamax.medassistant. The public Release contains a **debug-signed APK** for technical preview use, not a production build.

- [v0.4.0 Technical Preview notes](docs/发布说明-v0.4.0.md)
- [v0.4.0 Release (debug APK)](https://github.com/freminet-pers/personal-medication-assistant-android/releases/tag/v0.4.0)
- [User guide](docs/用户使用说明.md)
- [Build and install guide](docs/构建说明.md)
- [Testing and known limits](docs/测试与已知限制.md)
- [Documentation index and archive notes](docs/文档索引.md)

The public evidence currently covers Android unit/contract tests, MockWebServer protocol tests, and specified checks on an API 36.1 AVD. Real API traffic, API 26/API 35, physical devices, production signing, and medical compliance are not claimed. The repository currently keeps historical screenshots named v0.3-*; they are not v0.4 evidence, so this page does not present them as a v0.4 preview. New anonymous v0.4 captures should be added only after they are recorded and reviewed. [Screenshot directory](docs/screenshots/)

## Core medication workflow

1. **Record medications**: maintain medications, real batches, inventory, and storage notes.
2. **Schedule reminders**: create daily, weekly, interval, one-off, or as-needed plans; confirm taken, skipped, snoozed, or undone events in Today.
3. **Keep source materials**: import an image/PDF into the app-private area first; recognition stays a draft until each field is reviewed.
4. **Keep health context**: record only the minimum useful allergy, history, and adverse-reaction context locally.
5. **Use the Assistant when needed**: select the minimum context and ask for evidence-aware organization or checking. Records and reminders work offline and do not require an API key.

## Who it is for / who it is not for

**Good fit**

- People who want a personal Android record for medications, reminders, inventory, and document drafts.
- People willing to review a context preview and explicitly confirm before sending content to an external Provider.
- People who want to keep sources, raw material, Provider/model metadata, and uncertainty visible for their own review.

**Not a fit**

- Anyone seeking diagnosis, prescribing, dose adjustment, a complete clinical interaction database, or treatment decisions.
- Anyone treating the app as emergency care, continuous monitoring, a clinical record system, or a multi-user health platform.
- Anyone who cannot accept that a custom Provider may retain requests, log traffic, or apply its own privacy terms.
- Production deployment without separate credentials, authentication, rate limits, egress controls, audit controls, release signing, and appropriate review.

For breathing difficulty, altered consciousness, chest pain, severe allergic reactions, or other urgent symptoms, contact local emergency services or a qualified clinician instead of waiting for the app or AI.

## AI assistance and confirmation gates

AI comes after the medication workflow and does not automatically change formal records:

- Saving a Provider profile is not the same as a successful connection; test it separately.
- A custom Provider declares its protocol, model, endpoint, and image capability. Image requests are enabled only after an anonymous tiny-image capability test succeeds.
- The send flow previews the minimum necessary context. The user confirms before a request is sent to the selected Provider or independent search service.
- Image/PDF recognition creates a reviewable draft. Unknown fields stay empty or are marked uncertain; only field-by-field confirmation can write medication, batch, or document data.
- Search requires an independent Search Profile, a separate test, and structured URLs. Web pages, tool results, and model output are untrusted input, not automatic medical conclusions.
- Answers should retain sources and uncertainty and direct the user back to the label, pharmacist, or clinician.

v0.4.0 includes native adapters for OpenAI Chat Completions, OpenAI Responses, and Anthropic Messages. This does not mean arbitrary private-protocol or arbitrary-JSON gateway compatibility. See the [architecture notes](docs/架构说明.md) and [third-party notice](docs/第三方声明.md).

## Data, backup, and key boundaries

- Plans, dose status, inventory, health context, document drafts, and sessions are local SQLite data by default; images/PDFs first remain in the app-private area.
- Sensitive data uses Android Keystore-backed AES/GCM. Provider and Search Profile keys are isolated; the UI reports credential status without echoing the key.
- API keys are not stored in source, Git-tracked content, logs, screenshots, APK resources, SQLite backups, or exported sessions. This repository contains no real key.
- After confirmation, the minimum necessary context is sent to the user-configured domain. The project cannot control a third-party service's retention, training, or privacy policy.
- Offline records and reminders remain available without a key or network. Never put a key in an issue, pull request, screenshot, test fixture, or document.

## Install, build, and verification boundary

The public v0.4.0 Release APK is debug-signed and has SHA-256 0BDB94DD9BC2133D72C8072D1AFAB4B77A63020D13CE65B35790422F2737F858. Verify the source and accept the Technical Preview risk before installing; this is not a production-signed or medical-device build.

The source build uses Android Studio JDK 17, Android SDK platform 35, Build Tools 35.0.0, Gradle wrapper 8.7, and Android Gradle Plugin 8.6.1. The entry point is android-app; commands and the Windows path workaround are in the [build guide](docs/构建说明.md).

| Scope | Public status |
|---|---|
| Android Java/resource compilation and Debug assemble | Reported as passing in the acceptance record |
| Unit tests, Occurrence contract, MockWebServer protocol contracts | Reported as passing |
| API 36.1 AVD, light/dark themes, approximately 412dp/360dp, 1.3x font scale | Reported as passing |
| Real DeepSeek/custom Provider/search/image traffic | Not verified; no real key was used for this delivery |
| API 26/API 35, physical devices, camera/notification vendor differences | Not verified |
| Release signing, store publication, medical compliance, security audit | Not verified and not promised |

## Documentation and historical archive

- Current user-facing material lives under docs/; start with the [documentation index](docs/文档索引.md).
- The former root-level 01_–19_ research, planning, and acceptance files plus Luna_Max_药物健康助手_调研任务书.md now live under docs/archive/ as research / plans / validation. Their content and Git history semantics are preserved; they are not current product promises.
- Luna Max remains only in historical/internal and compatibility naming. The public product name is Personal Medication Assistant; the package name is not changed as part of documentation governance.

## License

Released under the [MIT License](LICENSE). See [CONTRIBUTING.md](CONTRIBUTING.md) and [SECURITY.md](SECURITY.md) for contribution and security boundaries. This is a personal health-record tool, not a medical diagnostic product.
