# Figma 发现与缺口分析

文件：`个人用药助手 · v0.1`  
File key：`PtD3iXllNuxL4lxMgSBVN2`  
核验日期：2026-09-19。

## 代码/计划侧

- 当前没有既有 Android UI 源码、Compose token、XML `colors.xml`、Code Connect 或现成组件。
- `design/tokens.json` 是本轮建立的 token 源事实；Figma 使用 slash-separated 变量名，Android 代码使用明确的 camelCase code syntax。
- v1 页面：今日用药、添加药物、家庭药箱、安全检查/助手、设置。
- v1 组件：Top app bar、底部导航、卡片、按钮、文本字段、状态徽章、风险提示卡、来源行、进度/剂量行。

## Figma 侧

- 初始文件为空：1 个 `Page 1`，0 个本地 variables/styles/components。
- 已订阅并搜索：Material 3 Design Kit、Simple Design System。
- 搜索命中：Material 3 Button、Text field、Card、Navigation Bar/Rail/Drawer、Simple Design System Card/Button 等；可以借鉴交互/命名语义，但没有导入为正式产品组件。
- 颜色/间距变量和 Typography style 搜索结果为空；阴影只找到若干库样式。

## 已创建的 Figma 对象

- Collections：`Primitives`、`Color`、`Spacing`、`Typography Primitives`、`Typography`、`Color Dark`。
- 原语：15 个品牌/状态色 + 2 个边框色、7 个间距、4 个圆角、9 个排版原语。
- Light semantic：背景、文字、品牌、状态、边框共 14 个变量，使用 alias 指向原语。
- Starter 计划拒绝 `Color.addMode('Dark')`，实际错误为 `Limited to 1 modes only`；因此改建了 `Color Dark` collection。

## 未完成与冲突

- 在创建 Dark 原语的下一次 Figma 调用前，Starter 计划触发 MCP rate-limit paywall；Dark 语义、文本/效果样式、页面、组件、截图和 Code Connect 均未完成。
- 没有 Figma 与代码的值冲突；只有 Dark collection 的额度阻塞。
- Android 代码将以 `design/tokens.json` 为运行时源；Figma 后续恢复额度后按此文件补齐 Dark alias、styles、components 和 screens，并更新 state ledger。

## 外部阻塞

2026-09-19 Figma MCP 返回：`You've reached the Figma MCP tool call limit on the Starter plan. Upgrade your plan...`。根据任务书时间盒，不重试、不等待升级；这不是本地代码完成度的成功信号，交付报告必须标为“Figma 部分完成”。
