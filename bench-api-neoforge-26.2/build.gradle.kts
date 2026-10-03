plugins {
    `java-library`
    `maven-publish`
    id("net.neoforged.moddev")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    api(project(":bench-api-core"))
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.encoding = "UTF-8"
}

tasks.test { useJUnitPlatform() }

// Minecraft-bound API signatures must resolve in FML's transformed game loader, not the app loader.
// Core API remains a platform-neutral ordinary Java library.
tasks.jar { manifest.attributes("FMLModType" to "GAMELIBRARY") }


publishing {
    publications {
        create<MavenPublication>("mavenJava") { from(components["java"]) }
    }
}

neoForge {
    enable {
        version = rootProject.property("neoForgeVersion").toString()
        // Public ModDev binary-artifact path for memory-constrained verification workers.
        isDisableRecompilation = providers.gradleProperty("modBenchBinaryArtifacts")
            .map(String::toBoolean).getOrElse(false)
    }
}