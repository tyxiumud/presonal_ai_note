# 第 3 课：基于本地资料的聊天与引用

> 前置：[第 1 课](01-local-text-journal.md) 和[第 2 课](02-ai-daily-markdown-and-reader.md)。本课假设你已经理解 Compose 页面 → ViewModel → Repository → Room 的链路，知道设置页如何保存 API 配置、每日整理如何生成 Markdown，以及来源指纹的作用。

前两课的数据仍然全部保留：

- `captures` 保存用户亲手写下的原始随记。
- `daily_analyses` 保存每日整理的元数据，正文在应用私有目录的 `analysis/daily/`。
- 本课新增 `chat_messages`，只保存聊天对话和引用信息。

聊天建立在同一条链路上：

```text
用户输入问题
  ↓
ChatViewModel（草稿 / 发送中 / 错误 / 是否可重试）
  ↓
ChatService
  ├─ 读取最近 8 条历史
  ├─ ChatContextBuilder：最近 7 天、2000 字符预算
  ├─ ChatApi → OpenAiCompatibleApiClient → HttpTransport
  └─ 保存回答 + 引用日期 / 记录 ID
  ↓
Room chat_messages
  ↓
Flow 自动回流
  ↓
ChatScreen 显示气泡与“参考：11月3日”
```

这里有两条必须记住的规则：

1. **上下文是本地挑出来的，不是把整本资料库发给模型。** 页面副标题写的是“从你的本地随记里找答案，不会发送整本资料库”。
2. **引用是本地记录的实际发送内容，不依赖模型自己声明。** 因此回答下方展示的日期可以被用户核实。

## 1. 聊天页：ChatRoute 与 ChatScreen

入口文件是 [ChatScreen.kt](../../app/src/main/java/com/example/test0/chat/ui/ChatScreen.kt)。

和记录页一样，聊天页分成两层：

- `ChatRoute` 只做一件事：收集 ViewModel 状态，把回调接上。
- `ChatScreen` 只根据 `ChatUiState` 绘制，不直接调用服务或数据库。

```kotlin
@Composable
fun ChatRoute(viewModel: ChatViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ChatScreen(
        state = state,
        onDraftChanged = viewModel::onDraftChanged,
        onSend = viewModel::send,
        onRetry = viewModel::retry,
        onSuggestionSelected = viewModel::onSuggestionSelected,
        onClearConversation = viewModel::clearConversation,
        onDeleteMessage = viewModel::deleteMessage,
        onSelectConversation = viewModel::selectConversation,
        onCreateConversation = viewModel::createConversation,
        onDeleteConversation = viewModel::deleteConversation,
    )
}
```

页面从上到下由三部分组成：

1. **列表区**：`LazyColumn` 显示标题、空状态建议、消息气泡和“正在翻你的记录…”气泡。
2. **消息气泡**：用户消息靠右，助手消息靠左；助手消息下方显示引用日期，没有引用时显示“未引用本地资料”。
3. **底部输入区**：输入框、发送按钮、错误提示和“重试”按钮。

几个容易忽略的交互规则：

- 只有输入非空且不在发送中时，发送按钮才可用。
- 发送中长按消息不会弹出删除确认，避免删除正在进行的一轮对话。
- “清空”只在有消息且不在发送时出现；删除单条消息需要长按并二次确认。
- 消息数量或发送状态变化时，列表自动滚到最后一条。
- 输入框使用 `imePadding()`，键盘弹出时底部输入区不会被遮住。

引用展示集中在 `ReferenceLabel`：

```kotlin
@Composable
private fun ReferenceLabel(message: ChatMessage) {
    val days = message.referencedDays
    val label = if (days.isEmpty()) {
        stringResource(R.string.chat_no_references)
    } else {
        stringResource(
            R.string.chat_references,
            days.joinToString("、") { formatDay(it) },
        )
    }
    Text(
        text = label,
        modifier = Modifier.padding(start = 6.dp, top = 6.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
```

这里只显示日期，不显示原始记录 ID。`referencedCaptureIds` 仍然保存在数据库里，方便以后做“点开引用查看原文”之类的功能。

建议断点：`ChatScreen`。先看空状态，再发一条消息，观察发送中气泡和引用标签。

对应界面测试在 [ChatScreenTest.kt](../../app/src/androidTest/java/com/example/test0/chat/ui/ChatScreenTest.kt)。

## 2. ChatViewModel：状态、草稿和失败处理

状态逻辑在 [ChatViewModel.kt](../../app/src/main/java/com/example/test0/chat/ui/ChatViewModel.kt)。

页面状态有七项，其中 `conversations` 和 `currentConversationId` 是多会话加进来的：

```kotlin
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val conversations: List<Conversation> = emptyList(),
    val currentConversationId: Long = DEFAULT_CONVERSATION_ID,
    val draft: String = "",
    val isSending: Boolean = false,
    val errorMessage: String? = null,
    val canRetry: Boolean = false,
) {
    val canSend: Boolean get() = draft.isNotBlank() && !isSending
}
```

`uiState` 由消息流、会话列表、当前会话、草稿、发送中、错误和可重试标志组合而成。消息流来自 `chatRepository.observeMessages(currentConversationId)`，并用 `flatMapLatest` 跟着当前会话切换：切会话时取消上一个订阅，只订阅新会话的消息。数据库一变化，界面就自动刷新；如果读取消息失败，流会退化成空列表，但不会让整个页面崩掉。

草稿保存在 `SavedStateHandle` 的 `chat_draft` 键里，当前会话 id 存在 `chat_conversation_id` 键里。所以 Activity 重建，甚至进程被系统回收后重新进入页面时，没发出去的半句话还在，而且会回到原来那个会话。ViewModel 初始化时会检查这个 id 对应的会话是否还存在（可能已被删除），不存在就回退到最新的一个。

发送逻辑有一个重要顺序：

```kotlin
fun send() {
    if (isSending.value) return
    val text = draft.value.trim()
    if (text.isEmpty()) {
        errorMessage.value = "请输入想问的内容"
        canRetry.value = false
        return
    }

    // 用户消息会先落库再请求，因此这里立刻清空输入框；配置缺失时再恢复原文。
    savedStateHandle[KEY_DRAFT] = ""
    request {
        try {
            chatService.send(text)
        } catch (throwable: Throwable) {
            if (throwable is ChatNotConfiguredException) {
                savedStateHandle[KEY_DRAFT] = text
            }
            throw throwable
        }
    }
}
```

为什么先清空、失败再恢复？因为用户消息会先写入数据库，清空输入框可以避免用户误以为没发出去而重复点击。只有“还没配置 API”这种请求根本没发出的情况，才把原文放回输入框。

