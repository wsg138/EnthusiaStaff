plugins {
    application
}
dependencies {
    implementation(project(":domain"))
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.20.1")
}
application {
    mainClass.set("net.enthusia.staff.simulator.SimulatorMain")
}
// The Paper runtime targets Java 25. Compile its production policy loader
// from its exact sources here, at Java 21 bytecode, to support users on JRE 21
// without bundling a Java-25 Paper JAR into this standalone utility.
tasks.named<JavaCompile>("compileJava") {
    source(files(
        project(":paper").file("src/main/java/net/enthusia/staff/paper/config/ReasonPolicyConfigurationLoader.java"),
        project(":paper").file("src/main/java/net/enthusia/staff/paper/config/ConfigurationValidationException.java")
    ))
}
tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}
