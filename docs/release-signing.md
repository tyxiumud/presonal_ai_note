# 打包与签名

## 两个 APK

| 类型 | 路径 | 用途 |
|---|---|---|
| debug | `app/build/outputs/apk/debug/app-debug.apk` | 日常开发、IDE 直接 Run，签名是系统 debug key |
| **release** | `app/build/outputs/apk/release/app-release.apk` | **已签名、开了 R8 压缩与混淆**，可以直接装真机、发给别人 |

两者签名不同，**不能互相覆盖安装**。从 debug 换到 release 时先卸载旧版（数据会一起清掉，重要内容先用右上角备份导出 zip）。

## 首次配置签名

凭据不进版本库，只留本机：

```bash
cp keystore.properties.example keystore.properties
# 然后按里面的注释生成 keystore，并填入 4 个值
```

`keystore.properties` 和 `*.jks` 已在 `.gitignore` 里。没有这个文件也能构建，但 release 会退回 debug 签名并在构建日志里警告——**那种包只能自己调试，不要分发**。

生成密钥库（示例）：

```bash
keytool -genkeypair -v \
  -keystore personal-ai-vault-release.jks -storetype JKS \
  -alias personal-ai-vault -keyalg RSA -keysize 2048 -validity 10000
```

> 密钥库和两个密码要一起备份到安全的地方。丢了就没法再给已经装过的人发升级包，Android 只允许同一个签名覆盖安装。

## 构建与校验

用 Android Studio 的 Gradle 面板运行 `assembleRelease`，或命令行：

```bash
./gradlew :app:assembleRelease
```

校验签名（需要本机有 JDK；本仓库环境没配 `JAVA_HOME`，可临时指定）：

```bash
export JAVA_HOME=/path/to/jdk
$ANDROID_SDK/build-tools/37.0.0/apksigner verify --verbose \
  app/build/outputs/apk/release/app-release.apk
```

期望输出包含 `Verifies`、`Verified using v2 scheme: true`、`Verified using v3 scheme: true`。

## R8 / 混淆

规则在 [keepRules/rules.keep](../app/src/main/keepRules/rules.keep)，AGP 会自动合并 `src/main/keepRules` 下的文件。当前固定了三类东西：

- **按名字持久化的枚举**：`CaptureType` / `ChatPersona` / `AppSkin` 的 `name()` 写进了数据库和偏好，改名会让旧数据静默退回默认值。
- **Room 实体与 DAO**：防止升级 Room 或调整优化范围后反射找不到类。
- **备份用的 data class**：目前是手写 `org.json`，这条是防御性的。

每次升级 AGP / Room / R8 之后，务必**装一次 release 包并点开主要页面**。这一步不是形式主义：AGP 9.5 的 `optimization.packageScope` 把 `kotlin.**` 纳入优化时，打出过能编译、能签名、但一启动就 `IllegalAccessError: Class kotlin.collections.h ... inaccessible` 的包。现在改用标准的 `isMinifyEnabled` + `proguard-android-optimize.txt`，并在模拟器上实测过启动、写库、设置、切皮肤。

映射文件在 `app/build/outputs/mapping/release/`，线上崩溃栈用它还原。

## 还差什么才能上架

- `applicationId` 目前是模板的 `io.github.tyxiumud.personalainote`，换成自己的包名。
- 上架 Play 需要 AAB：`:app:bundleRelease`。
- 补一份隐私说明（本 App 会把用户主动提交的随记发给他自己配置的 API）。
- 换一个正式的应用图标（当前是自制的矢量图标，可以保留）。
