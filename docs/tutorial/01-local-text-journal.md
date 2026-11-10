# 第 1 课：可选择日期的本地文字随记

这一阶段做成了一条完整、但足够小的 Android 数据链路：

```text
用户输入
  ↓
Compose 页面
  ↓
JournalViewModel
  ↓
CaptureRepository
  ↓
Room 数据库
  ↓
Flow 自动通知
  ↓
Compose 页面重新显示
```

理解这条链路后，第 2 课的 AI 整理、第 3 课的聊天，以及将来的图片/录音，都只是在链路中增加新的数据类型和处理步骤；本课先只看本地文字。

## 1. Activity 是应用进入页面的门

入口文件是 [MainActivity.kt](../../app/src/main/java/com/example/test0/MainActivity.kt)。

Android 启动应用时，先创建 `MainActivity`。它现在做四件事：

1. 从 `PersonalVaultApplication` 取得全局的依赖容器 `AppContainer`。
2. 创建四个 ViewModel：记录、阅览、聊天、设置。
3. 调用 `setContent`。
4. 把四个 ViewModel 交给根页面 `PersonalVaultApp`，由底部导航切换页面。

本课只跟踪其中的 `JournalViewModel`：

```kotlin
private val journalViewModel: JournalViewModel by viewModels {
    viewModelFactory {
        initializer {
            JournalViewModel(
                repository = container.captureRepository,
                analysisRepository = container.dailyAnalysisRepository,
                savedStateHandle = createSavedStateHandle(),
                analysisRunner = container.dailyAnalysisService,
            )
        }
    }
}
```

另外三个 ViewModel 的创建方式相同，只是依赖不同；它们会在第 2、3 课展开。

Activity 不负责保存随记，也不直接写数据库。它只负责把应用环境、ViewModel 和界面连接起来。

建议断点：`MainActivity.onCreate()`。重新运行应用时，可以看到 Android 从这里进入，并在 `setContent` 处进入 Compose。

## 2. Composable 描述“当前状态下页面长什么样”

页面在 [JournalScreen.kt](../../app/src/main/java/com/example/test0/journal/ui/JournalScreen.kt)。

`JournalScreen` 接收一个 `JournalUiState` 和一组回调。状态里现在有：

- `selectedDate`：当前选中的日期；
- `draft`：输入框文字；
- `captures`：当天已经保存的随记；
- `isLoading` / `isSaving`：读取或保存是否进行中；
- `validationMessage` / `readErrorMessage`：校验或读取失败提示；
- `isAnalyzing` / `analysisMessage`：AI 整理是否进行中及结果提示。

回调包括：

- 输入文字 → `onDraftChanged`
- 选择日期 → `onDateSelected`
- 点击保存 → `onSave`
- 读取失败后重试 → `onRetry`
- 点击“整理这一天” → `onAnalyze`
- 长按删除一条随记 → `onDeleteCapture`

`onAnalyze` 和 `onDeleteCapture` 属于后续加入的整理/删除能力，本课先不展开；但它们已经存在于当前页面，读代码时会看到。

它是“无状态页面”：页面不决定数据怎么保存，只根据 `JournalUiState` 绘制，并把用户操作向上报告。

这样做的好处是页面可以独立预览、独立测试，也不会把数据库代码塞进 UI。

明暗预览在 [JournalPreview.kt](../../app/src/main/java/com/example/test0/journal/ui/JournalPreview.kt)。

建议断点：`JournalScreen()`。修改输入文字时，观察 Compose 如何根据新状态重新执行相关部分。

## 3. ViewModel 保存页面状态和业务规则

状态逻辑在 [JournalViewModel.kt](../../app/src/main/java/com/example/test0/journal/ui/JournalViewModel.kt)。

为什么不把输入框状态全放在 Activity？

- 屏幕旋转时 Activity 可能重建，ViewModel 可以继续存在。
- 页面只负责展示，规则集中在一个地方更容易测试。
- 保存失败时，ViewModel 能保留用户尚未保存的文字。
- 数据库读取失败时，ViewModel 会显示错误并允许用户重试。
- “不能选择未来日期”“空内容不能保存”等规则不会依赖某个按钮是否正确禁用。
- 点击“整理这一天”时，`analyzeSelectedDay()` 会先检查是否已有整理在跑，再调用 `DailyAnalysisRunner`；整理失败只显示提示，不影响本地随记。

`JournalViewModel` 使用多个 `MutableStateFlow` 保存内部状态（包括草稿、保存中、整理中、各类错误消息），再组合成一个只读的 `uiState`。界面通过 `collectAsStateWithLifecycle()` 观察它。

选中的日期和尚未保存的草稿还会写入 `SavedStateHandle`。因此 Activity 普通重建，甚至系统回收进程后重新创建页面时，这两项临时状态仍能恢复。

建议断点：

- `onDateSelected()`：查看未来日期如何被拒绝。
- `save()`：查看文本校验、Repository 调用和成功后清空输入框。
- `analyzeSelectedDay()`：查看 AI 整理中状态和失败提示如何进入 `uiState`。
- `deleteCapture()`：注意它先删当天整理、再删原始记录，避免留下包含已删内容的衍生文件。
- `catch` 分支：理解为什么失败时不能清掉用户的草稿。

对应测试在 [JournalViewModelTest.kt](../../app/src/test/java/com/example/test0/journal/ui/JournalViewModelTest.kt)。

## 4. Repository 隔离“我要保存”与“具体怎么保存”

接口和实现分别位于：

- [CaptureRepository.kt](../../app/src/main/java/com/example/test0/journal/data/CaptureRepository.kt)
- [RoomCaptureRepository.kt](../../app/src/main/java/com/example/test0/journal/data/RoomCaptureRepository.kt)

ViewModel 只知道：

