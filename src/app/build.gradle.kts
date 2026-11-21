plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.appnest.store"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.appnest.store"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "2.0"

        buildConfigField(
            "String",
            "APP_URL",
            "\"https://ais-pre-zyrw3blegwj6srln4jhiuk-692468437334.asia-southeast1.run.app\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("com.google.android.material:material:1.12.0")
}
