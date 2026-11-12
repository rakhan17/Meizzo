plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseVersionName = providers.gradleProperty("meizzo.releaseVersionName")
    .orElse("3.0.0-beta.1").get()
val releaseVersionCode = providers.gradleProperty("meizzo.releaseVersionCode")
    .orElse("30300001").get().toInt()
val defaultSignalingUrl = "wss://meizzo-signaling.rakhanatanox.workers.dev"
val debugSignalingUrl = providers.gradleProperty("meizzo.debugSignalingUrl")
    .orElse(defaultSignalingUrl).get()
val releaseSignalingUrl = providers.gradleProperty("meizzo.releaseSignalingUrl")
    .orElse(defaultSignalingUrl).get()

android {
    namespace = "com.meizzo.client"
    compileSdk = 37
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
    buildFeatures {
        buildConfig = true
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":apps:glass-ui"))
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core:1.15.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.github.webrtc-sdk:android:144.7559.15")
}