统一的请求包装负责三件事：进入发送中、清空旧错误、结束后退出发送中。

```kotlin
private fun request(block: suspend () -> Unit) {
    viewModelScope.launch {
        isSending.value = true
        errorMessage.value = null
        canRetry.value = false
        try {
            block()
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            errorMessage.value = throwable.message
                ?.takeIf(String::isNotBlank)
                ?: "发送失败，请稍后重试"
            canRetry.value = throwable !is ChatNotConfiguredException
        } finally {
            isSending.value = false
        }
    }
}
```

`canRetry` 的规则很关键：**未配置 API 时不显示重试按钮**，因为重试也不会成功，应该先去设置页；其他错误（网络、HTTP、空回答）都允许重试。

清空对话和删除单条消息都直接调用仓库，并在失败时显示“清空失败，请稍后重试”或“删除失败，请稍后重试”。这两个操作都不会碰原始随记和每日整理。

建议断点：

- `send()`：观察草稿什么时候清空、什么时候恢复。
- `request()`：观察 `isSending`、`errorMessage`、`canRetry` 如何变化。
- `retry()`：确认重试不会重复保存用户问题。
- `clearConversation()` / `deleteMessage()`：确认只影响 `chat_messages`。

对应测试在 [ChatViewModelTest.kt](../../app/src/test/java/com/example/test0/chat/ui/ChatViewModelTest.kt)。

## 3. ChatService：一轮问答的完整流程

领域编排在 [ChatService.kt](../../app/src/main/java/com/example/test0/chat/domain/ChatService.kt)。

一次 `send()` 按顺序做五件事：

1. 去掉首尾空白；空问题直接拒绝，不写数据库。
2. 读取 API 配置；没有配置就抛 `ChatNotConfiguredException`，此时**还什么都没保存**。
3. 读取最近 8 条历史消息。
4. 把用户问题写入 `chat_messages`，得到带数据库 ID 的消息对象。
5. 构建本地上下文、调用 API、把回答和引用一起写入数据库。

```kotlin
suspend fun send(
    question: String,
    conversationId: Long = DEFAULT_CONVERSATION_ID,
): ChatMessage {
    val text = question.trim()
    if (text.isEmpty()) throw ChatException("请输入想问的内容")
    val connection = requireConnection()

    val history = chatRepository.recent(conversationId, HISTORY_LIMIT)
    val userMessage = chatRepository.append(
        ChatMessage(
            role = ChatRole.USER,
            content = text,
            createdAt = Instant.now(clock),
            conversationId = conversationId,
        ),
    )
    return replyTo(userMessage, connection, history, conversationId)
}
```

注意 `history` 是在保存新问题之前读取的，所以它只包含之前的对话，不会把刚问的问题重复算进去。

`replyTo()` 负责真正请求和落库：

```kotlin
private suspend fun replyTo(
    question: ChatMessage,
    connection: AiConnectionSettings,
    history: List<ChatMessage>,
): ChatMessage {
    val context = contextBuilder.build(question.content, today)
    val answer = api.reply(
        connection = connection,
        request = ChatRequest(
            question = question.content,
            context = context.text,
            history = history.map { turn ->
                ChatTurn(role = turn.role.apiValue(), content = turn.content)
            },
        ),
    ).trim()
    if (answer.isEmpty()) throw ChatException("AI 没有返回有效内容")

    return chatRepository.append(
        ChatMessage(
            role = ChatRole.ASSISTANT,
            content = answer,
            createdAt = Instant.now(clock),
            referencedDays = context.referencedDays,
            referencedCaptureIds = context.referencedCaptureIds,
        ),
    )
}
```

如果 API 失败，用户问题已经落库，但不会编造一条助手回答。界面会显示错误和重试按钮，用户可以看到自己问过什么。

重试专门处理“最后一条是用户问题”的情况：

```kotlin
suspend fun retry(conversationId: Long = DEFAULT_CONVERSATION_ID): ChatMessage {
    val latest = chatRepository.latest(conversationId)
        ?: throw ChatException("还没有可以重试的问题")
    if (latest.role != ChatRole.USER) throw ChatException("上一条消息已经有回答")
    val connection = requireConnection()
    val history = chatRepository.recent(conversationId, HISTORY_LIMIT + 1).dropLast(1)
    return replyTo(latest, connection, history, conversationId)
}
```

这里读 9 条再丢掉最后一条，是因为“最近消息”里包含待重试的那条用户问题；丢掉它，才能真正得到它之前的 8 条历史。

历史窗口固定为 8：

```kotlin
private companion object {
    const val HISTORY_LIMIT = 8
}
```

它的作用是控制请求大小和成本，不是长期记忆。更早的对话仍然在数据库里，只是不会再发给模型。

建议断点：`ChatService.send()`。依次观察“配置检查 → 历史读取 → 用户消息落库 → 上下文构建 → API 回答落库”。

对应测试在 [ChatServiceTest.kt](../../app/src/test/java/com/example/test0/chat/domain/ChatServiceTest.kt)。

## 4. ChatContextBuilder：最近 7 天、关键词和 2000 字符预算

检索逻辑在 [ChatContextBuilder.kt](../../app/src/main/java/com/example/test0/chat/domain/ChatContextBuilder.kt)。

它返回的不只是文本，还有这次实际用到的日期和记录 ID：

```kotlin
data class ChatContext(
    val text: String = "",
    val referencedDays: List<LocalDate> = emptyList(),
    val referencedCaptureIds: List<Long> = emptyList(),
) {
    val isEmpty: Boolean get() = text.isBlank()
}
```

### 4.1 先限定时间窗口

```kotlin
val days = captureRepository.observeDays()
    .first()
    .filter { !it.isAfter(today) }
    .sortedDescending()
    .take(maxDays)
if (days.isEmpty()) return ChatContext()
```

默认 `maxDays = 7`。未来日期被排除，日期从近到远排序，只取最近 7 个有记录的日期。没有记录时直接返回空上下文，不会把空字符串当成“资料”。

每个日期会读取当天的原始记录和整理结果。整理结果只有在来源指纹与当前原始记录一致时才使用：

```kotlin
val snapshots = days.map { day ->
    val captures = captureRepository.getByDay(day)
    DaySnapshot(
        day = day,
        captures = captures,
        analysis = analysisRepository.observeByDay(day)
            .first()
            ?.takeIf { it.sourceFingerprint == sourceFingerprint(captures) },
    )
}
```

这和阅览页的失效判断是同一条规则。整理之后又新增了随记，旧摘要就属于“过期结论”，不能进入聊天上下文，否则模型会把旧状态当成当前状态。

