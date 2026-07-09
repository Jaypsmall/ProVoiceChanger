plugins {
    id("com.android.application")
}

android {
    namespace = "com.provoicechanger"

    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.provoicechanger.beta"
        minSdk = 26
        versionCode = 2
        versionName = "0.2.0-BETA"

        targetSdk {
            version = release(26)
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
