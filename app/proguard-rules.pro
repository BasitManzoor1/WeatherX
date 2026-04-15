# Keep Kotlin Serialization models
-keepattributes *Annotation*, EnclosingMethod, InnerClasses, Signature
-keep,allowobfuscation,allowshrinking class kotlinx.serialization.json.** { *; }
-keepclassmembers class com.devorbit.weatherx.WeatherApi$** {
    *** get*();
    *** set*(*);
}
-keep @kotlinx.serialization.Serializable class com.devorbit.weatherx.** { *; }

# Keep WebView JS interfaces if you add any later
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# General optimizations
-repackageclasses ''
-allowaccessmodification
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*
