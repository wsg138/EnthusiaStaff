plugins {
    `java-library`
}

dependencies {
    api(project(":domain"))

    implementation("com.zaxxer:HikariCP:6.3.3")
    implementation("org.flywaydb:flyway-core:12.8.1")
    implementation("org.flywaydb:flyway-mysql:12.8.1")
    runtimeOnly("org.mariadb.jdbc:mariadb-java-client:3.5.8")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.20.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.20.1")
    implementation("org.slf4j:slf4j-api:2.0.17")
}
