plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android { namespace = "com.meizzo.client"; compileSdk = 35
    defaultConfig {
        applicationId = "com.meizzo.client"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "0.1.0"
        buildConfigField("String", "SIGNALING_URL", "\"ws://10.0.2.2:8080\"")
        manifestPlaceholders["allowCleartext"] = false
    }
    buildTypes { getByName("debug") { manifestPlaceholders["allowCleartext"] = true } }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
