import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.eyal98.stickerfinder.caption"
    compileSdk = 35

    defaultConfig {
        minSdk = 30
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        // LiteRT-LM is compiled with a newer Kotlin than this project; its API here is plain
        // classes and constructors, which read fine. Limited to this module.
        freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
}

dependencies {
    api(project(":core:ml"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.mediapipe.tasks.genai)
    // MPImage/BitmapImageBuilder: used by the genai API, but genai doesn't declare the dependency.
    implementation(libs.mediapipe.tasks.core)
    // Runs .litertlm models.
    implementation(libs.litertlm.android)

    testImplementation(libs.junit)
}