### 4.2 再把问题拆成关键词

第一版检索不引入分词库，也不做向量检索，只做简单的关键词命中：

```kotlin
internal fun queryTokens(question: String): Set<String> {
    val normalized = question.lowercase()
    val tokens = linkedSetOf<String>()

    Regex("[a-z0-9]{2,}").findAll(normalized).forEach { tokens += it.value }

    val cjk = normalized.filter { it in '\u4e00'..'\u9fff' }
    when {
        cjk.length >= 2 -> cjk.windowed(2).forEach { tokens += it }
        cjk.length == 1 -> tokens += cjk
    }

    return tokens.filterNot { it in STOP_TOKENS }.toSet()
}
```

规则是：

- 英文和数字：连续 2 个字符以上算一个词。
- 中文：连续汉字按二元组切分，例如“我在研究布局”会得到“我在”“在研”“研究”“究布”“布局”。
- 常见虚词（如“什么”“怎么”“我的”）会被过滤，避免所有问题都命中同一批记录。

每条原始记录按命中词数量打分，只保留分数大于 0 的记录，然后按“分数高 → 日期近 → ID 大”排序：

```kotlin
.filter { it.score > 0 }
.sortedWith(
    compareByDescending<RankedCapture> { it.score }
        .thenByDescending { it.day }
        .thenByDescending { it.capture.id },
)
```

### 4.3 摘要优先，原始记录补充，总长度封顶

上下文按两轮填充：

1. 先按日期从近到远放当天摘要。
2. 再按相关度放命中的原始记录。

```kotlin
for (snapshot in snapshots) {
    val summary = snapshot.analysis?.let { extractSummary(it.markdown) } ?: continue
    val block = "【${snapshot.day} 摘要】\n$summary\n"
    if (context.length + block.length > budgetChars) break
    context.append(block)
    usedDays += snapshot.day
}

for (item in ranked) {
    val block = "【${item.day} 记录】${item.capture.text.trim()}\n"
    if (context.length + block.length > budgetChars) break
    context.append(block)
    usedDays += item.day
    usedCaptureIds += item.capture.id
}
```

默认 `budgetChars = 2_000`。这里是“加得下就加，加不下就停”，不会跳过当前块再去找更小的块。实现简单、结果可预测，代价是可能少放一条本可以放下的短记录。

摘要提取只取 `## 当日摘要` 到下一个二级标题之间的内容；找不到标题时，退化为前几行非标题、非引用文字，最多 400 字符。

最后，`usedDays` 去重后按日期倒序返回，`usedCaptureIds` 按实际加入顺序返回。它们只保存在本地，用于气泡下方的引用标签。

第一版检索的边界：

- 没有语义检索，换个说法可能命中不到。
- 没有跨天关联，只按单天记录打分。
- 没有把全部历史发给模型，只发最近 7 天里挑中的内容。
- 上下文为空时仍然会请求 API，但系统提示会明确告诉模型“缺少个人上下文”。

建议断点：`ChatContextBuilder.build()`。分别用“最近在忙什么”“Compose 布局学到哪了”和空资料库观察挑选结果。

对应测试在 [ChatContextBuilderTest.kt](../../app/src/test/java/com/example/test0/chat/domain/ChatContextBuilderTest.kt)。

## 5. chat_messages 表与 RoomChatRepository

数据模型在 [ChatMessageEntity.kt](../../app/src/main/java/com/example/test0/chat/data/ChatMessageEntity.kt)：

```kotlin
@Entity(
    tableName = "chat_messages",
    indices = [Index("conversationId")],
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val role: String,
    val content: String,
    val createdAtEpochMillis: Long,
    val referencedDayKeys: String,
    val referencedCaptureIds: String,
    @ColumnInfo(defaultValue = "1")
    val conversationId: Long = 1,
)
```

几个设计选择：

- `role` 存字符串 `USER` / `ASSISTANT`，读取时再转回枚举；未知值一律按 `USER` 处理。
- 时间存毫秒数，避免 Room 直接处理 `Instant`。
- 引用日期和引用记录 ID 用逗号拼接成字符串。它们数量很小，不值得为第一版再建关联表。
- 引用字段允许空字符串，读取时会过滤空项。

[ChatMessageDao.kt](../../app/src/main/java/com/example/test0/chat/data/ChatMessageDao.kt) 只提供必要查询：

```kotlin
@Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY id ASC")
fun observeAll(conversationId: Long): Flow<List<ChatMessageEntity>>

@Query(
    "SELECT * FROM chat_messages WHERE conversationId = :conversationId " +
        "ORDER BY id DESC LIMIT :limit",
)
suspend fun recent(conversationId: Long, limit: Int): List<ChatMessageEntity>

@Query(
    "SELECT * FROM chat_messages WHERE conversationId = :conversationId " +
        "ORDER BY id DESC LIMIT 1",
)
suspend fun latest(conversationId: Long): ChatMessageEntity?
```

[RoomChatRepository.kt](../../app/src/main/java/com/example/test0/chat/data/RoomChatRepository.kt) 做两件转换：

- 数据库按 `id ASC` 观察，界面直接拿到从旧到新的列表。
- `recent()` 查询按 `id DESC` 取最近的几条，返回前再 `reversed()`，保证发给 API 的历史是从旧到新。

```kotlin
override suspend fun recent(limit: Int): List<ChatMessage> =
    dao.recent(limit).map(ChatMessageEntity::toModel).reversed()
```

`append()` 插入后把自增 ID 复制回模型，顺带刷新会话的 `updatedAt`、并在第一条提问时给会话起名；`clear(conversationId)` 只清当前会话；`delete(id)` 只删一条。它们都只影响聊天表，不会级联删除随记或整理。

消息写入数据库后，`observeAll()` 会通过 Flow 自动通知 ViewModel，页面无需手动刷新。这就是第 1 课那条“Room → Flow → Compose”链路的又一次复用。

接口定义在 [ChatRepository.kt](../../app/src/main/java/com/example/test0/chat/data/ChatRepository.kt)。

## 6. 数据库 2→3 迁移

数据库定义在 [PersonalVaultDatabase.kt](../../app/src/main/java/com/example/test0/journal/data/PersonalVaultDatabase.kt)。

第 2 课结束时版本是 2，包含 `captures` 和 `daily_analyses`。加入聊天后升到版本 3，并注册迁移：

```kotlin
@Database(
    entities = [CaptureEntity::class, DailyAnalysisEntity::class, ChatMessageEntity::class],
    version = 3,
    exportSchema = true,
)
```

迁移内容只有建表：

