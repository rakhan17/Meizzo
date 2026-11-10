plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseVersionName = providers.gradleProperty("meizzo.releaseVersionName")
    .orElse("0.1.0-beta.3").get()
val releaseVersionCode = providers.gradleProperty("meizzo.releaseVersionCode")
    .orElse("3").get().toInt()
val debugSignalingUrl = providers.gradleProperty("meizzo.debugSignalingUrl")
    .orElse("ws://10.0.2.2:8080").get()
val releaseSignalingUrl = providers.gradleProperty("meizzo.releaseSignalingUrl")
    .orElse("wss://configure-your-meizzo-endpoint.invalid").get()

android {
    namespace = "com.meizzo.controller"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.meizzo.controller"
        minSdk = 26
        targetSdk = 35
        versionCode = releaseVersionCode
        versionName = releaseVersionName
        manifestPlaceholders["allowCleartext"] = false
    }
    buildTypes {
        getByName("debug") {
            manifestPlaceholders["allowCleartext"] = true
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
