pluginManagement {
    repositories {
        // ModBench artifacts come from the local Maven repository until a remote one is published.
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.neoforged.net/releases")
    }
    plugins {
        id("com.zhongbai233.minecraft-bench") version providers.gradleProperty("modbench_version").get()
    }
}

plugins {
    id("net.neoforged.moddev.repositories") version "2.0.141"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        mavenCentral()
        maven("https://maven.neoforged.net/releases")
        gradlePluginPortal()
    }
}

rootProject.name = "simple-neoforge-mod"