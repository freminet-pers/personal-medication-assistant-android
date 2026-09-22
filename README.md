# 个人用药助手 · v0.4.0

[简体中文](README.md) | [English](README_EN.md)

一个本地优先的 Android 用药记录、提醒和 AI 辅助应用：今日计划、药物/批次/库存、资料草稿、健康档案和可审计助手分成五个页面。包名保持 `com.lunamax.medassistant`，当前 Debug 构建为 versionCode 4 / versionName 0.4.0。

## v0.4.0 页面预览

截图来自 API 36.1 AVD，使用无 Key 的匿名演示数据。

<p align="center">
  <a href="docs/screenshots/v0.4-assistant.png"><img src="docs/screenshots/v0.4-assistant.png" width="280" alt="助手页"></a>
  <a href="docs/screenshots/v0.4-ai-services.png"><img src="docs/screenshots/v0.4-ai-services.png" width="280" alt="AI 服务页"></a>
</p>

## v0.4.0 更新

- 内置 DeepSeek 预设显示为“DeepSeek V4.1 Flash”，实际模型 ID 为 `deepseek-flash`。
- 文字问答与图片识别共用当前 Provider 和同一模型，不再使用独立 vision 模型。
- 新增可保存、编辑、切换、测试和删除的自定义 Provider。
- 支持 OpenAI Chat Completions、OpenAI Responses、Anthropic Messages 三种原生协议；base URL 会自动拼接对应 endpoint。
- 联网搜索改为独立 Search Profile：DeepSeek 官方搜索复用内置 DeepSeek Key；其他模型必须单独配置并测试通过。
- Provider Key 与 Search Key 按配置 ID 分离存入 Android Keystore；界面只显示凭据状态，不回显 Key。
- SQLite 从 v6 迁移到 v7；历史药物、提醒、健康资料和资料索引保持原有本地边界。

## Provider 与多模态边界

自定义 API 不是“任意私有协议兼容”。每个 Provider 都要明确协议、base URL、模型 ID、认证头、输出字段和图片能力；保存配置与连接测试是两个动作。图片能力只有在声明并通过匿名小图测试后才允许发送。发送前仍会显示最小必要上下文并要求用户确认。

搜索只使用协议原生工具：OpenAI Responses 的 `web_search`、Anthropic Messages 的 `web_search_20250305`，以及内置 DeepSeek 官方搜索。搜索必须独立保存、单独测试并返回结构化 URL 后才会在助手页启用。

## 隐私与医疗边界

- 计划、状态、库存、健康资料、资料草稿和会话默认保存在本机 SQLite；敏感字段使用 Android Keystore AES/GCM。
- API Key 不进入 SQLite 备份、日志、源码、截图、APK 资源或导出会话；错误消息会脱敏。
- 图片/PDF 先保存到应用私有目录，只有用户确认后才发送到当前 Provider。
- AI 输出是带来源和不确定性的辅助建议，不替代医生、药师、说明书或急救服务。

本仓库不包含真实 API Key。没有 Key 时可使用离线功能，资料可保存为等待识别；本轮未执行真实 DeepSeek、搜索或图片请求。

## 构建、测试与交付

环境为 Android Studio JDK 17、Android SDK platform 35、Build Tools 35.0.0、Gradle wrapper 8.7。构建入口是 `android-app`；中文路径下单元测试若出现测试类找不到，可使用临时 ASCII 映射盘 X:，详见 [构建说明](docs/构建说明.md)。

自动化测试覆盖三种 Provider 协议、统一文字/图片请求、独立搜索、错误脱敏、端点安全规则、Provider 配置默认值和 OccurrenceEngine。API 36.1 AVD 已完成安装启动、明暗主题、360dp/约 412dp 和 1.3 倍字号检查；API 26/API 35、真机和真实 API 流量未宣称通过。

Debug APK：`android-app/app/build/outputs/apk/debug/app-debug.apk`

版本化交付副本：`个人用药助手-v0.4.0-debug.apk`；同一文件也复制到 `APK/个人用药助手-v0.4.0-debug.apk`，APK 按 .gitignore 忽略，不作为源码提交。

完整矩阵见 [v0.4.0 验收报告](19_v0.4.0_自定义API与统一多模态验收报告.md)、[用户使用说明](docs/用户使用说明.md)、[架构说明](docs/架构说明.md) 和 [测试与已知限制](docs/测试与已知限制.md)。

GitHub 仓库：[freminet-pers/personal-medication-assistant-android](https://github.com/freminet-pers/personal-medication-assistant-android)。若外部推送与 Release 已完成，可从 [v0.4.0 Release](https://github.com/freminet-pers/personal-medication-assistant-android/releases/tag/v0.4.0) 下载；发布状态以验收报告和最终交付说明为准。

## 许可证

本项目使用 [MIT License](LICENSE)。第三方接口范围见 [第三方声明](docs/第三方声明.md)。本项目是个人健康记录工具，不提供医疗诊断。
