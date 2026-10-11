import org.gradle.api.tasks.SourceSetContainer

plugins {
    id("com.gradleup.shadow")
}

val integrationContractsProject = project(":integration-contracts")
val integrationContractMainOutput = integrationContractsProject
    .extensions
    .getByType<SourceSetContainer>()
    .named("main")
    .get()
    .output

dependencies {
    implementation(project(":domain"))
    implementation(project(":persistence"))
    implementation(project(":protocol"))
    implementation(project(":discord-platform-api"))
    implementation(project(":moderation-platform-api"))
    compileOnly(integrationContractsProject)
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.20.1")
    compileOnly("io.papermc.paper:paper-api:26.2.build.122-stable")
    compileOnly("net.dmulloy2:ProtocolLib:5.4.0")
    compileOnly("net.luckperms:api:5.4")
    compileOnly("top.polar:api:2.0.0")
    testImplementation(integrationContractsProject)
    testRuntimeOnly(files(integrationContractMainOutput))
    testImplementation("io.papermc.paper:paper-api:26.2.build.122-stable")
    testImplementation("net.dmulloy2:ProtocolLib:5.4.0")
    testImplementation("net.luckperms:api:5.4")
}

// Paper 26.2 publishes Java 25-only API variants; this deployable module
// must compile with the same target without changing the network's other modules.
tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
}


// Compile the bridge implementation into Paper without the standalone plugin descriptor.
sourceSets {
    named("main") {
        java.srcDir(project(":paper-authority-bridge").file("src/main/java"))
        java.exclude("**/EnthusiaStaffAuthorityBridgePlugin.java")
    }
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
    archiveBaseName.set("EnthusiaStaff-Paper")
    archiveClassifier.set("")
    mergeServiceFiles()
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}
