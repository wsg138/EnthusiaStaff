plugins {
    `java-library`
}

dependencies {
    testImplementation(project(":domain"))
    testImplementation(project(":persistence"))
    testImplementation(project(":protocol"))
    testImplementation("com.zaxxer:HikariCP:6.3.3")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.20.1")
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.21.3"))
    testImplementation("org.testcontainers:mariadb")
    testImplementation("org.testcontainers:junit-jupiter")
}
