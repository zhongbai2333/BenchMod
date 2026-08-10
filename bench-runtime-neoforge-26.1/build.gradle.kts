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

val smokeBench by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += output + compileClasspath + sourceSets.main.get().runtimeClasspath
}

dependencies {
    api(project(":bench-api-core"))
    api(project(":bench-api-neoforge-26.1"))
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    add(smokeBench.implementationConfigurationName, project(":bench-api-core"))
    add(smokeBench.implementationConfigurationName, project(":bench-api-neoforge-26.1"))
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.encoding = "UTF-8"
}

// mods.toml declares version="${file.jarVersion}", which FML reads from this manifest attribute.
tasks.jar {
    manifest.attributes("Implementation-Version" to project.version)
}

tasks.test { useJUnitPlatform() }

publishing {
    publications {
        create<MavenPublication>("mavenJava") { from(components["java"]) }
    }
}

neoForge {
    version = rootProject.property("neoForgeVersion").toString()
    addModdingDependenciesTo(smokeBench)
    mods {
        create("modbench_runtime") {
            sourceSet(sourceSets.main.get())
            sourceSet(smokeBench)
        }
    }
    unitTest {
        enable()
        testedMod = mods.getByName("modbench_runtime")
    }
    runs {
        create("benchServer") {
            server()
            sourceSet = smokeBench
            gameDirectory = layout.buildDirectory.dir("modBench/runs/smoke/server")
            programArgument("--nogui")
            systemProperty("modBench.resultDirectory",
                    layout.buildDirectory.dir("modBench/raw-results/smoke/server").get().asFile.absolutePath)
            systemProperty("modBench.expectedProviderCount", "1")
            systemProperty("modBench.seed", "1")
            systemProperty("modBench.phaseTimeoutTicks", "200")
            systemProperty("modBench.targetMod", "modbench_runtime")
        }
    }
}