```kotlin
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS chat_messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                role TEXT NOT NULL,
                content TEXT NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL,
                referencedDayKeys TEXT NOT NULL,
                referencedCaptureIds TEXT NOT NULL
            )
            """.trimIndent(),
        )
    }
}
```

创建数据库时把两个迁移都加上：

```kotlin
.addMigrations(MIGRATION_1_2, MIGRATION_2_3)
```

要点：

- 这是纯新增表，不需要改动或重建旧表，因此不会丢随记。
- 没有使用 `fallbackToDestructiveMigration()`，旧数据不会被整体清空。
- `exportSchema = true`，当前 `app/schemas/` 下已有 `1.json` ~ `6.json`。
- 如果以后再加字段，应继续升版本并写迁移，而不是修改 `MIGRATION_2_3`。当前版本已经是 **v6**，多会话的 5→6 迁移见第 16 节。

建议断点：`PersonalVaultDatabase.create()`。用旧版本数据升级后，确认聊天页可写、阅览页的旧记录仍在。

## 7. 复用 API 客户端与 HTTP 传输

聊天和每日整理共用同一个客户端，接口定义在 [ChatApi.kt](../../app/src/main/java/com/example/test0/ai/api/ChatApi.kt)：

```kotlin
interface ChatApi {
    suspend fun reply(
        connection: AiConnectionSettings,
        request: ChatRequest,
    ): String
}
```

`ChatRequest` 包含三部分：当前问题、本地上下文、历史消息。历史消息用 `ChatTurn(role, content)` 表示。

实现仍然是 [OpenAiCompatibleApiClient.kt](../../app/src/main/java/com/example/test0/ai/api/OpenAiCompatibleApiClient.kt)，它同时实现 `DailyAnalysisApi` 和 `ChatApi`：

```kotlin
class OpenAiCompatibleApiClient(
    private val transport: HttpTransport = UrlConnectionHttpTransport(),
) : DailyAnalysisApi, ChatApi
```

聊天和整理有两处不同：

- 整理要求模型返回固定 JSON，再在本地渲染 Markdown。
- 聊天直接返回纯文本，不做结构解析，也不做流式输出。

系统提示把“本地资料边界”写死：

```kotlin
val hasContext = request.context.isNotBlank()
val contextRule = if (hasContext) {
    "回答只能依据下面的本地资料，可以注明参考的日期。资料里没有的内容就直说没有，不要猜测。\n\n本地资料：\n${request.context}"
} else {
    "本次没有检索到相关的本地资料，请明确告诉用户你缺少他的个人上下文，然后再给一般性的回答。"
}
```

完整提示还要求：用简体中文、语气克制、不做心理诊断、不编造事实、最多 3 段、不要输出 Markdown 代码块。

请求消息的顺序是“系统提示 → 历史 → 当前问题”，和 `ChatService` 传进来的历史顺序一致：

```kotlin
val messages = JSONArray()
    .put(
        JSONObject()
            .put("role", "system")
            .put("content", systemInstruction),
    )
history.forEach { turn ->
    messages.put(
        JSONObject()
            .put("role", turn.role)
            .put("content", turn.content),
    )
}
messages.put(
    JSONObject()
        .put("role", "user")
        .put("content", userContent),
)
```

随后客户端会：

- 校验地址必须是 HTTPS。
- 自动补 `/chat/completions`。
- 添加 `Authorization: Bearer <key>` 和 JSON 头。
- 检查 HTTP 状态码是否在 200–299。
- 读取 `choices[0].message.content`，空内容按错误处理。

真正的网络细节在 [HttpTransport.kt](../../app/src/main/java/com/example/test0/ai/api/HttpTransport.kt)：

```kotlin
private companion object {
    const val SOCKET_TIMEOUT_MILLIS = 15_000
    const val MAX_RESPONSE_BYTES = 1_000_000
    const val TOTAL_TIMEOUT_NANOS = 30_000_000_000L
}
```

它用 `HttpsURLConnection`，连接和读取各 15 秒，总时长 30 秒，响应最大 1 MB；协程取消时会主动断开连接。超时或响应过大有单独提示，其他网络异常统一转成“网络连接失败，请检查网络和 API 地址”。

聊天和整理都通过同一个 `AppContainer` 取得这个客户端：

```kotlin
private val apiClient by lazy { OpenAiCompatibleApiClient() }

val chatApi: ChatApi
    get() = apiClient
```

所以切换 API 供应商时，只需要替换这一个客户端的实现，聊天页和整理服务都不用改。

建议断点：`OpenAiCompatibleApiClient.reply()` 和 `UrlConnectionHttpTransport.post()`。

## 8. 聊天特有的失败语义

聊天比整理多了一些“对话状态”问题，不能只用一句“失败”概括：

| 场景 | 实际行为 | 用户看到什么 |
|---|---|---|
| 问题为空 | `ChatException`，不写数据库 | 输入框下方提示“请输入想问的内容” |
| 没有配置 API | `ChatNotConfiguredException`，用户问题也不保存 | 错误提示，草稿恢复，**不显示重试** |
| HTTP 错误或网络失败 | 用户问题已保存，助手回答不写入 | 错误提示 + “重试” |
| API 返回空内容 | 客户端抛出 `AiApiException`，服务层还有一次空白校验，不写入空回答 | 错误提示 + “重试” |
| 本地没有可用资料 | 仍发请求，上下文为空 | 回答下方显示“未引用本地资料” |
| 用户取消协程 | `CancellationException` 原样抛出 | 不显示为错误 |
| 清空或删除失败 | 捕获异常，保留原对话 | “清空失败/删除失败，请稍后重试” |

两个关键区分：

1. **“未配置”发生在写库之前。** 因此数据库里不会留下一条永远等不到回答的问题，草稿也能完整恢复。
2. **“API 失败”发生在写库之后。** 因此问题会保留，重试只补回答，不会重复保存问题。

`ChatNotConfiguredException` 继承自 `ChatException`，专门给 ViewModel 判断“是否需要显示重试”：

```kotlin
open class ChatException(message: String) : Exception(message)

class ChatNotConfiguredException(message: String) : ChatException(message)
```

另外，助手回答在落库前会先 `trim()`；只有空白字符的回答不会进入数据库。这避免了界面出现一个看起来“成功”的空气泡。

## 9. 对应测试

聊天相关测试分布在两个源码集：

