plugins {
    id("com.gradleup.shadow")
}

val velocityApi = "com.velocitypowered:velocity-api:3.4.0-SNAPSHOT"
val miniPlaceholdersApi = "io.github.miniplaceholders:miniplaceholders-api:3.0.1"

dependencies {
    implementation(project(":domain"))
    implementation(project(":persistence"))
    implementation(project(":protocol"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.20.1")
    compileOnly(velocityApi)
    compileOnly(miniPlaceholdersApi)
    compileOnly("net.luckperms:api:5.4")
    annotationProcessor(velocityApi)
    compileOnly("org.slf4j:slf4j-api:2.0.17")
    testImplementation(velocityApi)
    testImplementation(miniPlaceholdersApi)
    testImplementation("net.luckperms:api:5.4")
    testImplementation("org.slf4j:slf4j-api:2.0.17")
}

tasks.jar {
    enabled = false
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:-processing")
}

tasks.shadowJar {
    archiveBaseName.set("EnthusiaStaff-Velocity")
    archiveClassifier.set("")
    mergeServiceFiles()
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}
