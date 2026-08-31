import java.io.File
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

plugins {
    application
    id("com.gradleup.shadow")
}

abstract class VerifyStaffBotRuntime : DefaultTask() {
    private data class RuntimeInspection(
        val entryCount: Int,
        val jacksonCoreVersion: String,
        val jacksonDatabindVersion: String,
    )

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val runtimeJar: RegularFileProperty

    @get:OutputDirectory
    abstract val reportDirectory: DirectoryProperty

    @TaskAction
    fun verify() {
        val runtimeJarFile = runtimeJar.get().asFile
        check(runtimeJarFile.isFile) { "Missing staff-bot runtime jar: $runtimeJarFile" }
        val inspection = inspectRuntimeJar(runtimeJarFile)
        val digest = sha256(runtimeJarFile)
        writeReport(runtimeJarFile, digest, inspection)
    }

    private fun inspectRuntimeJar(runtimeJarFile: File): RuntimeInspection =
        ZipFile(runtimeJarFile).use { archive ->
            verifyManifest(archive)
            val jacksonCoreVersion = dependencyVersion(
                archive,
                "META-INF/maven/com.fasterxml.jackson.core/jackson-core/pom.properties",
                "jackson-core",
            )
            val jacksonDatabindVersion = dependencyVersion(
                archive,
                "META-INF/maven/com.fasterxml.jackson.core/jackson-databind/pom.properties",
                "jackson-databind",
            )
            verifyPatchedJackson(jacksonCoreVersion, jacksonDatabindVersion)
            RuntimeInspection(
                entryCount = verifyEntries(archive),
                jacksonCoreVersion = jacksonCoreVersion,
                jacksonDatabindVersion = jacksonDatabindVersion,
            )
        }

    private fun verifyManifest(archive: ZipFile) {
        val manifestEntry = checkNotNull(archive.getEntry("META-INF/MANIFEST.MF")) {
            "Staff-bot runtime jar is missing META-INF/MANIFEST.MF"
        }
        val manifest = archive.getInputStream(manifestEntry).bufferedReader(Charsets.UTF_8).use { it.readText() }
        check(manifest.lineSequence().any {
            it.trim() == "Main-Class: net.enthusia.staff.discordbot.StaffBotApplication"
        }) { "Staff-bot runtime jar has the wrong Main-Class" }
    }

    private fun dependencyVersion(archive: ZipFile, path: String, label: String): String {
        val entry = checkNotNull(archive.getEntry(path)) {
            "Staff-bot runtime jar is missing $label Maven metadata"
        }
        val properties = Properties()
        archive.getInputStream(entry).use(properties::load)
        return checkNotNull(properties.getProperty("version")) {
            "Staff-bot runtime jar has no version in $label Maven metadata"
        }
    }

    private fun verifyPatchedJackson(coreVersion: String, databindVersion: String) {
        check(coreVersion == "2.22.1") {
            "staff-bot must package patched jackson-core 2.22.1, found $coreVersion"
        }
        check(databindVersion == "2.22.1") {
            "staff-bot must package patched jackson-databind 2.22.1, found $databindVersion"
        }
    }

    private fun verifyEntries(archive: ZipFile): Int {
        var entryCount = 0
        var hasApplication = false
        var hasJda = false
        val entries = archive.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            entryCount++
            if (entry.name == "net/enthusia/staff/discordbot/StaffBotApplication.class") {
                hasApplication = true
            }
            if (entry.name == "net/dv8tion/jda/api/JDA.class") {
                hasJda = true
            }
            verifyExcludedClasses(entry.name)
            consumeEntry(archive, entry)
        }
        check(hasApplication) { "Staff-bot runtime jar is missing its application entry point" }
        check(hasJda) { "Staff-bot runtime jar is missing JDA runtime classes" }
        return entryCount
    }

    private fun verifyExcludedClasses(name: String) {
        check(!name.startsWith("club/minnced/opus/")) {
            "Audio-native Opus classes leaked into the no-audio staff-bot runtime: $name"
        }
        check(!name.startsWith("com/google/crypto/tink/")) {
            "Audio crypto classes leaked into the no-audio staff-bot runtime: $name"
        }
    }

    private fun consumeEntry(archive: ZipFile, entry: ZipEntry) {
        if (!entry.isDirectory) {
            archive.getInputStream(entry).use { input ->
                input.transferTo(OutputStream.nullOutputStream())
            }
        }
    }

    private fun sha256(runtimeJarFile: File): String = MessageDigest.getInstance("SHA-256")
        .digest(runtimeJarFile.readBytes())
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun writeReport(runtimeJarFile: File, digest: String, inspection: RuntimeInspection) {
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
                appendLine("entries: ${inspection.entryCount}")
                appendLine("main-class: net.enthusia.staff.discordbot.StaffBotApplication")
                appendLine("jda-runtime: present")
                appendLine("jackson-core: ${inspection.jacksonCoreVersion}")
                appendLine("jackson-databind: ${inspection.jacksonDatabindVersion}")
                appendLine("opus-native-classes: 0")
                appendLine("tink-audio-crypto-classes: 0")
            },
            Charsets.UTF_8,
        )
    }
}

dependencies {
    implementation(project(":persistence"))
    implementation("net.dv8tion:JDA:6.5.0") {
        exclude(module = "opus-java")
        exclude(module = "tink")
    }

    // JDA 6.5.0 publishes Jackson 2.22.0. CVE-2026-59889 is fixed in 2.22.1.
    // D16 also uses Jackson for strict private read-API DTO serialization.
    implementation("com.fasterxml.jackson.core:jackson-core:2.22.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:2.22.1")
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