| 文件 | 证明什么 |
|---|---|
| [ChatServiceTest.kt](../../app/src/test/java/com/example/test0/chat/domain/ChatServiceTest.kt) | 保存问答与引用；上下文包含本地记录；未配置时什么都不写；API 失败保留问题；重试不重复问题；历史顺序正确；历史与回答按会话隔离 |
| [ChatContextBuilderTest.kt](../../app/src/test/java/com/example/test0/chat/domain/ChatContextBuilderTest.kt) | 摘要与相关记录进入上下文；过期整理被排除；预算生效；空资料库返回空上下文；未来日期被忽略 |
| [ChatViewModelTest.kt](../../app/src/test/java/com/example/test0/chat/ui/ChatViewModelTest.kt) | 发送后清空草稿；清空当前会话不影响其他会话；切换 / 新建 / 删除会话；第一条提问给会话起名；删除单条消息保留其余 |
| [ChatScreenTest.kt](../../app/src/androidTest/java/com/example/test0/chat/ui/ChatScreenTest.kt) | 清空需确认；长按删除需确认；空对话没有清空入口；会话卡片可切换 / 新建；删除会话需确认 |
| [ChatConversationRepositoryTest.kt](../../app/src/androidTest/java/com/example/test0/chat/data/ChatConversationRepositoryTest.kt) | 消息按会话隔离；清空只影响一个会话；自动命名；删除会话连带删消息 |
| [DatabaseMigration5To6Test.kt](../../app/src/androidTest/java/com/example/test0/analysis/data/DatabaseMigration5To6Test.kt) | 旧消息归入默认会话、新列与索引存在 |

测试替身集中在 [ChatTestDoubles.kt](../../app/src/test/java/com/example/test0/chat/domain/ChatTestDoubles.kt)，包括内存版聊天仓库、假 API、假设置存储和假随记/整理仓库。

运行方式：

```powershell
.\gradlew.bat test
.\gradlew.bat connectedDebugAndroidTest
```

建议先读 `ChatServiceTest` 的用例，再读 `ChatContextBuilderTest`。前者证明“对话状态机”，后者证明“资料筛选规则”；多会话部分从 `ChatConversationRepositoryTest` 和 `DatabaseMigration5To6Test` 入手最直观。

## 10. 小结与练习

到这里，三条链路可以串起来看：

1. **记录链路**：`JournalScreen → JournalViewModel → CaptureRepository → Room`。
2. **整理链路**：`DailyAnalysisService → OpenAiCompatibleApiClient → Markdown 文件 + daily_analyses`。
3. **聊天链路**：`ChatScreen → ChatViewModel → ChatService → ChatContextBuilder → ChatApi → chat_messages`。

聊天并没有发明新的数据来源。它读取前两条链路已经保存的随记和整理，在本地挑选有限内容，再把回答和“用了哪些日期”一起写回数据库。这样既控制了请求大小，也让引用可以被用户核实。

推荐阅读顺序：

1. [ChatScreen.kt](../../app/src/main/java/com/example/test0/chat/ui/ChatScreen.kt)：先看界面状态和引用标签。
2. [ChatViewModel.kt](../../app/src/main/java/com/example/test0/chat/ui/ChatViewModel.kt)：看草稿、发送、重试和清空。
3. [ChatService.kt](../../app/src/main/java/com/example/test0/chat/domain/ChatService.kt)：看一轮问答的完整顺序。
4. [ChatContextBuilder.kt](../../app/src/main/java/com/example/test0/chat/domain/ChatContextBuilder.kt)：看 7 天窗口、关键词打分和预算。
5. [ChatMessageEntity.kt](../../app/src/main/java/com/example/test0/chat/data/ChatMessageEntity.kt) → [ChatMessageDao.kt](../../app/src/main/java/com/example/test0/chat/data/ChatMessageDao.kt) → [RoomChatRepository.kt](../../app/src/main/java/com/example/test0/chat/data/RoomChatRepository.kt)：看聊天如何持久化。
6. [PersonalVaultDatabase.kt](../../app/src/main/java/com/example/test0/journal/data/PersonalVaultDatabase.kt)：看 2→3 迁移。
7. [OpenAiCompatibleApiClient.kt](../../app/src/main/java/com/example/test0/ai/api/OpenAiCompatibleApiClient.kt) 和 [HttpTransport.kt](../../app/src/main/java/com/example/test0/ai/api/HttpTransport.kt)：看请求格式和网络边界。
8. 最后回到测试，验证你已经理解的规则。

练习：

1. 把 `ChatContextBuilder` 的 `maxDays` 改成 3，用同一个问题对比上下文和引用日期的变化。
2. 把 `budgetChars` 改成 500，观察哪些记录被截掉，并补一个断言“上下文长度不超过预算”的测试。
3. 给 `ChatService` 补一个测试：先写入 9 条历史，再发送新问题，确认发给 API 的仍然只有 8 条。
4. 如果要在气泡上显示“引用了 2 条记录”，需要从 `referencedCaptureIds` 出发做哪些查询？先画出数据流，再决定是否值得为它增加界面。

三课读完后，当前实现的三个主页面 + 设置弹层、三张表、两条 AI 调用链就已经全部覆盖。

## 11. 补充：人格预设与"发出即只读"

### 人格不是模型自带的

聊天回答显得生硬，通常不是模型能力问题，而是 `system` 提示词写得太像规则清单。[OpenAiCompatibleApiClient.kt](../../app/src/main/java/com/example/test0/ai/api/OpenAiCompatibleApiClient.kt) 的 `reply()` 现在给助手一个固定身份「小札」，并明确要求：像朋友说话、不用报告腔、不主动说教、不写鸡汤结尾。

在它之上，设置里可以选三种预设，定义在 [AiSettings.kt](../../app/src/main/java/com/example/test0/ai/settings/AiSettings.kt)：

| 预设 | 说话方式 |
|---|---|
| 温暖陪伴 | 先接住情绪，再谈记录里的事实，语气温和不急 |
| 简洁清晰 | 先给结论再给依据，短句、少形容词 |
| 轻快活泼 | 有一点幽默感，用短句和轻松比喻 |

`SettingsViewModel` 把它写进 `AiSettingsInput`，`KeystoreAiSettingsStore` 存在普通偏好里（和加密密钥分开），`ChatService.replyTo()` 读出来放进 `ChatRequest.persona`，最后由客户端拼进提示词。**预设只改变措辞，不改变 7 天窗口、关键词打分、2000 字预算和引用记录**，所以换语气不会让引用变得不可信。

### 哪些内容发出后不能改

| 内容 | 能不能改 | 能做什么 |
|---|---|---|
| 原始随记 | **默认不能改** | 删除；在设置里打开「允许修改已保存的随记」后才出现编辑按钮，改完会让当天整理失效 |
| 每日整理 | 不能改 | 点开只读详情、单独删除、重新整理 |
| 聊天消息 | 不能改 | 长按删除单条、清空对话 |

