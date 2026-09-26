import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.eyal98.stickerfinder"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.eyal98.stickerfinder"
        minSdk = 30
        targetSdk = 35
        // CI passes its run number so each sideload build installs over the previous one.
        versionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = "0.1.0"

        // 64-bit ARM only: every phone that can run the on-device models (Android 11+, 6 GB+
        // RAM) is arm64. The ML runtimes ship native code for 4 CPU types, and the other three
        // made the APK about 250 MB, big enough that sideload downloads failed to install.
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    // The sideload signing key: a PKCS12 file whose path CI passes as -PsigningStoreFile, with
    // its password in the SIGNING_KEYSTORE_PASSWORD environment variable (never on the command
    // line or in the repository). Without both, sideload builds come out unsigned.
    val signingStore = (findProperty("signingStoreFile") as String?)?.let(::file)
    val signingPassword: String? = System.getenv("SIGNING_KEYSTORE_PASSWORD")
    signingConfigs {
        if (signingStore != null && signingPassword != null) {
            create("sideload") {
                storeFile = signingStore
                storeType = "pkcs12"
                storePassword = signingPassword
                keyAlias = "stickerfinder"
                keyPassword = signingPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // A release-like build for installing on your own phone from CI: not debuggable (so
        // app data can't be read over USB), signed with one stable key so updates keep data.
        // R8 stays off until minified builds have been tested with the ML libraries.
        create("sideload") {
            initWith(getByName("release"))
            isMinifyEnabled = false
            isShrinkResources = false
            matchingFallbacks += listOf("release")
            signingConfigs.findByName("sideload")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    androidResources {
        // The bundled SigLIP model is memory-mapped straight from the APK, which needs it stored
        // uncompressed (it barely compresses anyway).
        noCompress += "tflite"
    }

    packaging {
        // Compress native libraries in the APK (they're extracted once at install). Stored
        // uncompressed, they made the sideload download about 2.5x bigger.
        jniLibs.useLegacyPackaging = true
    }

    lint {
        abortOnError = true
        checkDependencies = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:index"))
    implementation(project(":core:ml"))
    implementation(project(":core:embed"))
    implementation(project(":core:vision"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.work.runtime.ktx)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
