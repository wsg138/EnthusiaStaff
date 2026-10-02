pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

rootProject.name = "EnthusiaStaff"

include(
    "common",
    "discord-platform-api",
    "domain",
    "integration-contracts",
    "persistence",
    "protocol",
    "paper",
    "paper-authority-bridge",
    "velocity",
    "staff-bot",
    "integration-tests",
)