「允许修改」和外观皮肤都是**即时生效**的偏好，存在 `AiSettingsStore`，不需要先配置 API、也不用点「保存设置」。`JournalUiState.canEdit` 由设置流组合出来，界面据此决定要不要画铅笔图标；`JournalViewModel.editCapture()` 自己也做了一次守卫，避免只靠 UI 隐藏。皮肤则由 `MainActivity` 收集设置后传给 `Test0Theme(skin = ...)`，四套配色定义在 [Theme.kt](../../app/src/main/java/com/example/test0/ui/theme/Theme.kt)：暖纸 / 薄荷 / 藕粉 / 夜色。

原因：整理结果和回答都是 AI 生成的衍生内容。允许手改会让「来源指纹」和「引用了哪些记录」失去意义，也会让用户误以为那是自己写下的原始事实。删除后再重新生成，才是保持链路可解释的做法。

相关实现：[ReaderViewModel.deleteAnalysis()](../../app/src/main/java/com/example/test0/reader/ReaderViewModel.kt) 只调 `analysisRepository.deleteByDay()`，`captures` 表完全不动；[ChatViewModel](../../app/src/main/java/com/example/test0/chat/ui/ChatViewModel.kt) 只有 `deleteMessage()` 和 `clearConversation()`，没有编辑入口。

## 12. 补充：图片、语音与 Room v4

[第 1 课](01-local-text-journal.md) 的 `captures` 表原本只有文字。现在一条记录可以是三种类型，定义在 [Capture.kt](../../app/src/main/java/com/example/test0/journal/model/Capture.kt)：

| 类型 | 正文字段 | 媒体 |
|---|---|---|
| `TEXT` | 用户输入的文字 | 无 |
| `IMAGE` | 可选说明 | 私有目录下的图片绝对路径 |
| `AUDIO` | 可选说明 | 私有目录下的音频绝对路径 |

数据库从 v3 升到 **v4** 只加了一列，[MIGRATION_3_4](../../app/src/main/java/com/example/test0/journal/data/PersonalVaultDatabase.kt) 是：

```kotlin
db.execSQL("ALTER TABLE captures ADD COLUMN mediaPath TEXT")
```

旧文字记录的 `mediaPath` 为 `NULL`，不会丢数据。系统选择器返回的 `Uri` 由 [LocalMediaStore](../../app/src/main/java/com/example/test0/journal/data/MediaStore.kt) 复制进 `filesDir/media/`，这样即使以后外部文件被删掉，笔记里的图片和录音还在。删除记录时先取路径删文件、再删数据库行，避免留下孤儿文件。

语音是应用内 `MediaRecorder` 录的（需要 `RECORD_AUDIO` 权限，拒绝授权则退回选择音频文件）；图片用系统文件选择器选。两者走的是**同一条** `ViewModel.importMedia() → MediaStore.import() → CaptureRepository.addMedia()` 链路。

媒体暂时没有正文，所以给模型的是占位而不是空字符串：[DailyAnalysisService.analysisEntry()](../../app/src/main/java/com/example/test0/analysis/domain/DailyAnalysisService.kt) 和 [ChatContextBuilder.contextEntry()](../../app/src/main/java/com/example/test0/chat/domain/ChatContextBuilder.kt) 会写成 `[图片记录]` / `[语音记录]`。`sourceFingerprint` 只对非文字记录追加类型和路径，所以**旧整理的指纹保持不变**，不会因为这次升级就全部误判为「需重新整理」。

把媒体转成文字（图片描述、语音转写）是里程碑 4 剩下的部分，做完之后整理与聊天就能真正读到这些内容。

## 13. 补充：把图片和语音变成文字

### 两个不同的 API 端点

| 用途 | 端点 | 请求格式 | 模型 |
|---|---|---|---|
| 聊天 / 每日整理 | `/chat/completions` | JSON | 设置里的「模型名称」 |
| 图片描述 | `/chat/completions` | JSON，`content` 数组里放 `image_url` + base64 | 设置里的「图片理解模型」，留空则复用聊天模型（**必须支持视觉输入**） |
| 语音转写 | `/audio/transcriptions` | `multipart/form-data` 上传音频文件 | 设置里的「语音转写模型」，默认 `whisper-1` |

图片和聊天共用同一个端点，只是把纯文本 `content` 换成数组：

```json
{"type": "image_url", "image_url": {"url": "data:image/jpeg;base64,<...>"}}
```

语音转写是**另一个端点、另一类模型、另一份计费**，不是聊天 API 的一个参数。这也是为什么设置里给它单独的模型名。少数多模态模型支持音频输入，但兼容性差且贵，所以用标准转写端点更稳。

[HttpTransport.kt](../../app/src/main/java/com/example/test0/ai/api/HttpTransport.kt) 的 `postMultipart()` 手写 multipart 边界，塞进 `HttpRequest.bodyBytes`，复用同一个 `post()` 连接与超时逻辑，没有引入新的网络库。

### 结果怎么回到检索里

[MediaUnderstandingService](../../app/src/main/java/com/example/test0/media/MediaUnderstandingService.kt) 把得到的文字加上 `[图片描述] `/`[语音转写] ` 前缀，写回该条记录的 `textContent`（`updateText` 会刷新 `updatedAt`，于是当天整理的 `sourceFingerprint` 变化、旧整理自动变成「需重新整理」）。之后 `DailyAnalysisService.analysisEntry()` 和 `ChatContextBuilder.contextEntry()` 直接读这段文字，整理与聊天就真的能用到媒体内容了。

图片发送前会缩到最长边 1024 并压成 JPEG，避免 base64 把请求体撑到几 MB。触发入口在随记详情卡片的「识别图片内容」/「转写成文字」按钮——**只有你主动点才会发送**，符合隐私约定。

## 14. 补充：按日期导出与迁移

[BackupRepository](../../app/src/main/java/com/example/test0/backup/BackupRepository.kt) 用 `java.util.zip` 生成一个 zip，不依赖第三方库。**每天一个文件夹，内容是给人读的 Markdown**：

```text
README.md                      格式版本、导出时间、目录说明
2026-11-07/
  notes.md                     当天全部随记，按时间排列，长文本原样保留
  analysis.md                  AI 生成的 Markdown（如果有）
  media/                       图片、语音原文件
2026-11-08/
  notes.md
  ...
```

文件名用 ASCII，避免 Windows / macOS / 不同解压工具对中文 zip 条目名的编码差异导致乱码。

`notes.md` 长这样（用任何笔记软件打开都能直接读）：

