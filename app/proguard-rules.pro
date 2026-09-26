# R8 / ProGuard rules for Momentum.
#
# R8 strips and renames everything it can prove is unused, and deletes anything
# it cannot see being referenced — which includes anything instantiated by name
# (manifest components, reflected model classes). These rules name the things
# that must survive, so the release build does not break at runtime.
#
# Size note: minification is worth ~4.9 MB of the 7.7 MB debug APK, and
# obfuscation takes off a further ~0.08 MB. Both are on.

# --- Retrofit / OkHttp -------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-dontwarn javax.annotation.**
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# --- Gson -------------------------------------------------------------
# Gson builds these types reflectively, so their field names are read at
# runtime rather than called.
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep class com.anish.momentum.ai.** { *; }
-keep class com.anish.momentum.models.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-dontwarn sun.misc.**

# --- Manifest components ----------------------------------------------
# The system instantiates these by class name, so R8 sees no reference to them.
-keep class com.anish.momentum.utils.BootReceiver { *; }
-keep class com.anish.momentum.utils.ReminderReceiver { *; }
-keep class com.anish.momentum.widgets.StreakWidget { *; }
-keep class com.anish.momentum.widgets.WidgetActionReceiver { *; }

# --- Lottie -----------------------------------------------------------
# Its models come from the JSON animations, and the @SerializedName rule above
# already covers them. A blanket keep here used to retain ~305 extra classes.
-dontwarn com.airbnb.lottie.**

