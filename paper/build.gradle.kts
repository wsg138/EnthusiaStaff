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
    compileOnly(integrationContractsProject)
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.20.1")
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("net.dmulloy2:ProtocolLib:5.4.0")
    compileOnly("net.luckperms:api:5.4")
    testImplementation(integrationContractsProject)
    testRuntimeOnly(files(integrationContractMainOutput))
    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation("net.dmulloy2:ProtocolLib:5.4.0")
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
    archiveBaseName.set("EnthusiaStaff-Paper")
    archiveClassifier.set("")
    mergeServiceFiles()
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}