```markdown
# 2026-11-07 随记

共 2 条。

<!-- vault-capture type=TEXT at=1794063656766 imported=... updated=... media= -->

## 10:57 · 文字

今天想写一段比较长的内容，
可以有自己的小标题、列表和链接。

## 这是笔记自己的标题

正文里的 Markdown 原样保留。
```

几个关键决定：

- **正文用 Markdown，不用 JSON**。JSON 把长文本压成一行转义字符串，既不好读也不适合长笔记；Markdown 可以有多段、标题、列表，和文档里的写法一致。
- **元数据藏在 HTML 注释里**。`<!-- vault-capture ... -->` 在 Markdown 渲染时完全看不见，但导入时能无损还原时间、类型、媒体路径。正文里恰好以 `<!-- vault-` 开头的行会加零宽空格转义，避免被误判成下一条记录。
- **只精确跳过生成的结构行**。解析时只移除导出时写的 `## HH:mm · 类型` 标题和紧跟的媒体引用，用户正文里的 `## ` 标题、`[链接](...)` 一律保留——否则长笔记会被悄悄改坏。
- **不写绝对路径**。媒体只用 zip 内的相对名，导入时用 `MediaStore.writeMedia()` 重新落到新设备的私有目录，换设备不会失效。
- **按日期合并，不做行级去重**。导入时若某天本地已有随记，就整日跳过并报告，避免重复导入产生两份一样的记录；代价是同一天的新记录不会被增量合并。
- **兼容旧格式**。检测到 `manifest.json` 就走旧的 JSON 解析（v1），检测到 `README.md` 走 Markdown 解析（v2）；换格式不会让老备份作废。
- **整体读入内存但有上限**（100 MB），防止恶意 zip 撑爆内存。

入口在右上角，和设置并排的小图标；点开后可以勾选要导出的日期，默认全选。[BackupSheet](../../app/src/main/java/com/example/test0/backup/BackupSheet.kt) 用 `ActivityResultContracts.CreateDocument` / `OpenDocument` 走系统文件选择器。导入导出都在 `Dispatchers.IO` 上做，不卡界面。

对应测试：[BackupRepositoryTest](../../app/src/androidTest/java/com/example/test0/backup/BackupRepositoryTest.kt) 覆盖三件事：新格式导出到 zip 再导入另一个全新的内存数据库（含长文本、正文自带 `## ` 标题与链接、媒体、AI 整理、标签）、二次导入同日跳过、以及**旧 JSON 备份仍能导入**。

## 15. 补充：单一输入框、整页阅览与标签

### 只有一个输入框

曾经试过「短笔记框 + 一键放大成整页长笔记编辑器」两种框，实际用下来反而累赘，所以又收回到**一个框**：记录页的 `ComposerCard` 就是列表内一个可多行的 `OutlinedTextField`，长短内容都在这里写，没有「短笔记」标签、没有放大图标、也没有 `LongNoteEditor`。右下角的悬浮 + 按钮现在只是滚回列表顶部并聚焦这个输入框：

```kotlin
LaunchedEffect(focusInput) {
    if (focusInput) {
        listState.animateScrollToItem(0)
        inputFocus.requestFocus()
        focusInput = false
    }
}
```

历史的坑有两点，值得记住：

- 旧的长笔记把标题写成正文首行的 `# 标题`。去掉这套编辑器后，不再产生这种前缀；但**已有的旧数据**里还有，所以卡片和阅览列表统一用 [Capture.displayText](../../app/src/main/java/com/example/test0/journal/model/Capture.kt) 渲染，去掉开头的 `# `，界面上不再露出多余的 `#`。注意只在展示层处理，数据库与备份 zip 里仍保留原始正文。
- Manifest 里已经有 `adjustResize`，列表就**不要**再加 `imePadding()`，否则键盘弹出时两份 inset 叠加，输入框会被裁掉半截、下面留出一大片空白。

### 点开就是整页笔记

详情不再是「底部弹层 + 放大字号按钮」——长文在弹层里被裁一截、还得手动点放大。现在 [CaptureDetailSheet](../../app/src/main/java/com/example/test0/journal/ui/CaptureDetailSheet.kt) 是一个 `usePlatformDefaultWidth = false` 的 `Dialog`，`fillMaxSize()` 铺满整屏：顶部返回 + 复制，正文 `verticalScroll` 完整可读，下面依次是标签、识别、编辑/删除。整个界面就是这条笔记，不需要任何放大操作。

### 图片 / 语音各自的连接

媒体识别以前和聊天共用同一个 `connection()`，只能单独覆盖模型名。设置里现在给图片、语音各留了一组「接口地址 / 模型 / API 密钥」，三把密钥用 Keystore 分别加密：

- 图片有「与聊天设置相同」开关（默认开）：打开时复用聊天接口的地址与密钥，只可单独指定视觉模型；关掉才需要填独立三项。
- 语音没有单独配地址或密钥时，自动沿用聊天连接、只换转写模型（默认 `whisper-1`）。
- [KeystoreAiSettingsStore](../../app/src/main/java/com/example/test0/ai/settings/KeystoreAiSettingsStore.kt) 的 `visionConnection()` / `transcriptionConnection()` 负责解析；[MediaUnderstandingService](../../app/src/main/java/com/example/test0/media/MediaUnderstandingService.kt) 按 `CaptureType` 取对应连接，连模型也直接用 `connection.model`。接口里这两个方法给了默认实现（回退到 `connection()`），所以测试替身不用跟着改。

> `AiSettingsStore` 新加方法时给默认实现，是个省事的做法：真实存储覆盖，众多内存 fake 保持原样。

### 标签

标签挂在 `captures.tags`（Room 从 v4 升到 **v5**）：

```kotlin
ALTER TABLE captures ADD COLUMN tags TEXT NOT NULL DEFAULT ''
```

数据库里用逗号分隔的字符串存，[CaptureEntity](../../app/src/main/java/com/example/test0/journal/data/CaptureEntity.kt) 的 `encodeTags` / `decodeTags` 负责转换；标签本身不允许逗号。因为标签是加在 `Capture` 上的，**文字、图片、语音三类记录都能打**， detail 卡片里用 [TagSection](../../app/src/main/java/com/example/test0/journal/ui/TagSection.kt) 增删，另有「工作/生活/灵感/待办」四个预置标签可一键添加；卡片上显示前两个。

两个设计决定：

- `updateTags()` **不改正文也不改 `updatedAt`**，所以打标签不会让当天的 AI 整理变成「需重新整理」。
- 备份里标签写成可读的 `标签：#工作 #灵感` 一行，同时塞进 HTML 注释的 `tags=` 字段；导入时只精确跳过导出生成的这一行，不会误删用户正文里以「标签：」开头的内容。旧 JSON 备份没有标签，导入后为空列表。

