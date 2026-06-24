import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
}

group = "dev.lezli"
version = "0.2.0" // x-release-please-version

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdea("2025.2.6.2")
        testFramework(TestFrameworkType.Platform)
    }

    testImplementation("junit:junit:4.13.2")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    jvmToolchain(21)
}

intellijPlatform {
    // This plugin ships no UI forms and relies on no @NotNull bytecode
    // instrumentation, so code instrumentation is disabled. It also avoids a
    // local toolchain issue where instrumentCode resolves a JDK path that does
    // not exist. CI behavior is unaffected because no instrumented code exists.
    instrumentCode = false

    pluginConfiguration {
        ideaVersion {
            sinceBuild = "252"
        }

        changeNotes = """
            Initial development build.
        """.trimIndent()
    }
}
