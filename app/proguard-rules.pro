# Retrofit / Gson
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keep class io.github.zyakusen.tsukiyo.data.model.** { *; }
-keep class io.github.zyakusen.tsukiyo.data.api.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Media3
-dontwarn com.google.errorprone.annotations.**
