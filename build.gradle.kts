import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
    id("java")
    // IntelliJ Platform Gradle Plugin 2.x：提供 intellijPlatform {} 扩展和 intellij platform 任务组
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = "com.ranju"
version = "0.3.1"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

java {
    // 2026.2 平台是 Java 25 字节码，编译必须用 JDK 25（IDEA 自带的 jbr 即可）
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

// 配了 ideaHome（见 gradle.properties）就直接用本机装的 IDEA，省掉下载约 1.5 GB 的平台包
val ideaHome: String? = providers.gradleProperty("ideaHome").orNull

dependencies {
    intellijPlatform {
        if (ideaHome != null) {
            local(ideaHome)
        } else {
            create(IntelliJPlatformType.IntellijIdeaUltimate, "2026.2.3")
            // 在线平台下 JCEF 作为内置插件依赖，会自动进入编译类路径
            bundledPlugin("com.intellij.modules.jcef")
        }
    }

    if (ideaHome != null) {
        // 2026.2 起 JCEF 拆成独立内置插件，类在 plugins/jcef-plugin/lib/modules 下，
        // 2.2.1 版插件不会自动把它加进编译类路径，这里手动补上
        compileOnly(
            fileTree("$ideaHome/plugins/jcef-plugin/lib/modules") { include("*jcef*.jar") }
        )
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "com.ranju.fenbi-idea"
        name = "Fenbi Xingce"
        version = project.version.toString()
        description = """
            在 IntelliJ IDEA 里刷粉笔行测题：按考点组卷、作答、交卷看解析。<br/>
            支持整卷/每题限时、暂停与切屏自动暂停、键盘作答、本地错题本、按模块统计，以及资料分析的材料固定、表格与图片优化。<br/>
            使用前请在 Settings | Tools | Fenbi 行测 中填入粉笔网页版的 Cookie。
        """.trimIndent()
        vendor {
            name = "RAN JU"
        }
        ideaVersion {
            sinceBuild = "262"
            untilBuild = provider { null }
        }
    }

    // 搜索选项构建需要启动沙箱 IDE，开发插件时没必要，关掉能省一次启动
    buildSearchableOptions = false
}
