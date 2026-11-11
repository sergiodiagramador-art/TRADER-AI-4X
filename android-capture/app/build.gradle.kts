plugins {
    id("com.android.application")
}

dependencies {
    implementation("androidx.core:core:1.15.0")
    testImplementation("junit:junit:4.13.2")
}

android {
    namespace = "com.traderai4x.capture"
    compileSdk = 35
    testOptions {
        unitTests.isIncludeAndroidResources = false
    }

    defaultConfig {
        applicationId = "com.traderai4x.capture"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}
