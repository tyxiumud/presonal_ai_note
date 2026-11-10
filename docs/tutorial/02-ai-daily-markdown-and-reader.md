# 第 2 课：AI 每日 Markdown 与历史阅览

> 前置：[第 1 课](01-local-text-journal.md)。本课假设你已经理解 Compose 页面 → ViewModel → Repository → Room 的链路，以及 `Flow` 如何自动刷新页面。

这一阶段把第 1 课的本地数据链路继续向外延伸：

```text
设置页
  ↓
Keystore 加密 API 密钥
  ↓
用户点击“整理这一天”
  ↓
DailyAnalysisService 读取当天 Capture
  ↓
OpenAI-compatible API 返回结构化 JSON
  ↓
应用本地生成 Markdown
  ↓
AtomicFile 写入 files/analysis/daily/
  ↓
Room 保存 DailyAnalysis 元数据
  ↓
阅览页同时显示原始随记与 AI Markdown
```

原始随记始终保留在 Room 的 `captures` 表中。AI 结果放在 `daily_analyses` 表和独立 Markdown 文件里，二者不会互相覆盖。

本课只讲整理与阅览。聊天复用同一套 API 客户端和本地资料，但有自己的检索、持久化和失败语义，放在第 3 课。

## 1. 页面分别负责什么

应用底部有三个主入口，设置在**右上角的六边形图标**里（点开是一张紧凑的底部卡片，不占主 Tab）。记录页参考了小米笔记：大标题、日期 chip、双列错落卡片、右下角黄色 + 悬浮按钮。

四个入口分别是：

- “记录”：新增随记、选择内容日期、手动整理所选日期、编辑或删除随记。
- “阅览”：选择已有日期，查看原始随记，以及一条一条的每日整理（点开是只读详情，可单独删除）。
- “聊天”：基于最近本地资料回答提问，并显示引用了哪些日期。
- “设置”弹层：配置 API 地址、模型、密钥、输出选项和对话语气预设。

根页面在 [PersonalVaultApp.kt](../../app/src/main/java/com/example/test0/ui/PersonalVaultApp.kt)。它用一个可保存的枚举切换三个主页面，没有引入 Navigation-Compose；`AppSection` 的三个值对应三个入口，每个入口都由自己的 `*Route` 收集 ViewModel 状态。设置内容通过一张自定义的底部卡片（`Dialog` + 圆角 `Surface`）呈现，和页面切换无关。

本课只展开“记录 / 阅览 / 设置”三个页面。聊天页从 `ChatRoute` 开始，完整流程见[第 3 课](03-local-context-chat.md)。

## 2. 如何填写 API

进入“设置”页面后填写：

- API 地址：服务商提供的 OpenAI-compatible 基础地址，例如 `https://api.example.com/v1`。
- 模型名称：服务商文档给出的模型 ID。
- API 密钥：从服务商控制台创建的密钥。
- 输出选项：决定是否生成任务、温和建议和思考问题。

点击“保存设置”，然后点击“测试连接”。测试会发起一次很小的请求，可能产生少量 API 费用。

应用会自动把基础地址补成 `/chat/completions`。如果服务商给你的地址已经以 `/chat/completions` 结尾，也可以直接填写。

只允许 HTTPS 地址，避免把私人记录和密钥通过明文 HTTP 发送。不同兼容服务对模型名称和请求参数的支持可能不同，出现错误时先核对服务商文档。

## 3. 密钥为什么没有写在代码里

设置模型位于 [AiSettings.kt](../../app/src/main/java/com/example/test0/ai/settings/AiSettings.kt)，实际存储位于 [KeystoreAiSettingsStore.kt](../../app/src/main/java/com/example/test0/ai/settings/KeystoreAiSettingsStore.kt)。

流程是：

1. Android Keystore 生成只属于本 App 的 AES 密钥。
2. API Key 使用 AES/GCM 加密。
3. 偏好文件只保存密文和随机 IV。
4. 发起请求前才在内存中解密。
5. 日志、数据库和 Markdown 都不保存 API Key。

卸载应用会删除这些本地设置。调试版 App 适合个人学习，但不要把 APK 和已配置的手机交给不可信的人。

## 4. 网络层怎样保持供应商可替换

领域接口是 [DailyAnalysisApi.kt](../../app/src/main/java/com/example/test0/ai/api/DailyAnalysisApi.kt)。

