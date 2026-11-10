plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android { namespace = "com.meizzo.controller"; compileSdk = 35
    defaultConfig {
        manifestPlaceholders["allowCleartext"] = false
    }
    buildTypes {
        getByName("debug") {
            manifestPlaceholders["allowCleartext"] = true
            buildConfigField("String", "SIGNALING_URL", "\"${providers.gradleProperty("meizzo.debugSignalingUrl").orElse("ws://10.0.2.2:8080").get()}\"")
        }
        getByName("release") {
            buildConfigField("String", "SIGNALING_URL", "\"${providers.gradleProperty("meizzo.releaseSignalingUrl").orElse("wss://configure-your-meizzo-endpoint.invalid").get()}\"")
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
