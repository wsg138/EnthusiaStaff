plugins {
    id("com.gradleup.shadow")
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":persistence"))
    implementation(project(":protocol"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.20.1")
    compileOnly("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
    compileOnly("org.slf4j:slf4j-api:2.0.17")
    testImplementation("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
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