- `observeByDay(date)`：观察某一天的数据；
- `observeDays()`：观察所有有记录的日期，阅览页和聊天检索会用到；
- `getByDay(date)`：一次性读取某一天的数据，聊天构建上下文时使用；
- `addText(text, contentDate, now)`：保存一条文字；
- `delete(id)`：删除一条记录。

它不知道 SQL、表名或 Room 注解。接口里的 `observeDays()`、`getByDay()` 带默认实现，所以简单的测试替身可以只实现真正需要的方法。

以后如果增加文件导出、测试用内存仓库，甚至云同步，ViewModel 的页面规则不需要跟着全部重写。

建议断点：`RoomCaptureRepository.addText()`。观察输入如何被转换成 `CaptureEntity`。

## 5. 内容日期和导入时间不是一回事

这是本 App 很重要的一条规则。

假设 11 月 4 日把 10 月 31 日的旧随记放进 App：

- `contentAt`：内容属于 10 月 31 日；
- `dayKey`：`2026-10-31`，决定它显示在哪一天；
- `importedAt`：实际在 11 月 4 日加入 App；
- `updatedAt`：最近修改时间，目前新增时等于导入时间。

每日整理和聊天检索按内容日期归档，审计“什么时候导入”时使用导入时间。这样旧内容不会错误地混进今天的日记。

数据模型在：

- [Capture.kt](../../app/src/main/java/com/example/test0/journal/model/Capture.kt)
- [CaptureEntity.kt](../../app/src/main/java/com/example/test0/journal/data/CaptureEntity.kt)

## 6. Room 为什么能在重启后保留内容

Room 相关文件：

- [CaptureEntity.kt](../../app/src/main/java/com/example/test0/journal/data/CaptureEntity.kt)：定义 `captures` 表的一行。
- [CaptureDao.kt](../../app/src/main/java/com/example/test0/journal/data/CaptureDao.kt)：定义插入和查询 SQL。
- [PersonalVaultDatabase.kt](../../app/src/main/java/com/example/test0/journal/data/PersonalVaultDatabase.kt)：当前是 **version 3**，同时声明 `captures`、`daily_analyses`、`chat_messages` 三张表，并注册 1→2、2→3 两个迁移。
- [AppContainer.kt](../../app/src/main/java/com/example/test0/AppContainer.kt)：保证应用使用同一个数据库实例。

本课只关心 `captures`。`daily_analyses` 在第 2 课加入，`chat_messages` 在第 3 课加入；二者都在同一个数据库里，通过显式迁移新增，不会删除已有随记。

数据写进 SQLite 文件后，不依赖 Activity 或进程内存，所以杀掉并重新启动 App 仍然存在。

因为这是本地私密资料，Manifest 已关闭 Android 自动备份，备份规则也明确排除了数据库、文件和偏好设置。后续如果要跨设备迁移，应实现由用户主动触发、可以看见并确认的导出功能。

Room 的 schema 位于 `app/schemas/`，当前可以看到 `1.json`、`2.json`、`3.json`。以后修改表结构时，需要继续增加数据库版本并编写迁移，不能直接删除用户资料。

建议断点：

- `CaptureDao.insert()` 的调用位置；
- `PersonalVaultDatabase.create()`，查看数据库何时第一次创建。

数据库测试在 `app/src/androidTest/java/com/example/test0/journal/data/`。

## 7. Flow 为什么能自动刷新列表

`CaptureDao.observeByDay()` 返回 `Flow<List<CaptureEntity>>`。

保存新记录后，Room 知道 `captures` 表发生变化，于是自动发出一份新的列表。数据经过 Repository 转成 `Capture`，再进入 ViewModel 的 `uiState`。Compose 收到新状态后重新绘制时间线。

因此保存成功后不需要手动写“刷新列表”按钮。

切换日期时，ViewModel 使用 `flatMapLatest`：

- 停止观察旧日期；
- 开始观察新日期；
- 新日期的数据变化会继续自动更新。

## 8. 第一阶段测试保护了什么

- 空白内容不能保存。
- 未来日期不能选择。
- 补录内容必须进入用户选择的日期。
- 相同时间的记录顺序仍然稳定。
- 数据库关闭并重新打开后记录仍存在。
- 保存失败时草稿不会丢失。
- 数据库读取失败时会显示错误，点击“重试”能够恢复。
- 切换日期时不会短暂把前一天的内容显示在新日期下面。
- 选中日期和草稿能通过 `SavedStateHandle` 恢复。
- Compose 页面能显示日期、输入框、按钮和时间线。
- 私密随记不会进入 Android 自动备份。
- API 37 上的测试依赖已升级到兼容版本。

## 9. 一个小练习

先做一个不影响数据结构的小练习：

1. 在 [strings.xml](../../app/src/main/res/values/strings.xml) 中，把“这一天还没有记录”改成你喜欢的文字。
2. 在 `JournalScreen` 输入框下方增加字符计数，例如“12 / 500”。
3. 给字符计数写一个 Compose 测试，再修改页面让测试通过。

练习时继续遵守这次的顺序：

```text
先写失败测试 → 看见它失败 → 写最少代码 → 看见全部测试通过
```

## 10. 后续课程与当前功能边界

第 2 课会加入：

- API 设置与 Keystore 加密保存密钥；
- 手动“整理这一天”；
- AI 返回结构化结果；
- 本地生成每日 Markdown 与历史阅览。

第 3 课再加入：

- 基于本地资料的聊天；
- 上下文检索、引用日期与聊天记录持久化。

本课只讲本地文字随记，不展开 AI、图片、录音、后台定时任务或新闻推荐。

继续学习：[第 2 课：AI 每日 Markdown 与历史阅览](02-ai-daily-markdown-and-reader.md)。
