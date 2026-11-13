# AI 设置、每日 Markdown 与阅览 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有本地文字随记之上加入可配置的 OpenAI-compatible API、手动按日整理、独立 Markdown 存储，以及可浏览历史日期和整理结果的阅览页面。

**Architecture:** 保持单 app 模块和现有 Compose → ViewModel → Repository 分层。API 密钥由 Android Keystore 加密后保存，非秘密设置存入应用私有偏好；原始 Capture 继续存 Room，DailyAnalysis 元数据存 Room v2，Markdown 使用 AtomicFile 写入应用私有目录。根页面使用简单底部导航连接“记录 / 阅览 / 设置”，不在本里程碑引入聊天或复杂导航框架。

**Tech Stack:** Kotlin, Jetpack Compose Material 3, ViewModel/StateFlow, Room 2.8.4, Android Keystore AES/GCM, HttpURLConnection, org.json, AtomicFile, JUnit/Coroutines Test/AndroidX Test。

**Spec:** `docs/superpowers/specs/2026-11-04-personal-ai-vault-design.md`

## Global Constraints

- 原始内容与 AI 解析内容分开保存，AI 失败不得修改或删除 Capture。
- API 供应商不进入领域/UI 接口；网络层采用可配置的 OpenAI-compatible `POST /chat/completions`。
- API 密钥不得写入源码、资源、日志、测试报告或明文偏好。
- 只有用户主动点击整理或测试连接时才发送网络请求。
- 本里程碑不实现聊天、图片、录音、定时任务和新闻。
- 不新增第三方网络或序列化依赖，确保现有离线 Gradle 环境仍可构建。
- 现有数据库必须通过显式 1→2 migration 保留用户随记。
- 每项生产行为先写失败测试并观察 RED，再写最小实现观察 GREEN。

## Review Focus

- Room v1 中已有随记时升级到 v2，原数据必须完整保留且新增表可用；由 Task 2 migration device test 覆盖。
- API 返回 HTTP 非 2xx、空 choices、代码围栏 JSON 或非法 JSON 时，必须得到可理解错误且不泄露私人请求；由 Task 3 unit tests 覆盖。
- Keystore 密钥存储后偏好文件不得出现明文，删除密钥后连接配置必须失效；由 Task 1 device test 覆盖。
- 已有成功 Markdown 时重新整理失败，旧文件和旧元数据必须仍可阅览；由 Task 4 repository tests 覆盖。
- 某日整理后又新增记录，阅览页必须显示“需要重新整理”，不能把旧结果冒充最新；由 Task 5 ViewModel tests 覆盖。

---

### Task 1: 本地 AI 设置与 Keystore 密钥

