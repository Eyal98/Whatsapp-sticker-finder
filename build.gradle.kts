plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.asset.pack) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}

/**
 * -PmodelsInAssetPack: ship the three big models (Granite, SigLIP 2, the face model) in the
 * :model_pack install-time asset pack rather than as assets of the modules that use them.
 *
 * The Play bundle needs this — see model_pack/build.gradle.kts for why — and only the Play bundle:
 * asset packs aren't packaged into an APK, so the sideload APK, the debug APK and the on-device
 * smoke test all keep the models as plain assets and build with the flag off, which is the default.
 * Either way the app reads them from the same asset paths.
 *
 * app, core/embed and core/vision each read the flag with
 * providers.gradleProperty("modelsInAssetPack"); this comment is the one place it's explained.
 */
