import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.eyal98.stickerfinder.embed"
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
    }
}

dependencies {
    api(project(":core:data"))
    implementation(project(":core:ml"))
    implementation(libs.kotlinx.coroutines.core)
    // Google AI Edge on-device RAG SDK: runs EmbeddingGemma with its SentencePiece tokenizer.
    // Only its local embedding model is used; its cloud (Gemini) embedder is never constructed,
    // and the app has no INTERNET permission in any case.
    implementation(libs.localagents.rag)
    // Single-file (.litertlm) embedding models, e.g. Granite multilingual.
    implementation(libs.litertlm.android)
}
