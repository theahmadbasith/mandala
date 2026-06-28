# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Mandala Proguard Rules
-keep class com.mandala.net.database.** { *; }

# OkHttp Rules
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }

# Retrofit Rules
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }

# Moshi Rules
-dontwarn com.squareup.moshi.**
-keep @com.squareup.moshi.JsonQualifier interface *
-keepclassmembers class * {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}

# Kotlin Metadata (Required for Moshi and Kotlin reflection)
-keep class kotlin.Metadata { *; }

# Keep Enum values() for Moshi/Gson
-keepclassmembers class * extends java.lang.Enum {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
