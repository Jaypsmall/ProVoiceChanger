import java.util.Properties

plugins {
    id("com.android.application")
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

fun String.asBuildConfigString(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val geminiApiKey = (
        localProperties.getProperty("GEMINI_API_KEY")
            ?: System.getenv("GEMINI_API_KEY")
            ?: ""
        ).trim()

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
        versionName = "1.0.2"

        targetSdk = 35

        buildConfigField("String", "GEMINI_API_KEY", geminiApiKey.asBuildConfigString())
        buildConfigField("String", "GEMINI_MODEL", "gemini-3.8-flash".asBuildConfigString())
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation("androidx.annotation:annotation-jvm:1.10.0")
    implementation("androidx.core:core:1.13.1")
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
}
