# GitHub 候选项目调研

核验日期：2026-09-19。本文只记录代码级检查和本机实际尝试，不把 README 宣称当成构建成功。

## 1. 范围与方法

候选项目均浅克隆到 `调研证据/候选项目/`，未复制进正式 App 目录。每个项目记录仓库、核验 commit、许可证、技术栈、隐私与构建结果。候选池来自 GitHub 原始仓库与 README，技术判断以源码、Gradle 配置、Manifest 和本机命令为准。

## 2. 候选总表

| 项目 | 原始仓库 | 核验 commit / 日期 | 许可证 | 主要价值 | 实际验证 | 结论 |
|---|---|---|---|---|---|---|
| medTimer | [Futsch1/medTimer](https://github.com/Futsch1/medTimer) | `ba06327b0c4981bcbf10634ed687a240d42852a4` / 2026-09-14 | MIT | 离线提醒、库存、过期、CSV/JSON、FOSS/full | `:app:assembleFossDebug` 与单测启动，因 JVM 访问 Maven/Google TLS 握手失败 | 最适合借鉴提醒与离线产品边界，不直接复制 |
| Dose | [waseefakhtar/dose-android](https://github.com/waseefakhtar/dose-android) | `cf6e2fc9b399b25b9a643a7544f4a7c1a660cf57` / 2025-02-15 | MIT | Compose、Material 3、Room、Hilt、Firebase 的基础示例 | `:app:assembleDebug` 与单测启动，先被 Windows 非 ASCII 路径检查阻断 | 可借鉴分层与 UI 入门；Firebase 与早期产品形态不适合作为核心 |
| Anshin | [Tinnci/anshin](https://github.com/Tinnci/anshin) | `6ff1961cb48d091aab91d768ab995c1644fc4375` / 2026-08-16 | README 声称 Apache-2.0；本次快照未找到根目录 LICENSE，未确认 | 中文/中医药目录、精确提醒、库存、健康记录、模块化 | `:app:assembleDebug` 与单测启动，KSP `2.3.8` 插件解析失败 | 功能覆盖最接近，但许可证与快速变动依赖必须先补证，不直接复用 |
| Featherline | [mkx173/Featherline](https://github.com/mkx173/Featherline) | `e9a3d180fef7c32bf59f06e42832a04762eb1d2d` / 2026-09-05 | GPL-3.0 | SQLCipher Room、无网络/无遥测、加密备份、中文 | 启动过一次 `assembleDebug`；工具会话未返回可确认结果，未发现可交付 APK，因此不计为成功 | 可借鉴本地加密、备份与精确提醒；GPL-3.0 与本项目发布策略冲突，不能直接合入 |
| MediTrak | [AdamGuidarini/MediTrak](https://github.com/AdamGuidarini/MediTrak) | `5d6df343c4ce1f192a7a497c1bcb4e34ae5b7bb9` / 2026-09-18 | GPL-2.0 | 多患者、间隔提醒、本地 SQLite、F-Droid 生态 | 代码/Manifest/依赖检查；本轮未安排第二次全量构建 | 适合观察旧式本地数据模型，GPL-2.0、JNI/SQLite/XML 改造成本高 |

评分采用 1–5 分，5 为更适合本项目；“许可证”分只表示合入可行性，不代表许可证优劣。

| 项目 | 功能匹配 | 代码/架构 | 构建可行性 | 维护状态 | 许可证 | 安全/隐私 | 扩展性 | 中文适配 | 改造成本 | 总分 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| medTimer | 5 | 4 | 2 | 5 | 5 | 4 | 4 | 2 | 3 | 34/45 |
| Anshin | 5 | 4 | 2 | 4 | 2 | 3 | 5 | 5 | 3 | 33/45 |
| Dose | 3 | 4 | 2 | 3 | 5 | 2 | 4 | 1 | 4 | 28/45 |
| Featherline | 3 | 4 | 2 | 4 | 1 | 5 | 3 | 4 | 2 | 28/45 |
| MediTrak | 3 | 3 | 2 | 4 | 1 | 3 | 2 | 1 | 2 | 21/45 |

## 3. Top 候选代码级评估

### 3.1 medTimer：提醒与离线行为参考首选

- README 描述了多药、灵活周期、暂停/稍后、库存、有效期、历史、CSV/JSON 和无网络使用；源码为多模块 Kotlin/Android 项目，包含 FOSS/full 变体。
- Gradle/Manifest 检查到 Android 现代提醒所需的 `POST_NOTIFICATIONS`、`RECEIVE_BOOT_COMPLETED`、精确提醒相关能力和电池优化例外提示；正式项目仍要重新按官方 Android 权限政策实现。
- MIT 许可证允许在保留版权与许可文本的前提下参考或复用，但本项目不直接拷贝业务代码，避免把未审查的医疗语义带入。
- 实际构建命令和失败原因见 `调研证据/构建日志/候选项目构建验证-2026-09-19.md`。失败发生在依赖解析阶段，不能据此判断源码不编译。

推荐借鉴：提醒状态机、离线优先、库存/过期的领域边界、FOSS 与 Google 依赖隔离思路。需要重写：中文药品资料、OCR、说明书引用、健康档案和安全规则。

### 3.2 Anshin：功能覆盖最接近但许可证需阻断式确认

- README 公开列出今日剂量、Taken/Skip/Undo、精确 AlarmManager、重启恢复、库存、健康记录、中文药品目录、相互作用提示、CameraX/ML Kit Barcode、Room/KSP/Hilt/WorkManager 等。
- 结构上有 `app`、`core`、`feature`、数据库及 OCR 相关目录，包含 SSOT、投影、版本化 Room migration 等文档线索，适合研究“持久事实 → 提醒/通知投影”的设计。
- README badge 与正文声称 Apache-2.0，但本次浅克隆根目录没有 LICENSE 文件；在拿到可核对的许可证文件或上游明确授权前，不能复制源代码、图标、数据或数据库。
- 本机构建因 KSP Gradle 插件 `2.3.8` 无法从仓库解析而失败，命令未进入 Kotlin 编译阶段。

### 3.3 Featherline：本地加密与备份参考，不能作为代码基线

- README 与源码结构确认其是 Kotlin/Compose/Room/SQLCipher 的离线应用，包含精确提醒、重启/时区重建、加密压缩备份、生物识别和简体中文。
- 根许可证为 GPL-3.0，且 README 还列出改编代码与第三方通知；本项目后续若采用不同发布方式，直接合入会产生 copyleft 与通知义务，故只作架构参考。
- 其 HRT/药代动力学领域模型不应被迁移为通用药物风险结论，尤其不能把人口平均 PK 预测当成剂量建议。

### 3.4 Dose 与 MediTrak：局部参考

Dose 的 MIT 许可、Compose/Room/Hilt 组合清晰，适合快速比较现代 Android 结构；但 README 仍把 Firebase Analytics/Crashlytics 作为默认依赖，本项目的健康隐私设计不应默认引入遥测。MediTrak 的 GPL-2.0、JNI/SQLite/XML、Android 10+ 和多语言本地数据可作兼容性观察，但不作为新项目技术基线。

## 4. 实际验证记录

本机环境：Android Studio 自带 JBR，Gradle Wrapper 可启动；工作区路径含中文。Gradle/JVM 访问 Maven Central/Google 仓库时发生 TLS handshake/EOF，系统 `curl.exe` 对同类 URL 可访问，说明至少有运行时 TLS/代理链差异。

| 候选 | 命令 | 结果 | 证据 |
|---|---|---|---|
| medTimer | `JAVA_HOME=Android Studio\\jbr; .\\gradlew.bat :app:assembleFossDebug :app:testFossDebugUnitTest --no-daemon --console=plain --stacktrace` | 失败：依赖下载 TLS handshake | `调研证据/构建日志/...`；另有 Gradle 输出摘要 |
| Dose | `... :app:assembleDebug :app:testDebugUnitTest ...` | 失败：AGP 明确拒绝非 ASCII 项目路径 | 同上 |
| Anshin | `... :app:assembleDebug :app:testDebugUnitTest ...` | 失败：KSP `2.3.8` plugin not found | 同上 |
| Featherline | `... :app:assembleDebug :app:testDebugUnitTest ...` | 未确认；未见成功 APK，不作通过结论 | 同上 |

按照任务书时间盒，本轮不再次重复同一失败；正式项目将放在 ASCII 路径的独立 checkout，并只做一次全量构建。

## 5. 复用建议与许可证边界

1. 正式项目自主开发；仅吸收通用架构思想和 Android 官方 API，不复制候选项目的业务代码、图标、截图、药品数据或数据库。
2. 只考虑 MIT/Apache-2.0 兼容的明确依赖；GPL 项目保留在证据目录，不进入正式构建依赖。
3. 每个正式依赖锁定版本并生成依赖清单；实际采用的依赖才做许可证扫描。
4. DeepSeek Harness 是独立的 MIT 项目，需保留许可证与第三方 notices；其安全声明明确为 developer preview，不能把它当成医疗安全控制。

## 6. 原始链接

- [medTimer](https://github.com/Futsch1/medTimer)
- [Dose](https://github.com/waseefakhtar/dose-android)
- [Anshin](https://github.com/Tinnci/anshin)
- [Featherline](https://github.com/mkx173/Featherline)
- [MediTrak](https://github.com/AdamGuidarini/MediTrak)
- [DeepSeek Harness](https://github.com/deepseek-ai/deepseek-harness)

