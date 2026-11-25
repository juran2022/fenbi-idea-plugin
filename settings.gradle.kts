plugins {
    // 本机没有 JDK 17 时，Gradle 会自动下载
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "fenbi-idea-plugin"
