plugins {
    id("com.android.application")
}

android {
    namespace = "com.arenacommunity.control"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.arenacommunity.control"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "2.4.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}