## 16. 补充：聊天多会话与 Room v6

早期聊天只有一条连续的对话，`chat_messages` 里所有消息混在一起，「清空」就是全清。多会话把每条消息归属到一个会话，让聊天页可以开新窗口、分开保存。

### 数据：一张新表 + 消息加一列

`conversations` 表（[ConversationEntity](../../app/src/main/java/com/example/test0/chat/data/ConversationEntity.kt)）只有四个字段：自增 id、标题、创建时间、最后活动时间。`chat_messages` 增加 `conversationId`，并建索引：

```kotlin
@Entity(
    tableName = "chat_messages",
    indices = [Index("conversationId")],
)
data class ChatMessageEntity(
    // ...原有字段
    @ColumnInfo(defaultValue = "1")
    val conversationId: Long = 1,
)
```

`@ColumnInfo(defaultValue = "1")` 不是可省的细节：SQLite 用 `ALTER TABLE ... ADD COLUMN` 给已有数据的表加 `NOT NULL` 列时**必须**带默认值，否则旧行无法满足约束。Room 打开数据库时会核对表结构里列的默认值，实体不写同样的默认值就会被判定「迁移没有正确处理」。

[ConversationDao](../../app/src/main/java/com/example/test0/chat/data/ConversationDao.kt) 按 `updatedAtEpochMillis DESC` 观察会话列表，`touch()` 在每次写入消息时刷新它，所以最近聊过的排在最前面。

### 迁移 5→6：旧消息一条不丢

[MIGRATION_5_6](../../app/src/main/java/com/example/test0/journal/data/PersonalVaultDatabase.kt) 做四件事：

```sql
CREATE TABLE IF NOT EXISTS conversations (...)
INSERT INTO conversations (id, title, createdAtEpochMillis, updatedAtEpochMillis)
    SELECT 1, '默认对话',
           COALESCE(MIN(createdAtEpochMillis), 0),
           COALESCE(MAX(createdAtEpochMillis), 0)
    FROM chat_messages
ALTER TABLE chat_messages ADD COLUMN conversationId INTEGER NOT NULL DEFAULT 1
CREATE INDEX IF NOT EXISTS index_chat_messages_conversationId ON chat_messages (conversationId)
```

旧版本只有一条对话，所以全部归到 id=1 的「默认对话」；聚合查询在没有消息时也会返回一行（时间取 0）。全新安装走另一条路：`PersonalVaultDatabase.create()` 注册了 `RoomDatabase.Callback.onCreate`，建表后插入同一个默认会话。两条路都保证应用打开时至少有一个可写入的会话。

> 迁移测试 [DatabaseMigration5To6Test](../../app/src/androidTest/java/com/example/test0/analysis/data/DatabaseMigration5To6Test.kt) 直接构造 v5 老库、插入一条旧消息，升级后验证会话标题、`conversationId = 1` 和索引。因为它走的是真实升级路径，比只看代码可靠。

### 界面：标题右侧的 chip + 底部卡片

聊天页标题右侧是一个 `Surface` 做的 chip，显示**当前会话标题**；点开是和设置 / 备份同款的自绘底部卡片（`Dialog` + 圆角 `Surface`），里面列出所有会话，当前项高亮，右上角「新建对话」，每项右侧删除图标（删除要二次确认）。

`ChatScreen` 本身不碰数据库：切换 / 新建 / 删除都只是回调，状态从 `ChatUiState` 来。会话列表弹层也是纯展示，所以它可以直接用固定数据做界面测试。

标题和 chip 放在同一 `Row`，副标题单独占下一行——否则 chip 会把副标题挤成两行，读起来很碎。

### 状态：当前会话也进 SavedStateHandle

`ChatViewModel` 用 `savedStateHandle.getStateFlow(KEY_CONVERSATION_ID, DEFAULT_CONVERSATION_ID)` 记住当前会话，进程重建后仍能回到原来的会话。消息流用 `flatMapLatest` 跟着它切换：

```kotlin
private val messages = currentConversationId.flatMapLatest { id ->
    chatRepository.observeMessages(id)
        .catch { throwable ->
            if (throwable is CancellationException) throw throwable
            emit(emptyList())
        }
}
```

`flatMapLatest` 的语义是「新值到来就取消上一个流的收集」，正好对应「切换会话就别再收旧会话的消息」。初始化时还会校验这个 id 对应的会话是否已被删除，被删了就回退到最新一个；删光所有会话时 `deleteConversation()` 会立刻再建一个，保证永远不会没有当前会话。

`uiState` 现在由 7 个源组合，而 `combine` 最多接 5 个 flow，所以先把「消息 + 会话列表 + 当前会话 id」合成一个 `Core`，再和草稿、发送中、错误、可重试组合。这是 `combine` 参数不够用时的常规拆法。

### 隔离边界：什么跟着会话走，什么不跟着

| 行为 | 是否按会话隔离 |
|---|---|
| 消息列表、最近 8 条历史、重试 | 是，`WHERE conversationId = :id` |
| 写用户消息、写 AI 回答 | 是，两条都带 `conversationId` |
| 清空对话 | 是，只删当前会话的消息 |
| 删除单个会话 | 连带删除它的消息 |
| 随记检索（`ChatContextBuilder`） | **否**，仍从全部随记里挑，和哪个会话无关 |
| AI 人格、API 配置 | 否，全局设置 |

一句话：**会话只切「聊天记录」这一层，不切「个人资料库」这一层**。所以新开一个会话问同一个问题，本地资料还是那些，但不会带上一轮对话的历史。

发送中禁止切换 / 新建 / 删除会话（`isSending` 守卫），否则一个正在进行的请求可能把回答写进另一个会话。

### 会话名怎么来

- 种子 / 迁移出来的会话叫「默认对话」。
- 新建的空会话叫「新对话 N」（N 取当前会话数 + 1），列表里不会挤着好几个同名的空会话。
- 第一条用户提问写入时，[RoomChatRepository.append()](../../app/src/main/java/com/example/test0/chat/data/RoomChatRepository.kt) 发现标题还是占位名，就用 `conversationTitleFrom()` 取前 14 个字替换，`touch()` 同时刷新排序。
- `isPlaceholderConversationTitle()` 统一判断「是不是还没起名的占位标题」，避免命名规则散落在各处。

### 还没做的

第一版刻意没做：重命名会话、会话内搜索、把会话和聊天记录打进备份 zip（目前备份只包含随记、整理和媒体）。这些都在 [DEV_ROADMAP.md](../DEV_ROADMAP.md) 的待办里。

