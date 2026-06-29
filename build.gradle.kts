import org.jetbrains.grammarkit.tasks.GenerateLexerTask
import org.jetbrains.grammarkit.tasks.GenerateParserTask
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.grammarkit")
}

group = "dev.lezli"
version = "0.6.0" // x-release-please-version

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
// Regenerate explicitly with: ./gradlew generateFirebaseParser generateFirebaseLexer
// (both run automatically before compileKotlin/compileJava).
val generatedSourcesDir = layout.buildDirectory.dir("generated/sources/grammarkit")

grammarKit {
    jflexRelease.set("1.9.2")
    grammarKitRelease.set("2022.3.2")
}

val generateFirebaseParser by tasks.registering(GenerateParserTask::class) {
    sourceFile.set(layout.projectDirectory.file("src/main/grammar/FirebaseRules.bnf"))
    targetRootOutputDir.set(generatedSourcesDir)
    pathToParser.set("dev/lezli/hotrulez/parser/FirebaseRulesParser.java")
    pathToPsiRoot.set("dev/lezli/hotrulez/psi")
    purgeOldFiles.set(true)
}

val generateFirebaseLexer by tasks.registering(GenerateLexerTask::class) {
    sourceFile.set(layout.projectDirectory.file("src/main/grammar/FirebaseRules.flex"))
    targetOutputDir.set(generatedSourcesDir.map { it.dir("dev/lezli/hotrulez/lexer") })
    purgeOldFiles.set(true)
    dependsOn(generateFirebaseParser)
}

sourceSets {
    main {
        java.srcDir(generatedSourcesDir)
    }
}

tasks.named("compileKotlin") {
    dependsOn(generateFirebaseParser, generateFirebaseLexer)
}

tasks.named("compileJava") {
    dependsOn(generateFirebaseParser, generateFirebaseLexer)
}

// Tests reference the generated types directly; make the dependency explicit so a
// test-only compile never races ahead of generation.
tasks.named("compileTestKotlin") {
    dependsOn(generateFirebaseParser, generateFirebaseLexer)
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

// --- Plugin "What's New" derived from the release-please CHANGELOG -----------
// release-please owns CHANGELOG.md. Rather than hand-maintain changeNotes (which
// previously shipped a stale "Initial development build." into every release ZIP),
// render the latest CHANGELOG section into the small HTML subset the plugin
// descriptor accepts.
//
// The read goes through the file-contents provider (so CHANGELOG.md is tracked as
// a build input), but it is resolved and rendered EAGERLY to a plain String here
// at configuration time. A lazy Provider whose transform lambda closes over this
// build script cannot be serialized by Gradle's configuration cache once it is
// wired into patchPluginXml; a plain String can.

// Renders the most recent version section of a release-please CHANGELOG.md into
// the simple HTML accepted by plugin.xml <change-notes>. Strips the trailing
// "([sha](url))" commit links release-please appends to each entry, and maps
// "### Features"/"### Bug Fixes" headings and "*" bullets to <b>/<ul><li>.
fun renderLatestChangeNotes(changelog: String): String {
    val lines = changelog.lines()
    val start = lines.indexOfFirst { it.startsWith("## ") }
    if (start < 0) return ""
    val body = lines.drop(start + 1).takeWhile { !it.startsWith("## ") }

    fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    fun inline(s: String) =
        escape(s.replace(Regex("""\s*\(\[[0-9a-f]{6,}]\([^)]*\)\)\s*$"""), ""))
            .replace(Regex("""\*\*(.+?)\*\*"""), "<b>$1</b>")
            .replace(Regex("""`([^`]+)`"""), "<code>$1</code>")
            .replace(Regex("""\[(.+?)]\((https?://[^)]+)\)"""), """<a href="$2">$1</a>""")

    val html = StringBuilder()
    var inList = false
    fun closeList() { if (inList) { html.append("</ul>"); inList = false } }
    for (raw in body) {
        val line = raw.trim()
        when {
            line.isEmpty() -> {}
            line.startsWith("### ") -> {
                closeList()
                html.append("<p><b>").append(escape(line.removePrefix("### "))).append("</b></p>")
            }
            line.startsWith("* ") || line.startsWith("- ") -> {
                if (!inList) { html.append("<ul>"); inList = true }
                html.append("<li>").append(inline(line.drop(2))).append("</li>")
            }
            else -> {
                closeList()
                html.append("<p>").append(inline(line)).append("</p>")
            }
        }
    }
    closeList()
    return html.toString()
}

val changeNotesHtml: String =
    renderLatestChangeNotes(
        providers.fileContents(layout.projectDirectory.file("CHANGELOG.md")).asText.orNull.orEmpty(),
    ).ifBlank {
        "See the <a href=\"https://github.com/lezli01/hotrulez/blob/master/CHANGELOG.md\">CHANGELOG</a> for release notes."
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

        changeNotes = changeNotesHtml
    }
}