具体兼容实现是 [OpenAiCompatibleApiClient.kt](../../app/src/main/java/com/example/test0/ai/api/OpenAiCompatibleApiClient.kt)。它只负责：

- 组合 `POST /chat/completions` 请求；
- 添加 Bearer 密钥；
- 请求模型返回固定 JSON 字段；
- 把响应解析成 `StructuredDailyAnalysis`；
- 把 HTTP、空响应和错误格式转换成安全提示。

UI 和整理服务不知道供应商名称。以后增加另一种 API，只需要实现同一个接口。

当前同一个 [OpenAiCompatibleApiClient.kt](../../app/src/main/java/com/example/test0/ai/api/OpenAiCompatibleApiClient.kt) 还实现了 `ChatApi`，供第 3 课的聊天复用；底层 HTTP 细节封装在 [HttpTransport.kt](../../app/src/main/java/com/example/test0/ai/api/HttpTransport.kt)。本课先只看整理请求，聊天的消息格式和失败语义放到第 3 课。

## 5. 为什么不直接保存 AI 返回的 Markdown

模型被要求返回四个结构化字段：

- `summary`
- `tasks`
- `gentleSuggestions`
- `reflectionQuestions`

[DailyAnalysisService.kt](../../app/src/main/java/com/example/test0/analysis/domain/DailyAnalysisService.kt) 在本地把这些字段渲染成固定 Markdown。这样标题、任务复选框和可选栏目由 App 控制，模型不能随意改变文件结构。

## 6. Markdown 和数据库如何配合

Markdown 文件由 [DailyMarkdownStore.kt](../../app/src/main/java/com/example/test0/analysis/data/DailyMarkdownStore.kt) 写入：

```text
应用私有目录/
  analysis/
    daily/
      2026-11-05-<generation-id>.md
```

每次整理都会先用 `AtomicFile` 写入一个新的、不可变的版本文件。只有文件完整写好后，Room 才把当天元数据切换到新路径；数据库提交失败时删除候选文件，旧路径和旧 Markdown 保持不变。提交成功后再清理旧版本，即使进程在中间退出，也不会把新正文错误地配到旧元数据上。

Room 的 `daily_analyses` 表保存日期、当前文件路径、生成时间、模型名称、来源指纹，以及状态和错误信息。数据库从版本 1 升级到版本 2 时，通过显式 migration 新增 `daily_analyses` 表；第 3 课聊天加入后，版本又升到 3，并新增 `chat_messages` 表。两次迁移都不会删除原来的随记。

## 7. 来源指纹有什么作用

整理时，App 根据当天原始记录计算 SHA-256 指纹，并和 AI 结果一起保存。

阅览时重新计算当前记录指纹：

- 相同：AI Markdown 对应当前原始记录。
- 不同：整理后又新增了随记，页面显示“新增了记录，请重新整理”。

因此旧总结不会悄悄冒充最新内容。

## 8. 失败时会发生什么

- 没有配置 API：提示先去设置。
- 当天没有随记：不会发起请求。
- 网络失败或 HTTP 错误：显示可重试提示，不删除原始记录。
- AI 返回非法 JSON：不写入新 Markdown。
- 重新整理失败：之前成功的 Markdown 仍然保留。
- API Key 不会出现在错误信息中。

聊天有自己的一组失败语义：未配置、空回答、可重试与不可重试的区分见[第 3 课](03-local-context-chat.md)。

## 9. 推荐学习顺序

1. 从 [PersonalVaultApp.kt](../../app/src/main/java/com/example/test0/ui/PersonalVaultApp.kt) 看页面切换。
2. 看 [SettingsViewModel.kt](../../app/src/main/java/com/example/test0/ai/settings/SettingsViewModel.kt) 理解表单状态。
3. 看 [OpenAiCompatibleApiClient.kt](../../app/src/main/java/com/example/test0/ai/api/OpenAiCompatibleApiClient.kt) 理解 HTTP 与 JSON。
4. 看 [DailyAnalysisService.kt](../../app/src/main/java/com/example/test0/analysis/domain/DailyAnalysisService.kt) 理解业务编排。
5. 看 [ReaderViewModel.kt](../../app/src/main/java/com/example/test0/reader/ReaderViewModel.kt) 理解多个 Flow 如何组合成阅览状态。
6. 最后阅读对应测试，观察每条规则怎样被证明。

聊天已经实现，不再是“下一阶段”。它的入口、上下文检索、持久化和失败语义见[第 3 课：基于本地资料的聊天与引用](03-local-context-chat.md)。
