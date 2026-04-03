# ProGuard configuration for i-mode browser simulator app

# Keep all classes defined in this app
-keep class com.myapp.** { *; }

# Keep Android & AndroidX classes
-keep class androidx.** { *; }
-keep class android.** { *; }

# Keep OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep JSoup
-dontwarn org.jsoup.**

# Keep Kotlin extensions
-keep class kotlin.** { *; }
-keep interface kotlin.** { *; }

# Keep coroutines
-keep class kotlinx.coroutines.** { *; }

# Keep data classes
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep model classes (data classes, enums)
-keep class com.myapp.model.** { *; }

# Remove logging in production
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Optimization settings
-optimizationpasses 5
-dontobfuscate
-verbose

# Preserve line numbers for stack traces
-keepattributes SourceFile,LineNumberTable
