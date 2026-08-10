plugins {
    base
}

group = "com.zhongbai233.bench"
version = providers.gradleProperty("modBenchVersion").getOrElse("0.1.0-SNAPSHOT")

subprojects {
    group = rootProject.group
    version = rootProject.version
}