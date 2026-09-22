# Android 提醒与离线架构调研

核验日期：2026-09-19。目标是提醒/记录主流程离线可用，遵循 Android 新版本权限与省电限制，不用长时间真实等待验证。

## 1. 提醒机制

| 场景 | 方案 | 说明 |
|---|---|---|
| 用户要求在某一时刻提醒服药 | `AlarmManager.setExactAndAllowWhileIdle` 或 `setAlarmClock`，按设备/权限选择 | Android 12+ 精确闹钟受 `SCHEDULE_EXACT_ALARM` 或电源豁免限制；状态必须在设置页展示 |
| 不要求精确时刻的库存/过期扫描 | WorkManager/JobScheduler | 适合持久后台工作，不能代替精确服药提醒 |
| 重启、时区、时间变化 | `BOOT_COMPLETED`、`TIMEZONE_CHANGED`、`TIME_SET` 接收后从 Room SSOT 重建 | 旧 PendingIntent 取消/去重，重建应幂等 |
| 用户点击通知 | BroadcastReceiver/Activity action，写入实际状态和时间 | 采用唯一 dose occurrence ID，避免重复扣库存 |

官方依据：[AlarmManager API](https://developer.android.com/reference/android/app/AlarmManager)、[闹钟指南](https://developer.android.com/develop/background-work/services/alarms)、[Doze](https://developer.android.com/training/monitoring-device-state/doze-standby)、[WorkManager](https://developer.android.com/reference/androidx/work/package-summary)。Android 12+ 不精确闹钟在系统优化下可能延迟至少约 10 分钟；用户明确要求准点的场景才申请精确能力。

## 2. 权限与系统限制

- Android 13+ 需要运行时 `POST_NOTIFICATIONS`；拒绝后不能假设通知会展示，应在首次设置提醒时解释用途并允许重试。
- Android 12+ 需要检查 `AlarmManager.canScheduleExactAlarms()`；被撤销后未来精确闹钟可能取消，App 要进入“需修复”状态而非静默失败。
- Android 14 对 `USE_EXACT_ALARM` 有更严格的用途/商店政策；个人原型优先显式申请 `SCHEDULE_EXACT_ALARM` 并展示系统设置入口，正式上架前重新核对政策。
- Doze、厂商自启动/电池管理会影响后台行为；`setExactAndAllowWhileIdle` 仍需用户确认提醒权限和系统设置。
- 通知锁屏只显示“有一项用药提醒”，药名/剂量/疾病等敏感内容默认隐藏，允许用户主动选择更详细的公开级别。

## 3. 离线数据架构

- Room/SQLite：药物、成分、计划、剂量 occurrence、实际服用记录、库存、有效期、健康档案、规则结果和来源记录。
- DataStore：用户偏好、提醒提前量、思考档位、通知隐私级别和版本迁移标记；不存大文本和 API Key 明文。
- 应用私有文件：药盒/说明书图片、OCR 页面、导出草稿；路径只保存引用和哈希。
- FTS5/Room FTS：说明书章节与用户确认的文本；按药物/厂家/版本分区。
- Repository + UseCase + ViewModel/StateFlow：UI 不直接操作数据库，提醒调度器只消费领域状态。

数据一致性：以 Room 的 `DoseOccurrence` 为唯一事实；提醒、通知、统计、库存都是投影。`Taken/Skipped/Snoozed/Missed` 状态转换带时间和来源（用户点击/超时/系统恢复），库存扣减使用事务和 occurrence 唯一键。

## 4. 重启、时区和省电

每条计划保存 `ZoneId`、本地时间、有效日期范围和重复规则。旅行策略由用户选择：

1. 随设备本地时区移动；或
2. 保持处方所在地时区并在界面显著提示。

时区变化后只重算未来 occurrence，不修改历史事实。重启/时间校正后清理过期 PendingIntent，再从 SSOT 生成未来有限窗口；每次调度记录结果，失败原因可在诊断页查看。库存/有效期通过每日 WorkManager 任务补偿，不依赖网络。

## 5. 测试方法

按任务书时间盒，不做 30 分钟或数小时真实等待。注入 `Clock`/`TimeProvider`，让测试时间前移 1–2 分钟；直接调用 Receiver/UseCase 验证：创建 occurrence → 调度 → 触发 → Taken/Skip/Snooze → 库存/历史更新 → 重启重建。使用 Robolectric/本地 JVM 单测覆盖状态机，并在可用模拟器做一次短时核心冒烟。厂商后台限制和所有 Android 版本不在本轮穷举范围。

## 6. 预定技术栈

Kotlin + AndroidX/Compose Material 3 + Room + DataStore + AlarmManager + WorkManager + ML Kit；只增加必要的依赖并锁版本。AI/联网不是提醒的前置条件。Formal App 的 Gradle 构建放在 ASCII 路径，避免候选验证中发现的 Windows 非 ASCII 路径阻断。

