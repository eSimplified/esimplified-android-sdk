-keep class io.esimplified.sdk.** { *; }
-keepattributes *Annotation*
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class io.esimplified.sdk.model.** {
    <init>(...);
    <fields>;
}
-keepattributes Signature
-keepattributes Exceptions
-keep,allowobfuscation interface retrofit2.Call
-keep,allowobfuscation interface retrofit2.Callback
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**
