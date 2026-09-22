# OCR 与药品说明书本地资料库调研

核验日期：2026-09-19。目标是“拍照辅助录入 + 用户确认 + 可追溯本地资料”，不是把 OCR 直接当成药品事实。

## 1. OCR 方案比较

| 方案 | 优点 | 限制/隐私 | 建议 |
|---|---|---|---|
| Google ML Kit Text Recognition v2 Chinese | 官方 Android SDK；支持中文；可选 bundled 或 Google Play Services 模型；手机端低延迟 | unbundled 需要模型下载/Play Services；bundled 增大包体；闭源运行时依赖，离线行为要按变体验证 | 首选 MVP；药盒/说明书扫描先本地完成 |
| ML Kit Barcode | 离线扫码，适合条码/二维码辅助定位；Android API 23+ | bundled/unbundled 体积和模型可用性不同；条码不等于批准文号真伪 | 作为 OCR 前置和交叉校验 |
| PaddleOCR | 开源、中文生态强、可本地部署，官方仓库 Apache-2.0 | Android 集成、模型体积、ABI/NNAPI 性能和二次模型许可需要单独验证 | 后续离线增强候选，不在首个可安装版本强行引入 |
| DeepSeek Flash 图像输入 | 官方 `deepseek-flash` 支持图片，可处理复杂版面/低清图片 | 需要上传图片、产生 token 成本和隐私风险；网络/密钥不可用时不可用 | 仅作为用户明确触发的辅助识别/校对，不能替代本地 OCR 或人工确认 |

原始资料：[ML Kit 中文文字识别](https://developers.google.com/ml-kit/vision/text-recognition/v2?hl=zh-CN)、[支持语言](https://developers.google.com/ml-kit/vision/text-recognition/v2/languages?hl=zh-CN)、[Android 说明](https://developers.google.com/ml-kit/vision/text-recognition/v2/android?hl=zh-cn)、[条码扫描](https://developers.google.com/ml-kit/vision/barcode-scanning/android)、[PaddleOCR](https://github.com/PaddlePaddle/PaddleOCR)。ML Kit 当前文档列出的中文依赖版本需要在正式锁定时再次核对；本次没有把网络依赖写入最终 App。

## 2. 处理流程

1. 相机/系统照片选择器取得单张或多页照片，默认不上传。
2. 本地裁剪、旋转、透视/亮度增强；保留原图哈希和缩略图，原图由用户决定是否保留。
3. 先扫条码/二维码；再 OCR 识别中文、数字、日期和批准文号候选。
4. 用字段级解析器提取通用名、商品名、厂家、规格、剂型、批准文号、有效期、批号、用法关键词；记录字符框和置信度。
5. 对低置信度、相互冲突、关键字段缺失的项目显示人工确认表单；未确认值只能存为 `candidate`。
6. 用户确认后生成药物身份和来源引用；确认动作、时间和原图页号写入审计记录。
7. 说明书多页按页保存/裁切，按章节分段和索引；原始页只在本地加密存储。

## 3. 说明书本地资料模型

建议最小对象：

- `LeafletDocument`: `id`、药物身份、厂家、规格、剂型、地区、版本/日期、原图哈希、录入时间、确认状态。
- `LeafletPage`: `documentId`、页序、图片路径、OCR 原文、OCR 引擎/版本、语言、置信度摘要。
- `LeafletSection`: `pageRange`、标准章节（适应症、用法用量、禁忌、不良反应、相互作用、特殊人群、贮藏）、文本、token/字符范围。
- `SourceReference`: 来源类型、来源 URL/记录号、访问日期、页面/页码、原文片段、证据级别、地区、版本。
- `Correction`: 原始 OCR、用户修订值、字段、用户确认时间；不覆盖原文。

索引建议：Room/SQLite FTS 对 `LeafletSection` 做全文检索，结构化字段走普通索引；查询先按药物身份/厂家/规格/版本过滤，再按章节和关键词检索。送给 AI 只发送命中段落和引用，不发送整本病历或整本说明书。

## 4. 本地存储和隐私

- 原图、OCR 文本、用户健康资料放应用私有目录/数据库；不放公共媒体目录。
- 数据库密钥由 Android Keystore 保护；可选 SQLCipher/文件加密，不能把 Keystore 当作自动数据库加密。
- 默认关闭敏感内容云备份，使用 `dataExtractionRules` 排除或只备份用户主动导出的加密包。
- 图片上传给 `deepseek-flash` 前明确提示、缩小范围、去除姓名/住址/病历号；没有联网/密钥时仍完成本地录入。
- 网页和 OCR 文本均标记为不可信数据；文档中的“忽略系统规则/索取密钥/调用工具”等内容只显示为资料，不能改变系统提示词。

## 5. 测试样例与准确率观察

本轮没有用户提供的真实药盒/说明书样本，也没有伪造 OCR 准确率数字。已完成的是官方能力与数据流程核验；未确认字段准确率、中文小字体、曲面包装、折痕、竖排文字和有效期识别效果。阶段 B 使用至少三类**虚构/公开无敏感信息**样本，采用字段级结果表：通用名、规格、有效期、批准文号、厂家、章节标题；只统计人工复核后的准确率，并保留失败样本。

验收阈值不要只看字符 CER：关键字段应分别统计 exact match、字段缺失率、人工修订率；任何药名/规格/有效期低置信度都必须阻止自动写入可靠事实。

## 6. 阶段建议

MVP 使用 ML Kit Chinese + Barcode 的本地路径，保留 PaddleOCR 作为后续替换点。DeepSeek 视觉只用于用户显式请求的疑难图片辅助，不用于后台批量上传。说明书本地库先支持用户拍摄和手动粘贴文本，后续增加 PDF/图片分页；每一处回答都显示“第几份说明书、第几页、版本/日期”。

