import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import java.util.Properties

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

val localProps = Properties().also { props ->
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(props::load)
}

val localAndroidStudioPath: String = localProps.getProperty("androidStudioPath")
    ?: run {
        val os = System.getProperty("os.name").lowercase()
        when {
            os.contains("mac") ->
                File("/Applications").listFiles()
                    ?.filter { it.name.startsWith("Android Studio") && it.name.endsWith(".app") }
                    ?.map { "${it.absolutePath}/Contents" }
                    ?.firstOrNull { File(it).exists() }

            os.contains("win") ->
                listOfNotNull(
                    System.getenv("PROGRAMFILES")?.let { "$it\\Android\\Android Studio" },
                    System.getenv("LOCALAPPDATA")?.let { "$it\\Programs\\Android Studio" },
                ).firstOrNull { File(it).exists() }

            else ->
                listOf(
                    "${System.getProperty("user.home")}/android-studio",
                    "/opt/android-studio",
                    "/usr/local/android-studio",
                ).firstOrNull { File(it).exists() }
        }
    }
    ?: error("Android Studio not found. Set androidStudioPath in local.properties")

dependencies {
    intellijPlatform {
        local(localAndroidStudioPath)

        // Required bundled plugins
        bundledPlugin("com.intellij.gradle")
        bundledPlugin("org.jetbrains.plugins.gradle")
        bundledPlugin("org.jetbrains.android")

        pluginVerifier()
        zipSigner()
        testFramework(TestFrameworkType.Platform)
        plugin("com.intellij.mcpServer", "261.24374.39")
    }

    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    // IntelliJ's JUnit 5 session listener still references junit.framework.TestCase.
    testImplementation("junit:junit:4.13.2")
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
