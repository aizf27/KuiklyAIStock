pluginManagement {
    buildscript {
        repositories {
            google()
            mavenCentral()
            maven {
                url = uri("https://mirrors.tencent.com/nexus/repository/maven-tencent/")
            }
        }
        dependencies {
            // 兼容 Kotlin 2.1，替换 AGP 7.4.2 内置的旧版 D8/R8。
            classpath("com.android.tools:r8:8.6.17")
        }
    }
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
        mavenLocal()
        maven {
            url = uri("https://mirrors.tencent.com/nexus/repository/maven-tencent/")
        }
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        mavenLocal()
        maven {
            url = uri("https://mirrors.tencent.com/nexus/repository/maven-tencent/")
        }
    }
}

rootProject.name = "KuiklyAIStock"
include(":androidApp")
include(":shared")
include(":KuiklyChart")
project(":KuiklyChart").projectDir = file("third_party/KuiklyChart")
// 模板可能未包含可选宿主，存在时才加入构建。
if (file("h5App").isDirectory) include(":h5App")
if (file("miniApp").isDirectory) include(":miniApp")
