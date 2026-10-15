plugins {
    id("com.android.application")
    kotlin("android")
}
android {
    namespace = "com.attributehq.validation"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.attributehq.validation"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "validation"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions { jvmTarget = "1.8" }
}
dependencies { implementation("com.attributehq:sdk:1.1.0-beta.3") }
