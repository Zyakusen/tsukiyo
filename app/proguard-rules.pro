# Retrofit / Gson
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keep class com.zyakusen.tsukiyo.data.model.** { *; }
-keep class com.zyakusen.tsukiyo.data.api.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Media3
-dontwarn com.google.errorprone.annotations.**
