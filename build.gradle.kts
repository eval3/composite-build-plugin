import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.3.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.0"
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

kotlin {
    jvmToolchain(17)
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

val localAndroidStudioPath = "/Applications/Android Studio Preview.app/Contents"
val isLocalBuild = file(localAndroidStudioPath).exists()

dependencies {
    intellijPlatform {
        // 本地开发用本地 Android Studio；CI 环境自动下载指定版本
        if (isLocalBuild) {
            local(localAndroidStudioPath)
        } else {
            androidStudio(providers.gradleProperty("ciAndroidStudioVersion").get())
        }

        // Required bundled plugins
        bundledPlugin("com.intellij.gradle")
        bundledPlugin("org.jetbrains.plugins.gradle")
        // 使用 androidStudio() 下载时，org.jetbrains.android 是平台核心组件，无需单独声明
        if (isLocalBuild) {
            bundledPlugin("org.jetbrains.android")
        }

        pluginVerifier()
        zipSigner()
        testFramework(TestFrameworkType.Platform)
    }

    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // 编译期引用本机已安装的 MCP Server 插件 jar（运行时由 IDE 提供，不打包进发布产物）
    val mcpJar = file("${System.getProperty("user.home")}/Library/Application Support/JetBrains/IdeaIC2025.2/plugins/mcpserver/lib/mcpserver.jar")
    if (mcpJar.exists()) {
        compileOnly(files(mcpJar))
    }

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

intellijPlatform {
    pluginConfiguration {
        name = "Composite Build Manager"
        version = providers.gradleProperty("pluginVersion")

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            // 不设版本上限，兼容所有未来版本
            untilBuild = provider { null }
        }
    }

    signing {
        certificateChainFile = file("chain.crt")
        privateKeyFile = file("private.pem")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

tasks {
    wrapper {
        gradleVersion = "8.10"
    }

    test {
        useJUnitPlatform()
    }

    // Microsoft JDK (ms-*) 在 macOS 上缺少 Packages 目录，导致 instrumentCode 失败
    // 禁用字节码插桩任务，不影响 Kotlin null-safety 编译期检查
    instrumentCode {
        enabled = false
    }
    instrumentTestCode {
        enabled = false
    }
    // 使用本地 Android Studio 作为平台时，沙箱环境不兼容，禁用此任务
    buildSearchableOptions {
        enabled = false
    }
}