**Files:**
- Create: `app/src/main/java/io/github/tyxiumud/personalainote/ai/settings/AiSettings.kt`
- Create: `app/src/main/java/io/github/tyxiumud/personalainote/ai/settings/AiSettingsStore.kt`
- Create: `app/src/main/java/io/github/tyxiumud/personalainote/ai/settings/KeystoreAiSettingsStore.kt`
- Create: `app/src/main/java/io/github/tyxiumud/personalainote/ai/settings/SettingsViewModel.kt`
- Create: `app/src/main/java/io/github/tyxiumud/personalainote/ai/settings/SettingsScreen.kt`
- Create: `app/src/test/java/io/github/tyxiumud/personalainote/ai/settings/SettingsViewModelTest.kt`
- Create: `app/src/androidTest/java/io/github/tyxiumud/personalainote/ai/settings/KeystoreAiSettingsStoreTest.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Produces: `AiSettingsSnapshot`, `AiConnectionSettings`, and `AiSettingsStore.observe()/save()/connection()/clearApiKey()`.
- Produces: Settings UI state/events used by Task 6 root navigation.
- Consumes: none.

- [ ] Write tests for validation, save-without-replacing-existing-key, clear key, encrypted round-trip, and no plaintext key in preferences.
- [ ] Run focused unit/device tests and observe expected failures because the settings types do not exist.
- [ ] Implement the settings models, Keystore AES/GCM storage, ViewModel, and Compose form with base URL, model, masked key field, output-option switches, save and clear actions.
- [ ] Run focused tests and confirm all pass.
- [ ] Record completion in this plan’s ledger; Git commit is unavailable because this workspace is not a Git repository.

### Task 2: DailyAnalysis Room v2 与历史日期查询

**Files:**
- Create: `app/src/main/java/io/github/tyxiumud/personalainote/analysis/data/DailyAnalysisEntity.kt`
- Create: `app/src/main/java/io/github/tyxiumud/personalainote/analysis/data/DailyAnalysisDao.kt`
- Create: `app/src/androidTest/java/io/github/tyxiumud/personalainote/analysis/data/DatabaseMigration1To2Test.kt`
- Create: `app/src/androidTest/java/io/github/tyxiumud/personalainote/analysis/data/DailyAnalysisDaoTest.kt`
- Modify: `app/src/main/java/io/github/tyxiumud/personalainote/journal/data/PersonalVaultDatabase.kt`
- Modify: `app/src/main/java/io/github/tyxiumud/personalainote/journal/data/CaptureDao.kt`
- Modify: `app/src/main/java/io/github/tyxiumud/personalainote/journal/data/CaptureRepository.kt`
- Modify: `app/src/main/java/io/github/tyxiumud/personalainote/journal/data/RoomCaptureRepository.kt`

**Interfaces:**
- Produces: `DailyAnalysisDao.observeByDay()/upsert()`, `PersonalVaultDatabase.MIGRATION_1_2`, and `CaptureRepository.observeDays()/getByDay()`.
- Consumes: existing Capture schema and repository behavior.

- [ ] Write DAO and migration tests proving analysis persistence, date observation, and v1 Capture preservation.
- [ ] Run focused device tests and observe compile/test failure for missing schema and APIs.
- [ ] Add DailyAnalysis entity/DAO, database version 2 migration, history-date queries, and repository mappings.
- [ ] Run focused device tests and confirm all pass; confirm generated schema 2 JSON exists.
- [ ] Record completion in the ledger.

### Task 3: OpenAI-compatible client 与结构化结果

**Files:**
- Create: `app/src/main/java/io/github/tyxiumud/personalainote/ai/api/DailyAnalysisApi.kt`
- Create: `app/src/main/java/io/github/tyxiumud/personalainote/ai/api/OpenAiCompatibleApiClient.kt`
- Create: `app/src/main/java/io/github/tyxiumud/personalainote/ai/api/HttpTransport.kt`
- Create: `app/src/test/java/io/github/tyxiumud/personalainote/ai/api/OpenAiCompatibleApiClientTest.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Produces: `DailyAnalysisApi.testConnection(connection)` and `analyze(connection, request): StructuredDailyAnalysis`.
- Consumes: `AiConnectionSettings` from Task 1.

- [ ] Write fake-transport unit tests for endpoint normalization, bearer authentication, JSON request escaping, success parsing, fenced JSON, non-2xx, missing choices, malformed structured content, and HTTPS validation.
- [ ] Run focused unit tests and observe expected missing-type failures.
- [ ] Implement HttpURLConnection transport on IO dispatcher and provider-neutral Chat Completions adapter; add INTERNET permission.
- [ ] Run focused tests and confirm all pass.
- [ ] Record completion in the ledger.

### Task 4: 原子 Markdown 文件与每日整理服务

**Files:**
- Create: `app/src/main/java/io/github/tyxiumud/personalainote/analysis/model/DailyAnalysis.kt`
- Create: `app/src/main/java/io/github/tyxiumud/personalainote/analysis/data/DailyAnalysisRepository.kt`
- Create: `app/src/main/java/io/github/tyxiumud/personalainote/analysis/data/RoomDailyAnalysisRepository.kt`
- Create: `app/src/main/java/io/github/tyxiumud/personalainote/analysis/data/DailyMarkdownStore.kt`
- Create: `app/src/main/java/io/github/tyxiumud/personalainote/analysis/domain/DailyAnalysisService.kt`
- Create: `app/src/test/java/io/github/tyxiumud/personalainote/analysis/domain/DailyAnalysisServiceTest.kt`
- Create: `app/src/androidTest/java/io/github/tyxiumud/personalainote/analysis/data/DailyMarkdownStoreTest.kt`

**Interfaces:**
- Produces: `DailyAnalysisService.analyze(day)`, `DailyAnalysisRepository.observeByDay(day)`, deterministic `sourceFingerprint(captures)`.
- Consumes: CaptureRepository, DailyAnalysisDao, DailyAnalysisApi, AiSettingsStore.

- [ ] Write tests for no settings, empty day, option-aware Markdown, stable fingerprint, atomic successful write, and preserving the prior file/metadata when API or parsing fails.
- [ ] Run focused tests and observe expected missing-type failures.
- [ ] Implement structured-result rendering, SHA-256 fingerprinting, AtomicFile storage, repository, and orchestration service.
- [ ] Run focused unit/device tests and confirm all pass.
- [ ] Record completion in the ledger.

