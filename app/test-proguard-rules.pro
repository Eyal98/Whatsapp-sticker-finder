# R8 rules for the smoke test's APK (src/androidTest) when it tests the minified build. The test
# is only a harness: keep it and its test libraries whole; the app's own classes are mapped by R8.
-keep class com.eyal98.stickerfinder.ModelSmokeTest { *; }
-keep class com.eyal98.stickerfinder.MemoryBudgetTest** { *; }
-keep class androidx.test.** { *; }
-keep class org.junit.** { *; }
-dontwarn **
