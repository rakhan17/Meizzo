plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseVersionName = providers.gradleProperty("meizzo.releaseVersionName")
    .orElse("2.0.1-beta.1").get()
val releaseVersionCode = providers.gradleProperty("meizzo.releaseVersionCode")
    .orElse("20001301").get().toInt()
val defaultSignalingUrl = "wss://meizzo-signaling.rakhanatanox.workers.dev"
val debugSignalingUrl = providers.gradleProperty("meizzo.debugSignalingUrl")
    .orElse(defaultSignalingUrl).get()
val releaseSignalingUrl = providers.gradleProperty("meizzo.releaseSignalingUrl")
    .orElse(defaultSignalingUrl).get()

android {
    namespace = "com.meizzo.client"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.meizzo.client"
        minSdk = 26
        targetSdk = 35
        versionCode = releaseVersionCode
        versionName = releaseVersionName
        manifestPlaceholders["allowCleartext"] = false
    }
    buildTypes {
        getByName("debug") {
            manifestPlaceholders["allowCleartext"] = false
            buildConfigField("String", "SIGNALING_URL", "\"$debugSignalingUrl\"")
        }
        getByName("release") {
            buildConfigField("String", "SIGNALING_URL", "\"$releaseSignalingUrl\"")
        }
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core:1.15.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
