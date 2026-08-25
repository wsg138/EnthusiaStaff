plugins {
    application
    id("com.gradleup.shadow")
}

dependencies {
    implementation("net.dv8tion:JDA:6.5.0") {
        exclude(module = "opus-java")
        exclude(module = "tink")
    }
    runtimeOnly("org.slf4j:slf4j-jdk14:2.0.17")
}

application {
    mainClass.set("net.enthusia.staff.discordbot.StaffBotApplication")
}

tasks.jar {
    enabled = false
}

tasks.shadowJar {
    archiveBaseName.set("EnthusiaStaff-StaffBot")
    archiveClassifier.set("")
    mergeServiceFiles()
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}
