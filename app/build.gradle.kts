import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// 发布签名凭据只放在本机 gitignored 的 keystore.properties 里。
// 没有凭据时退回到 debug 签名并给出警告，保证任何机器都能打出可安装的包，
// 但那种包只能自己调试，不能分发。
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.isFile) {
        file.inputStream().use(::load)
    }
}
val hasReleaseKeystore = keystoreProperties.getProperty("storeFile") != null

// 本机默认 AI 连接：只从 gitignored 的 api.properties 读取，密钥不进版本库，
// 也不写死在源码里。别人 clone 后没有这份文件，仍然需要在设置里自行配置。
val apiDefaults = Properties().apply {
    val file = rootProject.file("api.properties")
    if (file.isFile) {
        file.inputStream().use(::load)
    }
}

private fun buildConfigString(value: String?): String =
    "\"" + value.orEmpty().replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "io.github.tyxiumud.personalainote"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.tyxiumud.personalainote"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "DEFAULT_API_BASE_URL",
            buildConfigString(apiDefaults.getProperty("apiBaseUrl")),
        )
        buildConfigField(
            "String",
            "DEFAULT_API_MODEL",
            buildConfigString(apiDefaults.getProperty("apiModel")),
        )
        buildConfigField(
            "String",
            "DEFAULT_API_ALT_MODEL",
            buildConfigString(apiDefaults.getProperty("apiAltModel")),
        )
        buildConfigField(
            "String",
            "DEFAULT_API_KEY",
            buildConfigString(apiDefaults.getProperty("apiKey")),
        )
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                logger.warn(
                    "未找到 keystore.properties：release 将使用 debug 签名，" +
                        "仅可本机调试，不要分发。参考 keystore.properties.example 配置。",
                )
                signingConfigs.getByName("debug")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
            )
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
