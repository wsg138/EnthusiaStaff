import org.gradle.api.tasks.SourceSetContainer
plugins { application }
val paperMainOutput = project(":paper").extensions.getByType<SourceSetContainer>().named("main").get().output
dependencies {
    implementation(project(":domain"))
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.20.1")
    compileOnly(files(paperMainOutput))
    testRuntimeOnly(files(paperMainOutput))
}
application { mainClass.set("net.enthusia.staff.simulator.SimulatorMain") }
tasks.withType<JavaCompile>().configureEach { options.release.set(25) }
// Include ONLY the production policy YAML parser, not any Paper server runtime code.
tasks.named<Jar>("jar") {
    from(paperMainOutput) {
        include("net/enthusia/staff/paper/config/ReasonPolicyConfigurationLoader*.class")
        include("net/enthusia/staff/paper/config/ConfigurationValidationException.class")
    }
}
