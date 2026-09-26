# Only for the "minified" build type (the smoke test's target), not what ships. The smoke test's
# own libraries (androidx.test) run inside the app's process and call Kotlin runtime helpers,
# like Intrinsics.checkNotNullParameter, that R8 removes from the app when the app itself
# doesn't use them: the test runner then crashes before any test starts. Keeping the Kotlin
# runtime changes nothing about what's being tested (the ML libraries' native code and the
# app's own classes are minified exactly as in the sideload build).
-keep class kotlin.** { *; }
# Libraries androidx.test shares with the app, which R8 trims to what the app itself uses.
-keep class androidx.tracing.** { *; }
-keep class androidx.concurrent.** { *; }
-keep class com.google.common.util.concurrent.** { *; }
-keep class androidx.lifecycle.** { *; }
-keep class androidx.core.** { *; }
-keep class androidx.annotation.** { *; }

# The app classes the smoke test calls directly. The test is compiled against their original
# names; R8 renames them, merges companions and changes signatures, so the test can't find them
# (NoSuchMethodError / NoSuchFieldError). Keeping these wrappers intact still leaves every ML
# library minified under the sideload build's own rules, which is what the test checks.
-keep class com.eyal98.stickerfinder.ocr.** { *; }
-keep class com.eyal98.stickerfinder.vision.** { *; }
-keep class com.eyal98.stickerfinder.ml.** { *; }
-keep class com.eyal98.stickerfinder.embed.** { *; }
-keep class com.eyal98.stickerfinder.search.** { *; }
