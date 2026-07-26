plugins {
    `java-library`
}

dependencies {
    api(project(":domain"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.20.1")
}
