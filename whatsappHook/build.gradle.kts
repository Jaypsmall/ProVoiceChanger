plugins {
    id("com.android.application")
}

android {
    namespace = "com.provoicechanger.whatsapphook"

    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.provoicechanger.whatsapphook"
        minSdk = 26
        versionCode = 1
        versionName = "0.1.0"

        targetSdk {
            version = release(26)
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    compileOnly(files("../libs/XposedBridgeApi-stub.jar"))
}
