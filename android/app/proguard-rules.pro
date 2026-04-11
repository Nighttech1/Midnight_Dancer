# Midnight Dancer — keep rules for release (R8/ProGuard)
# JNI: keep classes that have native methods so .so can find them (sherpa_onnx, etc.)
-keepclasseswithmembers class * {
    native <methods>;
}

# flutter_local_notifications + Gson: без этого R8 ломает TypeToken при чтении
# scheduled_notifications из SharedPreferences → java.lang.RuntimeException: Missing type parameter.
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.Unsafe
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.dexterous.flutterlocalnotifications.** { *; }
