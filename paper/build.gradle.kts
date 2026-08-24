plugins {
    id("com.gradleup.shadow")
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":persistence"))
    implementation(project(":protocol"))
    compileOnly(project(":integration-contracts"))
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.20.1")
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("net.dmulloy2:ProtocolLib:5.4.0")
    testImplementation(project(":integration-contracts"))
    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation("net.dmulloy2:ProtocolLib:5.4.0")
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
