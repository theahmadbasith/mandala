# Add project specific ProGuard rules here.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Room database keep rules
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.**

# Vico charting library rules
-keep class com.patrykandpatrick.vico.** { *; }
-dontwarn com.patrykandpatrick.vico.**

# OkHttp R8 Full Mode dontwarn rules for optional platform bindings
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn okhttp3.internal.platform.**