### Task 5: 记录页整理状态与阅览页

**Files:**
- Modify: `app/src/main/java/io/github/tyxiumud/personalainote/journal/ui/JournalViewModel.kt`
- Modify: `app/src/main/java/io/github/tyxiumud/personalainote/journal/ui/JournalScreen.kt`
- Modify: `app/src/main/java/io/github/tyxiumud/personalainote/journal/ui/JournalPreview.kt`
- Create: `app/src/main/java/io/github/tyxiumud/personalainote/reader/ReaderViewModel.kt`
- Create: `app/src/main/java/io/github/tyxiumud/personalainote/reader/ReaderScreen.kt`
- Modify: `app/src/test/java/io/github/tyxiumud/personalainote/journal/ui/JournalViewModelTest.kt`
- Create: `app/src/test/java/io/github/tyxiumud/personalainote/reader/ReaderViewModelTest.kt`
- Modify: `app/src/androidTest/java/io/github/tyxiumud/personalainote/journal/ui/JournalScreenTest.kt`
- Create: `app/src/androidTest/java/io/github/tyxiumud/personalainote/reader/ReaderScreenTest.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Produces: Journal “整理这一天” flow, Reader date list/detail state, stale indicator, retry/error states.
- Consumes: Task 2 date queries and Task 4 analysis service/repository.

- [ ] Write ViewModel/UI tests for missing configuration, empty day, analyzing/success/failure, selecting historical days, raw record display, Markdown display, no-analysis empty state, and fingerprint mismatch stale state.
- [ ] Run focused tests and observe expected failures.
- [ ] Add Journal analysis action/state and Reader ViewModel/Compose UI with date selection and plain-text Markdown rendering.
- [ ] Run focused unit/device tests and confirm all pass.
- [ ] Record completion in the ledger.

### Task 6: 三入口应用外壳、依赖装配与连接测试

**Files:**
- Create: `app/src/main/java/io/github/tyxiumud/personalainote/ui/PersonalVaultApp.kt`
- Modify: `app/src/main/java/io/github/tyxiumud/personalainote/AppContainer.kt`
- Modify: `app/src/main/java/io/github/tyxiumud/personalainote/MainActivity.kt`
- Modify: `app/src/main/java/io/github/tyxiumud/personalainote/ai/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/io/github/tyxiumud/personalainote/ai/settings/SettingsScreen.kt`
- Create: `app/src/androidTest/java/io/github/tyxiumud/personalainote/ui/PersonalVaultAppTest.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Produces: runnable app with bottom navigation “记录 / 阅览 / 设置” and Settings “测试连接”.
- Consumes: all repositories/services/ViewModels from Tasks 1–5.

- [ ] Write UI and SettingsViewModel tests for navigation, save feedback, connection success/failure, and no-config connection rejection.
- [ ] Run focused tests and observe failures.
- [ ] Wire AppContainer factories, Activity ViewModels, root bottom navigation, and connection-test action.
- [ ] Run focused tests and confirm all pass.
- [ ] Record completion in the ledger.

### Task 7: 教学文档与完整验收

**Files:**
- Create: `docs/tutorial/02-ai-daily-markdown-and-reader.md`
- Create: `.superpowers/sdd/2026-11-05-ai-daily-markdown-reader/progress.md`
- Modify: `docs/tutorial/01-local-text-journal.md` only to link the next lesson if needed.

**Interfaces:**
- Consumes: completed runnable flow from Tasks 1–6.
- Produces: beginner-readable explanation and final verification evidence.

- [ ] Document settings → encrypted key → API client → structured result → AtomicFile → Room metadata → Reader data flow, including exact setup examples without real secrets.
- [ ] Run `:app:testDebugUnitTest :app:connectedDebugAndroidTest :app:assembleDebug --offline --no-configuration-cache --no-daemon`.
- [ ] Manually validate on the API 37 emulator: three tabs, saved settings, masked key, invalid configuration errors, historical reading, no-capture analysis block, and restart persistence. Do not use a real paid API key during automated verification.
- [ ] Run Android Studio inspections on all changed production files and resolve errors.
- [ ] Request one whole-feature code review; fix Critical/Important findings once with RED→GREEN tests and ledger deferred minors.
- [ ] Re-run the full suite and report the APK path and safe API setup instructions.
