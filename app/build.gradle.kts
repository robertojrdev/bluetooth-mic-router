// Modified from BTMicFix: platform Java UI and no third-party runtime dependencies.
plugins { id("com.android.application") }

android {
    namespace = "com.btmicfix"
    compileSdk = 35
    defaultConfig {
        // Override only for an update to the previous privately distributed test build.
        applicationId = providers.gradleProperty("localApplicationId")
            .getOrElse("io.github.robertojrdev.bluetoothmicrouter")
        minSdk = 31
        targetSdk = 35
        versionCode = 5
        versionName = if (providers.gradleProperty("localApplicationId").isPresent) "1.0.0-local-update" else "1.0.0"
    }
    val signingStore = System.getenv("BMR_SIGNING_STORE")
    if (!signingStore.isNullOrBlank()) {
        signingConfigs {
            create("localRelease") {
                storeFile = file(signingStore)
                storePassword = file(requireNotNull(System.getenv("BMR_SIGNING_PASSWORD_FILE")))
                    .readText().trim()
                keyAlias = "bluetooth-mic-router"
                keyPassword = storePassword
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            if (!signingStore.isNullOrBlank()) signingConfig = signingConfigs.getByName("localRelease")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        // SDK 35 is pinned for the verified Android 12+ sideload release.
        disable += "OldTargetApi"
        warningsAsErrors = true
    }
}

// No runtime dependencies: all application classes use the Android/Java platform.
