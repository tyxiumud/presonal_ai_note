# Personal AI Note

一个**本地优先**的 Android 随记应用：随手记下文字、图片、录音，让自选的 AI 服务把当天内容整理成 Markdown，再基于自己的历史记录聊天，回答里可以点回被引用的原始随记。

> 这是个人开源项目，处于早期阶段，接口与数据格式仍可能变化。项目最初按教学 Demo 设计，代码与文档保留了较完整的注释和讲解。

## 它能做什么

- **随记**：按日期记录文字 / 图片 / 录音，可补录过去的日期；全局搜索与标签筛选，写的时候就能选标签。
- **每日整理**：调用 OpenAI-compatible 接口生成结构化结果，落到本地 `analysis.md`；任务、建议、回顾栏目可在设置里开关。
- **本地上下文聊天**：支持多会话。回答参考最近的记录（默认近 7 天、关键词检索、约 2000 字符预算），气泡下「查看原文」能逐条回溯真正被引用的随记。
- **图片与语音**：图片压缩后交给视觉模型生成描述，录音交给转写模型，结果一并纳入检索。
- **备份与迁移**：按日期导出 zip，每天一个目录，含 `notes.md`、`analysis.md`、`media/` 原文件与 `chat/conversations.md`。Markdown 人可读，导入时按日期合并、已有日期整日跳过；旧 JSON 格式备份仍可导入。
- **外观与人格**：4 套配色、3 种聊天人格，只影响呈现，不改数据与检索逻辑。

## 数据在哪里

- 随记、整理结果、聊天记录、媒体文件都存**本机 App 私有目录**（Room + 文件），不上传、无账号、无埋点。
- 唯一联网的地方是你**自己配置的 AI 接口**（聊天 / 整理 / 视觉 / 转写）。不配置也能用：随记、搜索、阅览、备份全是本地功能。
- API Key 经 Android Keystore 加密后保存，不写进源码，也不进版本库。

## 构建

需要 Android Studio、Android SDK Platform 37、JDK 17+。依赖版本见 `gradle/libs.versions.toml`（AGP 9.5.0 / Kotlin 2.2.10 / Compose BOM 2026.02.01 / Room 2.8.4），`minSdk 24`。

```bash
git clone git@github.com:tyxiumud/presonal_ai_note.git
cd presonal_ai_note
cp api.properties.example api.properties   # 可选，见下
./gradlew assembleDebug
```

`api.properties` 和 `keystore.properties` 都被 `.gitignore` 忽略，**两者都不是构建必需项**：没有 `api.properties` 时默认值为空，首次启动后在「设置」里自行填写；没有 `keystore.properties` 时 release 包退回 debug 签名并打印警告（仅可本机调试，不要分发）。

### api.properties（可选，仅本机开发用）

| 字段 | 说明 |
|---|---|
| `apiBaseUrl` | OpenAI-compatible 服务根地址，例如 `https://api.openai.com/v1` |
| `apiModel` | 默认聊天 / 整理模型 |
| `apiAltModel` | 设置页一键切换的备用模型 |
| `apiKey` | 默认密钥 |

> ⚠️ 这些值会被编译进 `BuildConfig`，**不要把填了真实密钥的构建产物分发给别人**。正式使用请在 App 设置页填写，密钥由 Keystore 加密保存。

### 发布签名

复制 `keystore.properties.example` 为 `keystore.properties` 并填入 4 个值，详见 [打包与签名](docs/release-signing.md)。密钥库和两个密码请自行备份——丢了就无法再给已安装的用户发布升级包。

## 文档

| 文档 | 内容 |
|---|---|
| [开发进度与待办](docs/DEV_ROADMAP.md) | 当前做到哪、接下来做什么 |
| [架构框图](docs/architecture.html) | 四层架构，浏览器直接打开 |
| [设计文档](docs/superpowers/specs/2026-11-04-personal-ai-vault-design.md) | 产品边界、数据设计、里程碑与验收标准 |
| [教程 1：本地文字随记](docs/tutorial/01-local-text-journal.md) | Room / ViewModel / Composable 数据流 |
| [教程 2：AI 每日整理与阅览](docs/tutorial/02-ai-daily-markdown-and-reader.md) | 密钥存放、网络层、来源指纹 |
| [教程 3：本地上下文聊天](docs/tutorial/03-local-context-chat.md) | 上下文构建、引用可追溯 |
| [打包与签名](docs/release-signing.md) | release 包、keystore、R8 规则、上架前清单 |

## 许可证

[MIT](LICENSE) © 2026 tyxiumud

欢迎 issue / PR。提交前请确认没有把 `api.properties`、`keystore.properties` 或 `*.jks` 带进版本库。
