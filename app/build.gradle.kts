// Locally hardened: platform Java UI, no third-party runtime dependencies.
plugins { id("com.android.application") }

android {
    namespace = "com.btmicfix"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.btmicfix.local"
        minSdk = 31
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0-local-hardened"
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        // Pinned SDK is intentional for a reproducible local test artifact.
        disable += "OldTargetApi"
        warningsAsErrors = true
    }
}

// No dependencies: all application classes use the Android/Java platform.
