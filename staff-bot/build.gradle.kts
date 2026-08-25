import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

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

val verifyStaffBotRuntime by tasks.registering {
    group = "verification"
    description = "Verifies the executable staff-bot shaded jar and preserves integrity evidence."
    dependsOn(tasks.shadowJar)

    doLast {
        val runtimeJar = tasks.shadowJar.get().archiveFile.get().asFile
        check(runtimeJar.isFile) { "Missing staff-bot runtime jar: $runtimeJar" }

        fun resolvedVersion(group: String, name: String): String {
            val matches = configurations.runtimeClasspath.get()
                .resolvedConfiguration
                .resolvedArtifacts
                .filter { artifact ->
                    artifact.moduleVersion.id.group == group && artifact.name == name
                }
            check(matches.size == 1) {
                "Expected exactly one resolved $group:$name artifact but found ${matches.size}"
            }
            return matches.single().moduleVersion.id.version
        }

        val jacksonCoreVersion = resolvedVersion("com.fasterxml.jackson.core", "jackson-core")
        val jacksonDatabindVersion = resolvedVersion("com.fasterxml.jackson.core", "jackson-databind")
        check(jacksonCoreVersion == "2.22.1") {
            "staff-bot must resolve patched jackson-core 2.22.1, found $jacksonCoreVersion"
        }
        check(jacksonDatabindVersion == "2.22.1") {
            "staff-bot must resolve patched jackson-databind 2.22.1, found $jacksonDatabindVersion"
        }

        var entryCount = 0
        ZipFile(runtimeJar).use { archive ->
            val manifestEntry = checkNotNull(archive.getEntry("META-INF/MANIFEST.MF")) {
                "Staff-bot runtime jar is missing META-INF/MANIFEST.MF"
            }
            val manifest = archive.getInputStream(manifestEntry).bufferedReader(Charsets.UTF_8).use { it.readText() }
            check(manifest.lineSequence().any {
                it.trim() == "Main-Class: net.enthusia.staff.discordbot.StaffBotApplication"
            }) { "Staff-bot runtime jar has the wrong Main-Class" }

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
            .digest(runtimeJar.readBytes())
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        val reportDirectory = rootProject.layout.buildDirectory.dir("reports/runtime-jars").get().asFile
        reportDirectory.mkdirs()
        runtimeJar.copyTo(reportDirectory.resolve(runtimeJar.name), overwrite = true)
        reportDirectory.resolve("staff-bot-integrity.txt").writeText(
            buildString {
                appendLine("Staff bot runtime inspection")
                appendLine("============================")
                appendLine()
                appendLine("runtime: ${runtimeJar.name}")
                appendLine("size: ${runtimeJar.length()} bytes")
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

tasks.check {
    dependsOn(verifyStaffBotRuntime)
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}
