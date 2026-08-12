plugins {
    `java-gradle-plugin`
    `maven-publish`
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
    withSourcesJar()
    withJavadocJar()
}

// ModDev must share the TestKit-injected classpath: fixtures that apply both plugins would
// otherwise load ModDev in a child classloader our classes cannot link against.
val modDevForTestKit: Configuration by configurations.creating

dependencies {
    compileOnly("net.neoforged.moddev:net.neoforged.moddev.gradle.plugin:${providers.gradleProperty("modDevVersion").orElse("2.0.141").get()}")
    modDevForTestKit("net.neoforged.moddev:net.neoforged.moddev.gradle.plugin:${providers.gradleProperty("modDevVersion").orElse("2.0.141").get()}")
    implementation(project(":bench-report-schema"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.19.2")
    implementation("com.networknt:json-schema-validator:1.5.9")
    testImplementation(gradleApi())
    testImplementation(gradleTestKit())
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.pluginUnderTestMetadata {
    pluginClasspath.from(modDevForTestKit)
}

// The plugin injects its own coordinates as bench dependencies, so it must know its version.
tasks.processResources {
    // Local String capture keeps the filesMatching action configuration-cache serializable.
    val modBenchGroup = project.group.toString()
    val modBenchVersion = project.version.toString()
    inputs.property("modBenchGroup", modBenchGroup)
    inputs.property("modBenchVersion", modBenchVersion)
    filesMatching("com/zhongbai233/bench/gradle/modbench.properties") {
        expand("group" to modBenchGroup, "version" to modBenchVersion)
    }
}

gradlePlugin {
    plugins {
        create("modBench") {
            id = "com.zhongbai233.minecraft-bench"
            implementationClass = "com.zhongbai233.bench.gradle.ModBenchPlugin"
            displayName = "ModBench Gradle Plugin"
            description = "Configures isolated benchmark source sets and NeoForge server runs."
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.encoding = "UTF-8"
}

tasks.test { useJUnitPlatform() }
