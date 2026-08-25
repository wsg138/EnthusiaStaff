import java.io.OutputStream
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipFile
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class VerifyStaffBotRuntime : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val runtimeJar: RegularFileProperty

    @get:OutputDirectory
    abstract val reportDirectory: DirectoryProperty

    @TaskAction
    fun verify() {
        val runtimeJarFile = runtimeJar.get().asFile
        check(runtimeJarFile.isFile) { "Missing staff-bot runtime jar: $runtimeJarFile" }

        var entryCount = 0
        var jacksonCoreVersion = ""
        var jacksonDatabindVersion = ""
        ZipFile(runtimeJarFile).use { archive ->
            val manifestEntry = checkNotNull(archive.getEntry("META-INF/MANIFEST.MF")) {
                "Staff-bot runtime jar is missing META-INF/MANIFEST.MF"
            }
            val manifest = archive.getInputStream(manifestEntry).bufferedReader(Charsets.UTF_8).use { it.readText() }
            check(manifest.lineSequence().any {
                it.trim() == "Main-Class: net.enthusia.staff.discordbot.StaffBotApplication"
            }) { "Staff-bot runtime jar has the wrong Main-Class" }

            fun dependencyVersion(path: String, label: String): String {
                val entry = checkNotNull(archive.getEntry(path)) {
                    "Staff-bot runtime jar is missing $label Maven metadata"
                }
                val properties = Properties()
                archive.getInputStream(entry).use(properties::load)
                return checkNotNull(properties.getProperty("version")) {
                    "Staff-bot runtime jar has no version in $label Maven metadata"
                }
            }

            jacksonCoreVersion = dependencyVersion(
                "META-INF/maven/com.fasterxml.jackson.core/jackson-core/pom.properties",
                "jackson-core")
            jacksonDatabindVersion = dependencyVersion(
                "META-INF/maven/com.fasterxml.jackson.core/jackson-databind/pom.properties",
                "jackson-databind")
            check(jacksonCoreVersion == "2.22.1") {
                "staff-bot must package patched jackson-core 2.22.1, found $jacksonCoreVersion"
            }
            check(jacksonDatabindVersion == "2.22.1") {
                "staff-bot must package patched jackson-databind 2.22.1, found $jacksonDatabindVersion"
            }

            var hasApplication = false
            var hasJda = false
            val entries = archive.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name
                entryCount++
                if (name == "net/enthusia/staff/discordbot/StaffBotApplication.class") {
                    hasApplication = true
                }
                if (name == "net/dv8tion/jda/api/JDA.class") {
                    hasJda = true
                }
                check(!name.startsWith("club/minnced/opus/")) {
                    "Audio-native Opus classes leaked into the no-audio staff-bot runtime: $name"
                }
                check(!name.startsWith("com/google/crypto/tink/")) {
                    "Audio crypto classes leaked into the no-audio staff-bot runtime: $name"
                }
                if (!entry.isDirectory) {
                    archive.getInputStream(entry).use { input ->
                        input.transferTo(OutputStream.nullOutputStream())
                    }
                }
            }
            check(hasApplication) { "Staff-bot runtime jar is missing its application entry point" }
            check(hasJda) { "Staff-bot runtime jar is missing JDA runtime classes" }
        }

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(runtimeJarFile.readBytes())
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        val reportDirectoryFile = reportDirectory.get().asFile
        reportDirectoryFile.mkdirs()
        runtimeJarFile.copyTo(reportDirectoryFile.resolve(runtimeJarFile.name), overwrite = true)
        reportDirectoryFile.resolve("staff-bot-integrity.txt").writeText(
            buildString {
                appendLine("Staff bot runtime inspection")
                appendLine("============================")
                appendLine()
                appendLine("runtime: ${runtimeJarFile.name}")
                appendLine("size: ${runtimeJarFile.length()} bytes")
                appendLine("sha256: $digest")
                appendLine("entries: $entryCount")
                appendLine("main-class: net.enthusia.staff.discordbot.StaffBotApplication")
                appendLine("jda-runtime: present")
                appendLine("jackson-core: $jacksonCoreVersion")
                appendLine("jackson-databind: $jacksonDatabindVersion")
                appendLine("opus-native-classes: 0")
                appendLine("tink-audio-crypto-classes: 0")
            },
            Charsets.UTF_8)
    }
}

plugins {
    application
    id("com.gradleup.shadow")
}

dependencies {
    implementation("net.dv8tion:JDA:6.5.0") {
        exclude(module = "opus-java")
        exclude(module = "tink")
    }

    // JDA 6.5.0 publishes Jackson 2.22.0. CVE-2026-59889 is fixed in 2.22.1.
    // Keep both Jackson core components aligned on the patched release.
    runtimeOnly("com.fasterxml.jackson.core:jackson-core:2.22.1")
    runtimeOnly("com.fasterxml.jackson.core:jackson-databind:2.22.1")
    runtimeOnly("org.slf4j:slf4j-jdk14:2.0.18")
}

application {
    mainClass.set("net.enthusia.staff.discordbot.StaffBotApplication")
}

tasks.jar {
    enabled = false
}

tasks.shadowJar {
    archiveBaseName.set("EnthusiaStaff-StaffBot")
    archiveClassifier.set("")
    mergeServiceFiles()
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }
}

val verifyStaffBotRuntime by tasks.registering(VerifyStaffBotRuntime::class) {
    group = "verification"
    description = "Verifies the executable staff-bot shaded jar and preserves integrity evidence."
    dependsOn(tasks.shadowJar)
    runtimeJar.set(tasks.shadowJar.flatMap { it.archiveFile })
    reportDirectory.set(rootProject.layout.buildDirectory.dir("reports/runtime-jars"))
}

tasks.check {
    dependsOn(verifyStaffBotRuntime)
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}
