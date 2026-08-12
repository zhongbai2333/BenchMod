import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.testing.Test

plugins {
    base
}

val jitPackBuild = providers.environmentVariable("JITPACK")
    .map { it.equals("true", ignoreCase = true) }
    .getOrElse(false)
val jitPackGroup = providers.environmentVariable("GROUP")
    .zip(providers.environmentVariable("ARTIFACT")) { ownerGroup, repository -> "$ownerGroup.$repository" }

group = if (jitPackBuild) {
    jitPackGroup.get()
} else {
    providers.gradleProperty("modBenchGroup").getOrElse("com.zhongbai233.bench")
}
version = if (jitPackBuild) {
    providers.environmentVariable("VERSION").get()
} else {
    providers.gradleProperty("modBenchVersion").getOrElse("0.1.1")
}

val moduleDescriptions = mapOf(
    "bench-api-core" to "Platform-neutral ModBench provider, scenario, metric, camera, and GUI selector APIs.",
    "bench-report-schema" to "Versioned JSON schemas and examples for ModBench reports.",
    "bench-api-neoforge-26.1" to "NeoForge 26.1 server and client benchmark provider APIs.",
    "bench-runtime-neoforge-26.1" to "NeoForge 26.1 runtime mod that executes ModBench scenarios and writes reports.",
    "bench-gradle-plugin" to "Gradle plugin that isolates benchmark sources and configures NeoForge benchmark runs.",
)

subprojects {
    group = rootProject.group
    version = rootProject.version

    plugins.withId("maven-publish") {
        extensions.configure<PublishingExtension> {
            publications.withType(MavenPublication::class.java).configureEach {
                pom {
                    name.set("ModBench ${project.name}")
                    description.set(moduleDescriptions[project.name] ?: "A ModBench module.")
                    url.set("https://github.com/zhongbai2333/BenchMod")
                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://opensource.org/license/mit")
                        }
                    }
                    developers {
                        developer {
                            id.set("zhongbai2333")
                            name.set("zhongbai233")
                            url.set("https://github.com/zhongbai2333")
                        }
                    }
                    scm {
                        connection.set("scm:git:https://github.com/zhongbai2333/BenchMod.git")
                        developerConnection.set("scm:git:ssh://git@github.com/zhongbai2333/BenchMod.git")
                        url.set("https://github.com/zhongbai2333/BenchMod")
                    }
                }
            }
        }
    }

    // Cloud-synced macOS workspaces can transiently preserve conflict copies such as
    // "ExampleTest 2.class" while Gradle replaces an output. They are neither Java classes nor
    // release content, so keep test discovery and publications deterministic if one appears.
    tasks.withType<Test>().configureEach { exclude("**/* *.class") }
    tasks.withType<Jar>().configureEach { exclude("**/* *.class") }
}

val publishedModules = listOf(
    "bench-api-core",
    "bench-report-schema",
    "bench-api-neoforge-26.1",
    "bench-runtime-neoforge-26.1",
    "bench-gradle-plugin",
)

tasks.named("check") {
    dependsOn(subprojects.map { "${it.path}:check" })
}

tasks.register("verifyReleaseReadiness") {
    group = "verification"
    description = "Builds, tests, and publishes the five public JitPack modules to Maven Local."
    dependsOn(publishedModules.map { ":$it:check" })
    dependsOn(publishedModules.map { ":$it:publishToMavenLocal" })
}
