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
        applicationId = "com.provoicechanger"
        minSdk = 26
        versionCode = 3
        versionName = "0.3.0"

        targetSdk = 35
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation("androidx.annotation:annotation-jvm:1.10.0")
}
