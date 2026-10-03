pluginManagement {
    val useLocalModBench = providers.gradleProperty("modBenchLocal")
        .map(String::toBoolean)
        .getOrElse(false)
    val modBenchVersion = providers.gradleProperty("modbench_version").get()
    val modDevVersion = providers.gradleProperty("moddev_version").get()

    repositories {
        if (useLocalModBench) {
            mavenLocal {
                content {
                    includeGroup("com.zhongbai233.bench")
                }
            }
        } else {
            maven("https://jitpack.io") {
                content {
                    includeGroup("com.github.zhongbai2333.BenchMod")
                }
            }
        }
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.neoforged.net/releases")
    }
    plugins {
        id("com.zhongbai233.minecraft-bench") version modBenchVersion
        id("net.neoforged.moddev.repositories") version modDevVersion
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.zhongbai233.minecraft-bench") {
                val group = if (useLocalModBench) {
                    "com.zhongbai233.bench"
                } else {
                    "com.github.zhongbai2333.BenchMod"
                }
                useModule("$group:bench-gradle-plugin:$modBenchVersion")
            }
        }
    }
}

plugins {
    id("net.neoforged.moddev.repositories")
}

dependencyResolutionManagement {
    val useLocalModBench = providers.gradleProperty("modBenchLocal")
        .map(String::toBoolean)
        .getOrElse(false)

    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (useLocalModBench) {
            mavenLocal {
                content {
                    includeGroup("com.zhongbai233.bench")
                }
            }
        } else {
            maven("https://jitpack.io") {
                content {
                    includeGroup("com.github.zhongbai2333.BenchMod")
                }
            }
        }
        mavenCentral()
        maven("https://maven.neoforged.net/releases")
        gradlePluginPortal()
    }
}

rootProject.name = "simple-neoforge-mod"
