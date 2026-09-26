# R8 rules for the release, sideload and minified (smoke test) builds.

# Strip verbose, debug and info logging.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Keep file names and line numbers so crash reports can be translated back with R8's retrace and
# the build's mapping.txt (kept by CI with every APK and release).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Native code in these libraries looks up Java classes, fields and methods by name, so none of
# them may be renamed or removed. The on-device smoke test runs each one on the minified build.
-keep class com.googlecode.tesseract.android.** { *; }
-keep class com.google.ai.edge.litertlm.** { *; }
-dontwarn com.google.ai.edge.litertlm.**
-keep class com.google.ai.edge.litert.** { *; }
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**
