# Keep Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class com.codeforge.agent.**$$serializer { *; }
-keepclassmembers class com.codeforge.agent.** {
    *** Companion;
}
-keepclasseswithmembers class com.codeforge.agent.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep JNI
-keep class com.codeforge.agent.nativebridge.** { *; }
-keepclasseswithmembernames class * { native <methods>; }

# Keep OkHttp/Retrofit
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keepattributes Signature
-keepattributes Exceptions

# Keep Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Keep JGit
-dontwarn org.eclipse.jgit.**
-dontwarn org.slf4j.**
-keep class org.eclipse.jgit.** { *; }
-keep class org.slf4j.** { *; }

# Keep Compose
-keep class androidx.compose.** { *; }

# Security crypto / Tink
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.crypto.tink.**