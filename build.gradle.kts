import org.jetbrains.grammarkit.tasks.GenerateLexerTask
import org.jetbrains.grammarkit.tasks.GenerateParserTask
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.grammarkit")
}

group = "dev.lezli"
version = "0.4.0" // x-release-please-version

repositories {
    mavenCentral()
    maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
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

// --- Grammar-Kit + JFlex code generation ---------------------------------
// Source grammar lives in src/main/grammar; generated parser/PSI/lexer go to
// build/generated/sources/grammarkit and are added to the main source set.
// Regenerate explicitly with: ./gradlew generateFirestoreParser generateFirestoreLexer
// (both run automatically before compileKotlin/compileJava).
val generatedSourcesDir = layout.buildDirectory.dir("generated/sources/grammarkit")

grammarKit {
    jflexRelease.set("1.9.2")
    grammarKitRelease.set("2022.3.2")
}

val generateFirestoreParser by tasks.registering(GenerateParserTask::class) {
    sourceFile.set(layout.projectDirectory.file("src/main/grammar/FirestoreRules.bnf"))
    targetRootOutputDir.set(generatedSourcesDir)
    pathToParser.set("dev/lezli/hotrulez/parser/FirestoreRulesParser.java")
    pathToPsiRoot.set("dev/lezli/hotrulez/psi")
    purgeOldFiles.set(true)
}

val generateFirestoreLexer by tasks.registering(GenerateLexerTask::class) {
    sourceFile.set(layout.projectDirectory.file("src/main/grammar/FirestoreRules.flex"))
    targetOutputDir.set(generatedSourcesDir.map { it.dir("dev/lezli/hotrulez/lexer") })
    purgeOldFiles.set(true)
    dependsOn(generateFirestoreParser)
}

sourceSets {
    main {
        java.srcDir(generatedSourcesDir)
    }
}

tasks.named("compileKotlin") {
    dependsOn(generateFirestoreParser, generateFirestoreLexer)
}

tasks.named("compileJava") {
    dependsOn(generateFirestoreParser, generateFirestoreLexer)
}

// Tests reference the generated types directly; make the dependency explicit so a
// test-only compile never races ahead of generation.
tasks.named("compileTestKotlin") {
    dependsOn(generateFirestoreParser, generateFirestoreLexer)
}

// The platform test framework boots a real IDE whose home is the distribution the
// IntelliJ Platform Gradle plugin extracts into a Gradle artifact-transform output.
// That output is an *immutable* workspace. By default the IDE's OpenTelemetry
// exporters write open-telemetry-metrics.*.csv / -meters.*.json / -plotter.html into
// {ideHome}/system/log, mutating the immutable workspace and tripping Gradle's
// integrity check ("the contents of the immutable workspace ... have been modified")
// during :intellijPlatformTestClasspath resolution.
//
// Pointing the exporter file properties at "" did NOT stop the writes: the platform
// treats the empty/relative value as a name resolved against the IDE log dir, which
// is {ideHome}/system/log -- still inside the immutable workspace. Instead we move
// the booted IDE's whole log dir to a writable build directory via idea.log.path
// (honoured by PathManager.getLogPath()), and also give the exporters absolute paths
// there. Either redirect alone keeps the transform workspace pristine; both are set
// for safety. Property names per JetBrains KB SUPPORT-A-714.
tasks.withType<Test>().configureEach {
    val ideLogDir = layout.buildDirectory.dir("test-ide-log").get().asFile
    doFirst { ideLogDir.mkdirs() }
    systemProperty("idea.log.path", ideLogDir.absolutePath)
    systemProperty(
        "idea.diagnostic.opentelemetry.metrics.file",
        ideLogDir.resolve("open-telemetry-metrics.csv").absolutePath,
    )
    systemProperty(
        "idea.diagnostic.opentelemetry.meters.file.json",
        ideLogDir.resolve("open-telemetry-meters.json").absolutePath,
    )
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
