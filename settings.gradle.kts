pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.neoforged.net/releases")
    }
}

plugins {
    id("net.neoforged.moddev.repositories") version "2.0.141"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven("https://maven.neoforged.net/releases")
        gradlePluginPortal()
    }
}

rootProject.name = "mod-bench"

include(
    "bench-api-core",
    "bench-report-schema",
    "bench-api-neoforge-26.1",
    "bench-runtime-neoforge-26.1",
    "bench-gradle-plugin",
    "bench-network-core",
    "bench-network-proxy",
)