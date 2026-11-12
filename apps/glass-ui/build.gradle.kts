plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.meizzo.glass"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api("org.jetbrains.compose.runtime:runtime:1.12.0")
    api("org.jetbrains.compose.foundation:foundation:1.12.0")
    api("org.jetbrains.compose.ui:ui:1.12.0")
    api("io.github.kyant0:backdrop:2.0.1")
    api("io.github.kyant0:shapes:1.2.1")
}
