# ProGuard rules for DiptoKey release builds

# Keep Kotlinx Serialization metadata
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.diptokey.ime.**$$serializer { *; }
-keepclassmembers class com.diptokey.ime.** {
    *** Companion;
}
-keepclasseswithmembers class com.diptokey.ime.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Room
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# Keep IME service (referenced from manifest)
-keep class com.diptokey.ime.DiptoKeyIME { *; }
