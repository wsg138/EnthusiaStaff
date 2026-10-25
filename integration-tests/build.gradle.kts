plugins {
    `java-library`
}

dependencies {
    testImplementation(project(":domain"))
    testImplementation(project(":persistence"))
    testImplementation(project(":protocol"))
    testImplementation(project(":velocity"))
    // Velocity disables its plain jar; expose compiled production classes to JUnit at runtime.
    testRuntimeOnly(files(rootProject.project(":velocity").layout.buildDirectory.dir("classes/java/main")))
    testImplementation("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
    testImplementation("com.zaxxer:HikariCP:6.3.3")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.20.1")
    testImplementation("org.flywaydb:flyway-core:12.8.1")
    testImplementation("org.flywaydb:flyway-mysql:12.8.1")
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.21.4"))
    testImplementation("org.testcontainers:mariadb")
    testImplementation("org.testcontainers:junit-jupiter")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.17")
}

// Resolve the exact shaded artifact at configuration time. The old doFirst
// closure called project(":paper") at execution time, which breaks Gradle's
// configuration cache even after all MariaDB integration tests pass.
val paperRuntimeJar = project(":paper").layout.buildDirectory.file(
    "libs/EnthusiaStaff-Paper-" + project.version + ".jar"
)

tasks.named<Test>("test") {
    dependsOn(":paper:shadowJar")
    // Packaging tests must rerun when the shaded JAR content changes.
    inputs.file(paperRuntimeJar).withPropertyName("paperRuntimeJar")
    // A fixed versioned path avoids accidentally selecting an old shaded JAR
    // or a sources JAR when multiple release builds exist in build/libs.
    systemProperty("enthusia.paperRuntimeJar", paperRuntimeJar.get().asFile.absolutePath)
}
