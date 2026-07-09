# Spartan release (R8) rules. Room, Hilt, Compose, WorkManager, and OkHttp ship consumer rules;
# the entries below cover kotlinx-serialization, Retrofit's reflective surface, and AppAuth.

# --- kotlinx-serialization ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class com.spartan.**$$serializer { *; }
-keepclassmembers class com.spartan.** {
    *** Companion;
}
-keepclasseswithmembers class com.spartan.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Retrofit ---
-keepattributes Signature, Exceptions
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-dontwarn retrofit2.KotlinExtensions*
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit

# --- OkHttp / Okio ---
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- AppAuth (OAuth) ---
-keep class net.openid.appauth.** { *; }

# --- Tink (transitively via security-crypto) ---
-dontwarn com.google.errorprone.annotations.**
