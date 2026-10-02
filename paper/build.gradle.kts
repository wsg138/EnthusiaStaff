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

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":persistence"))
    implementation(project(":protocol"))
    compileOnly(integrationContractsProject)
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.20.1")
    compileOnly("io.papermc.paper:paper-api:26.2.build.129-stable")
    compileOnly("net.dmulloy2:ProtocolLib:5.4.0")
    compileOnly("net.luckperms:api:5.4")
    testImplementation(integrationContractsProject)
    testRuntimeOnly(files(integrationContractMainOutput))
    testImplementation("io.papermc.paper:paper-api:26.2.build.129-stable")
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


/*
 * Disposable TEMP Paper 26.3 compatibility proof.
 *
 * This exists only on validation/temp-263-runtime-proof-8f2c9fbd. It is
 * intentionally wired into check so the repository's existing trusted Java 25
 * Coverage workflow executes it without changing canonical CI or product code.
 */
val temp263ProofClasses = layout.buildDirectory.dir("classes/java/temp263Proof")

val compileTemp263Proof by tasks.registering(JavaCompile::class) {
    source(rootProject.fileTree("validation/temp-263-proof/src/main/java") {
        include("**/*.java")
    })
    classpath = project.extensions.getByType<SourceSetContainer>().named("main").get().compileClasspath
    destinationDirectory.set(temp263ProofClasses)
    options.release.set(25)
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

val temp263ProofJar by tasks.registering(Jar::class) {
    dependsOn(compileTemp263Proof)
    archiveFileName.set("Temp263Proof.jar")
    destinationDirectory.set(layout.buildDirectory.dir("temp263-proof"))
    from(temp263ProofClasses)
    from(rootProject.file("validation/temp-263-proof/src/main/resources"))
}

val temp263RuntimeProof by tasks.registering(Exec::class) {
    dependsOn(tasks.shadowJar, temp263ProofJar)
    workingDir(rootProject.projectDir)
    commandLine(
        "bash",
        rootProject.file("validation/temp-263-proof/run-proof.sh").absolutePath,
        temp263ProofJar.flatMap { it.archiveFile }.get().asFile.absolutePath
    )
}

tasks.named("check") {
    dependsOn(temp263RuntimeProof)
}
