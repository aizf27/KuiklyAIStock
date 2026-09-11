import java.util.Properties

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}
val deepSeekApiKey = localProperties.getProperty("DEEPSEEK_API_KEY", "")
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.example.kuiklyaistock"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.example.kuiklyaistock"
        minSdk = 23
        targetSdk = 30
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "DEEPSEEK_API_KEY", "\"\"")
        multiDexEnabled = true
    }

    buildTypes {
        getByName("debug") {
            buildConfigField("String", "DEEPSEEK_API_KEY", "\"$deepSeekApiKey\"")
        }
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(project(":shared"))

    implementation("androidx.recyclerview:recyclerview:1.2.1")
    implementation("androidx.appcompat:appcompat:1.3.1")
    implementation("androidx.multidex:multidex:2.0.1")

    implementation("com.squareup.picasso:picasso:2.71828")

    implementation("androidx.core:core-ktx:1.6.0")
    implementation("androidx.dynamicanimation:dynamicanimation:1.0.0")
    implementation("com.github.bumptech.glide:glide:4.12.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.12.0")
}