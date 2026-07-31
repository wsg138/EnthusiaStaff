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
