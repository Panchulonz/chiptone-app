plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "cl.conclusa.chiptone"
    compileSdk = 34

    defaultConfig {
        applicationId = "cl.conclusa.chiptone"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "0.2.0"
    }

    signingConfigs {
        // Llave de desarrollo para instalar por APK (NO es la llave de subida a Google Play)
        create("dev") {
            storeFile = file("dev.keystore")
            storePassword = "chiptone"
            keyAlias = "dev"
            keyPassword = "chiptone"
        }
    }

    buildFeatures { buildConfig = true }

    flavorDimensions += "edition"
    productFlavors {
        create("owner") {
            dimension = "edition"
            applicationIdSuffix = ".owner"
            versionNameSuffix = "-owner"
            buildConfigField("boolean", "PRO", "true")
            resValue("string", "app_name", "Chiptone Owner")
        }
        create("play") {
            dimension = "edition"
            buildConfigField("boolean", "PRO", "false")
            resValue("string", "app_name", "Chiptone")
        }
    }

    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("dev") }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("dev")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
