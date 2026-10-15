plugins {
    id("com.android.application")
}

android {
    namespace = "ro.amadya.pos"
    compileSdk = 37

    defaultConfig {
        applicationId = "ro.amadya.pos"
        minSdk = 26
        // 35, not 37: from API 36, Android ignores the landscape lock on tablets (screens 600dp and wider).
        // The counter app must stay in landscape (spec: Counter mode).
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "DEFAULT_SERVER", "\"https://amadya.alphatech.ro\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
        }
    }

    lint {
        // Target SDK 35 on purpose (see defaultConfig). The Gradle version follows backend/.
        disable += setOf("OldTargetApi", "AndroidGradlePluginVersion", "UseKtx")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.activity:activity:1.13.0")
    implementation("androidx.core:core:1.19.1")
    implementation("androidx.webkit:webkit:1.17.1")

    testImplementation("junit:junit:4.13.2")
}
