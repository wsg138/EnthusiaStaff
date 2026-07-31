plugins {
    `java-library`
}

dependencies {
    testImplementation(project(":domain"))
    testImplementation(project(":persistence"))
    testImplementation(project(":protocol"))
    testImplementation("com.zaxxer:HikariCP:6.3.3")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.20.1")
    testImplementation("org.flywaydb:flyway-core:12.8.1")
    testImplementation("org.flywaydb:flyway-mysql:12.8.1")
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.21.4"))
    testImplementation("org.testcontainers:mariadb")
    testImplementation("org.testcontainers:junit-jupiter")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.17")
}

tasks.named<Test>("test") {
    dependsOn(":paper:shadowJar")
    doFirst {
        val runtimeDirectory = project(":paper").layout.buildDirectory.dir("libs").get().asFile
        val runtimeJars = checkNotNull(runtimeDirectory.listFiles { file ->
            file.isFile
                    && file.name.startsWith("EnthusiaStaff-Paper-")
                    && file.name.endsWith(".jar")
                    && !file.name.endsWith("-sources.jar")
        }).toList()
        require(runtimeJars.size == 1) {
            "Expected exactly one Paper runtime jar, found: ${runtimeJars.joinToString { it.name }}"
        }
        systemProperty("enthusia.paperRuntimeJar", runtimeJars.single().absolutePath)
    }
}
