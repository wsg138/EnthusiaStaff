import java.util.zip.ZipFile
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class VerifyTransitionBridgeRuntime : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val runtimeJar: RegularFileProperty

    @get:Input
    abstract val forbiddenEntries: ListProperty<String>

    @get:Input
    abstract val requiredEntries: ListProperty<String>

    @get:Input
    abstract val requiredMigrationEntries: ListProperty<String>

    @TaskAction
    fun verifyRuntimeJar() {
        ZipFile(runtimeJar.get().asFile).use { archive ->
            forbiddenEntries.get().forEach { entry ->
                check(archive.getEntry(entry) == null) {
                    "Transition bridge contains forbidden runtime class: $entry"
                }
            }
            requiredEntries.get().forEach { entry ->
                check(archive.getEntry(entry) != null) {
                    "Transition bridge is missing required runtime entry: $entry"
                }
            }
            check(requiredMigrationEntries.get().isNotEmpty()) {
                "No transition migration resources were discovered at build time"
            }
            requiredMigrationEntries.get().forEach { entry ->
                check(archive.getEntry(entry) != null) {
                    "Transition bridge is missing migration resource: $entry"
                }
            }
        }
    }
}

plugins {
    id("com.gradleup.shadow")
}

val paperApiCoordinate = listOf(
    "io.papermc.paper",
    "paper-api",
    "1.21.11-R0.1-SNAPSHOT"
).joinToString(":")

val forbiddenTransitionBridgeEntries = listOf(
    "net/enthusia/staff/persistence/JdbcSanctionMutationStore.class",
    "net/enthusia/staff/persistence/JdbcModerationStore.class",
    "net/enthusia/staff/persistence/migration/LiteBansMigrationService.class"
)

val requiredTransitionBridgeEntries = listOf(
    "net/enthusia/staff/persistence/TransitionDataRuntime.class",
    "net/enthusia/staff/domain/application/DiscordSrvMigrationService.class",
    "org/flywaydb/core/Flyway.class",
    "org/mariadb/jdbc/Driver.class"
)

val requiredTransitionMigrationEntries = project(":persistence")
    .fileTree("src/main/resources/db/migration") {
        include("V*.sql")
    }
    .files
    .map { "db/migration/${it.name}" }
    .sorted()

dependencies {
    implementation(project(":domain"))
    implementation(project(":persistence"))
    implementation(project(":protocol"))
    compileOnly(paperApiCoordinate)
    compileOnly("net.luckperms:api:5.4")
    testImplementation(paperApiCoordinate)
    testImplementation("net.luckperms:api:5.4")
}

tasks.processResources {
    val resolvedPluginVersion = project.version.toString()
    inputs.property("pluginVersion", resolvedPluginVersion)
    filesMatching("plugin.yml") {
        expand("version" to resolvedPluginVersion)
    }
}

tasks.jar {
    enabled = false
}

tasks.shadowJar {
    archiveBaseName.set("EnthusiaStaff-AuthorityBridge")
    archiveClassifier.set("")
    mergeServiceFiles()
    minimize {
        exclude(dependency("org.flywaydb:.*:.*"))
        exclude(dependency("org.mariadb.jdbc:.*:.*"))
        exclude(dependency("com.zaxxer:HikariCP:.*"))
        exclude(dependency("org.slf4j:slf4j-api:.*"))
    }
}

val verifyTransitionBridgeRuntime by tasks.registering(VerifyTransitionBridgeRuntime::class) {
    group = "verification"
    description = "Verifies required and forbidden entries in the shaded transition bridge runtime."
    dependsOn(tasks.shadowJar)
    runtimeJar.set(tasks.shadowJar.flatMap { it.archiveFile })
    forbiddenEntries.set(forbiddenTransitionBridgeEntries)
    requiredEntries.set(requiredTransitionBridgeEntries)
    requiredMigrationEntries.set(requiredTransitionMigrationEntries)
}

tasks.assemble {
    dependsOn(verifyTransitionBridgeRuntime)
}

tasks.check {
    dependsOn(verifyTransitionBridgeRuntime)
}
