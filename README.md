# 个人用药助手

[简体中文](README.md) | [English](README_EN.md)

> **Technical Preview（技术预览）**：这是一个本地优先的个人用药记录与提醒工具。AI 只在用户明确确认、请求范围最小化且来源可追溯时，辅助整理资料或核对信息；它不是医生、药师、诊断系统、处方系统或急救服务。

## 先看结论

当前公开基线是 main 上的 v0.4.0：versionCode 4 / versionName 0.4.0，应用的历史 applicationId 仍为 com.lunamax.medassistant。公开 Release 提供的是 **Debug 签名 APK**，用于技术预览，不是生产发布。

- [v0.4.0 Technical Preview 说明](docs/发布说明-v0.4.0.md)
- [v0.4.0 Release（Debug APK）](https://github.com/freminet-pers/personal-medication-assistant-android/releases/tag/v0.4.0)
- [用户使用说明](docs/用户使用说明.md)
- [构建与安装说明](docs/构建说明.md)
- [测试与已知限制](docs/测试与已知限制.md)
- [文档索引与历史档案说明](docs/文档索引.md)

当前只公开声称：Android 单元/契约测试、MockWebServer 协议测试，以及 API 36.1 AVD 上的指定页面与尺寸检查通过。真实 API 流量、API 26/API 35、实体设备、生产签名和医疗合规均未声称通过。仓库目前保留的是 v0.3 文件名的历史截图；它们不是 v0.4 证据，本页不把旧图当作 v0.4 页面预览。待重新录制并审阅匿名 v0.4 画面后再补充。[截图目录](docs/screenshots/)

## 核心用药工作流

1. **记录药物**：在“药物”页维护药物、实际批次、库存和储存信息。
2. **安排提醒**：建立每日、每周、间隔、临时或按需计划，在“今日”页确认已服用、跳过、稍后或撤销。
3. **保留资料**：把说明书图片/PDF 先放在应用私有目录；识别结果先是草稿，逐字段核对后才写入正式记录。
4. **记录健康背景**：在“健康”页维护过敏史、既往史和不良反应等最小必要资料。
5. **需要时再用助手**：在本地记录基础上，用户可选择最小必要上下文，请 AI 整理资料或辅助核对。助手不是必需组件，离线记录和提醒不依赖 API Key。

## 适合谁 / 不适合谁

**适合**

- 希望在 Android 设备上维护个人用药、提醒、库存和资料草稿的人。
- 愿意在把内容发送到外部 Provider 前查看摘要并明确确认的人。
- 希望保留来源、原始资料、模型/Provider 信息和不确定字段，自己复核的人。

**不适合**

- 需要诊断、处方、剂量调整、完整临床相互作用数据库或治疗决策的人。
- 把应用当作急救、持续监护、临床病历系统或多人共享医疗平台的人。
- 不能接受自定义 Provider 可能记录请求内容、保留日志或适用其自身隐私条款的人。
- 还没有完成密钥、认证、限流、出口控制、审计和发布签名配置的生产部署。

出现呼吸困难、意识改变、胸痛、严重过敏或其他紧急信号时，请优先联系当地急救服务或专业医护人员，不要等待应用或 AI 回复。

## AI 辅助的确认门

AI 位于核心用药流程之后，并且默认不自动改变正式记录：

- 保存 Provider 配置不等于连接成功；需要单独执行连接测试。
- 自定义 Provider 必须声明协议、模型、端点和图片能力；图片请求只有在匿名小图能力测试通过后才允许启用。
- 发送前展示最小必要上下文；用户确认后才会向当前 Provider 或独立搜索服务发出请求。
- 图片/PDF 识别只产生待核对草稿；未知字段保持空值或进入不确定字段，逐字段确认后才写入药物、批次或资料。
- 搜索必须使用独立 Search Profile、单独测试并返回结构化 URL；网页、工具结果和模型输出都是不可信输入，不能自动变成医疗结论。
- 回答应保留来源和不确定性，并提示回到说明书、药师或医生处核对。

v0.4.0 支持 OpenAI Chat Completions、OpenAI Responses 和 Anthropic Messages 三种原生协议适配器；这不代表兼容任意私有协议或任意 JSON 网关。详细边界见[架构说明](docs/架构说明.md)和[第三方声明](docs/第三方声明.md)。

## 数据、备份与密钥边界

- 计划、服用状态、库存、健康资料、资料草稿和会话默认保存在本机 SQLite；图片/PDF 先保存在应用私有目录。
- 敏感数据使用 Android Keystore-backed AES/GCM；API Key 按 Provider/Search Profile 隔离保存，界面只显示凭据状态，不回显完整 Key。
- API Key 不进入源码、Git 追踪内容、日志、截图、APK 资源、SQLite 备份或会话导出。本仓库不包含真实 Key。
- 用户确认后才会把最小必要上下文发送到用户配置的域名。第三方服务的数据留存、训练和隐私政策不由本项目控制，请配置前自行核对。
- 没有 Key 或网络不可用时，仍可使用本地记录、提醒，并把资料保存为等待识别；不要把 Key 写进 issue、PR、截图、测试夹具或文档。

## 安装、构建与验证边界

公开 Release 的 APK 是 v0.4.0 Debug 构建，SHA-256 为 0BDB94DD9BC2133D72C8072D1AFAB4B77A63020D13CE65B35790422F2737F858。安装前请确认来源并接受 Technical Preview 风险；不要把它当作生产签名或医疗器械软件。

从源码构建需要 Android Studio JDK 17、Android SDK platform 35、Build Tools 35.0.0、Gradle wrapper 8.7 和 Android Gradle Plugin 8.6.1。构建入口为 android-app，命令与 Windows 中文路径 workaround 见[构建说明](docs/构建说明.md)。

| 范围 | 当前公开状态 |
|---|---|
| Android Java/资源编译、Debug assemble | 验收记录为通过 |
| 单元测试、Occurrence 合同、MockWebServer 协议合同 | 验收记录为通过 |
| API 36.1 AVD、明暗主题、约 412dp/360dp、1.3 倍字号 | 验收记录为通过 |
| 真实 DeepSeek/自定义 Provider/搜索/图片流量 | 未验证；本轮未使用真实 Key |
| API 26/API 35、实体设备、相机/通知厂商差异 | 未验证 |
| Release 签名、商店发布、医疗合规、安全审计 | 未验证，不作承诺 |

## 文档与历史档案

- 面向使用者的当前文档在 docs/；从[文档索引](docs/文档索引.md)开始。
- 原根目录的 01_–19_ 调研、计划和验收文件，以及 Luna_Max_药物健康助手_调研任务书.md，现已按 research / plans / validation 移入 docs/archive/；内容与 Git 历史语义保留，不是当前产品承诺。
- Luna Max 只出现在历史内部档案和兼容命名中；公开产品名是“个人用药助手 / Personal Medication Assistant”，包名暂不因展示治理而变更。

## 许可证

本项目使用 [MIT License](LICENSE)。贡献和安全边界见 [CONTRIBUTING.md](CONTRIBUTING.md) 与 [SECURITY.md](SECURITY.md)。本项目是个人健康记录工具，不提供医疗诊断。
