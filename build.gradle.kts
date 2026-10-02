import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.bundling.Jar
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    base
    jacoco
    id("com.gradleup.shadow") version "8.3.11" apply false
}

group = "net.enthusia.staff"
version = providers.gradleProperty("releaseVersion").orElse("0.1.0-SNAPSHOT").get()

allprojects {
    group = rootProject.group
    version = rootProject.version

    providers.gradleProperty("enthusiaBuildRoot").orNull?.let { externalRoot ->
        layout.buildDirectory.set(file("$externalRoot/${project.name}"))
    }
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "jacoco")

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(25))
        withSourcesJar()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(21)
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("failed", "skipped")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }

    dependencies {
        "testImplementation"(platform("org.junit:junit-bom:5.13.4"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    extensions.configure<JacocoPluginExtension> {
        toolVersion = "0.8.13"
    }
}

val productionProjects = subprojects.filterNot { it.name == "integration-tests" }

/**
 * Combines unit-test and Testcontainers execution data into repository-wide
 * XML and HTML reports for CI artifacts and external coverage reporting.
 */
tasks.register<JacocoReport>("jacocoAggregateReport") {
    group = "verification"
    description = "Generates one repository-wide JaCoCo XML and HTML report."

    dependsOn(subprojects.map { "${it.path}:test" })

    val mainSourceSets = productionProjects.map { project ->
        project.extensions.getByType<SourceSetContainer>().named("main").get()
    }

    executionData.setFrom(
        files(subprojects.map { it.layout.buildDirectory.file("jacoco/test.exec") })
    )
    sourceDirectories.setFrom(files(mainSourceSets.map { it.allSource.srcDirs }))
    classDirectories.setFrom(files(mainSourceSets.map { it.output.classesDirs }))

    reports {
        xml.required.set(true)
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/aggregate/jacoco.xml"))
        html.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/aggregate/html"))
        csv.required.set(false)
    }
}

tasks.register("runtimeJars") {
    group = "build"
    description = "Builds the deployable Paper, authority-bridge, Velocity, and staff-bot runtime jars."
    dependsOn(
        ":paper:shadowJar",
        ":paper-authority-bridge:verifyTransitionBridgeRuntime",
        ":velocity:shadowJar",
        ":staff-bot:shadowJar"
    )
}


/*
 * Disposable reopening proof only.
 *
 * This branch is intentionally isolated from canonical PR #280. The task builds
 * the frozen Paper runtime first, then compiles a separate proof plugin and runs
 * exact Paper 26.3 build 134 on loopback with a real 26.3 protocol client.
 */
val temp263ProofClasses = layout.buildDirectory.dir("temp263-proof/classes")

val compileTemp263Proof by tasks.registering(JavaCompile::class) {
    source(fileTree("validation/temp-263-proof/src/main/java") { include("**/*.java") })
    classpath = project(":paper").configurations.getByName("compileClasspath")
    destinationDirectory.set(temp263ProofClasses)
    options.encoding = "UTF-8"
    sourceCompatibility = "25"
    targetCompatibility = "25"
    options.release.set(25)
}

val temp263ProofJar by tasks.registering(Jar::class) {
    dependsOn(compileTemp263Proof)
    archiveFileName.set("Temp263Proof.jar")
    destinationDirectory.set(layout.buildDirectory.dir("temp263-proof"))
    from(temp263ProofClasses)
    from("validation/temp-263-proof/src/main/resources")
}

val temp263RuntimeProof by tasks.registering(Exec::class) {
    group = "verification"
    description = "Proves the frozen vanish reflection seam on exact TEMP Paper 26.3 build 134."
    dependsOn(":paper:shadowJar", temp263ProofJar)
    doFirst {
        val staffJars = fileTree("paper/build/libs") {
            include("EnthusiaStaff-Paper-*.jar")
            exclude("*-sources.jar")
        }.files.sortedBy { it.name }
        require(staffJars.size == 1) {
            "Expected exactly one frozen EnthusiaStaff Paper JAR, found: " +
                    staffJars.joinToString { it.name }
        }
        commandLine(
            "bash",
            "validation/temp-263-proof/run-ci-proof.sh",
            staffJars.single().absolutePath,
            temp263ProofJar.get().archiveFile.get().asFile.absolutePath
        )
    }
    outputs.dir(layout.buildDirectory.dir("reports/runtime-jars/temp263-proof"))
}

tasks.named("check") {
    dependsOn(temp263RuntimeProof)
}
