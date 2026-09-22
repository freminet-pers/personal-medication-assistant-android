# 个人用药助手 research gateway

这是一个仅绑定 `127.0.0.1` 的受控后端原型。正式 AI 模型固定为 `deepseek-v4-flash`；研究检索走 DeepSeek 官方 Anthropic-compatible Web Search 工具，网页抓取沿用官方 Harness 的 HTTP(S)、同源重定向、公开地址、内容类型、大小上限和不可信内容告警边界。它不是模拟搜索：没有运行时密钥时会返回明确的 `WEB_PROVIDER_CREDENTIAL_MISSING`。

## 本地运行

```powershell
$env:DEEPSEEK_API_KEY = '<runtime-only-key>'
node src/server.mjs
Remove-Item Env:DEEPSEEK_API_KEY
```

密钥只能由临时环境变量或运行时安全输入提供；不要写入脚本、文档、Git、日志、APK 或测试夹具。线上部署应在隔离的受控服务中执行，限制网络出口、进程权限和日志字段。

## 接口

- `GET /health`
- `POST /v1/research/search`：`{"queries":["..."]}`，最多 4 个唯一查询。
- `POST /v1/research/fetch`：`{"url":"https://..."}`，拒绝 URL 凭据、私网解析、跨源重定向和超限响应。
- `POST /v1/assistant/answer`：问题与最小必要上下文；返回必须配合来源和证据级别展示。

研究实现依据官方仓库 `deepseek-ai/deepseek-harness` 的 `web-search-deepseek`、`web-fetch-http` 和 `tool-web` 源码行为；本项目固定审计提交见 `docs/第三方声明.md`。
