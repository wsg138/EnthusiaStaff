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
    "db/migration/V19__discord_moderation_persistence.sql",
    "org/flywaydb/core/Flyway.class",
    "org/mariadb/jdbc/Driver.class"
)

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
    doLast {
        java.util.zip.ZipFile(archiveFile.get().asFile).use { archive ->
            forbiddenTransitionBridgeEntries.forEach { entry ->
                check(archive.getEntry(entry) == null) { "Transition bridge contains forbidden runtime class: $entry" }
            }
            requiredTransitionBridgeEntries.forEach { entry ->
                check(archive.getEntry(entry) != null) { "Transition bridge is missing required runtime entry: $entry" }
            }
        }
    }
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}
