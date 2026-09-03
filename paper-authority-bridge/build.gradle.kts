plugins {
    id("com.gradleup.shadow")
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":protocol"))
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("net.luckperms:api:5.4")
    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
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
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}
