plugins {
    base
    jacoco
    id("com.gradleup.shadow") version "8.3.6" apply false
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
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
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

tasks.register("runtimeJars") {
    group = "build"
    description = "Builds the only two deployable Minecraft plugin jars."
    dependsOn(":paper:shadowJar", ":velocity:shadowJar")
}
