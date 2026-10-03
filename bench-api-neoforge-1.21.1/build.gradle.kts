plugins {
    `java-library`
    `maven-publish`
    id("net.neoforged.moddev")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
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
    options.release = 21
    options.encoding = "UTF-8"
}

tasks.test { useJUnitPlatform() }

// Minecraft-bound API signatures must resolve through FML's transformed game loader.
// The core remains platform-neutral and has no Minecraft/NeoForge dependency.
tasks.jar { manifest.attributes("FMLModType" to "GAMELIBRARY") }

publishing {
    publications {
        create<MavenPublication>("mavenJava") { from(components["java"]) }
    }
}

neoForge {
    enable {
        version = rootProject.property("neoForgeVersion").toString()
        // Official patched-binary path for memory-constrained build workers.
        isDisableRecompilation = providers.gradleProperty("modBenchBinaryArtifacts")
            .map(String::toBoolean).getOrElse(false)
    }
}