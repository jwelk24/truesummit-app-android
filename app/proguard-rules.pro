# R8 / ProGuard rules for TrueSummit.
#
# The theme here is reflection: anything resolved by name at runtime rather
# than by a compile-time reference is invisible to R8 and will be stripped or
# renamed. Those failures only surface in release builds.

# ── Kotlin metadata & coroutines ─────────────────────────────────────────────
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keepattributes Signature
-keepattributes InnerClasses,EnclosingMethod
-keepattributes SourceFile,LineNumberTable

-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# ── kotlinx.serialization ────────────────────────────────────────────────────
# Generated serializers are referenced reflectively via the companion.
-keepattributes *Annotation*
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    static <1>$Companion Companion;
    static **$* *;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn kotlinx.serialization.**

# The AI response models are deserialized straight from Gemini's JSON.
-keep @kotlinx.serialization.Serializable class com.truesummit.android.** { *; }

# ── Room ─────────────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.paging.**

# Entities are also mapped by field name by the generated Room code.
-keep class com.truesummit.android.data.entity.** { *; }
-keep class com.truesummit.android.data.model.** { *; }
-keep class com.truesummit.android.data.converter.** { *; }

# ── Retrofit + Gson ──────────────────────────────────────────────────────────
# Gson maps JSON keys onto field names, so renaming the fields breaks parsing
# silently rather than loudly.
-keepattributes Exceptions
-keep,allowobfuscation interface retrofit2.Call
-keep,allowobfuscation class retrofit2.Response
-keepclasseswithmembers interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**

-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Plaid request/response models cross the Gson boundary.
-keep class com.truesummit.android.service.Plaid** { *; }
-keep class com.plaid.** { *; }
-dontwarn com.plaid.**

# ── Supabase / Ktor ──────────────────────────────────────────────────────────
-keep class io.github.jan.supabase.** { *; }
-dontwarn io.github.jan.supabase.**
-dontwarn io.ktor.**
-keepclassmembers class io.ktor.** { volatile <fields>; }

# ── Compose ──────────────────────────────────────────────────────────────────
# Compose is largely R8-safe, but the compiler emits classes the shrinker can
# misjudge when they are only reached from composition.
-dontwarn androidx.compose.**

# ── Glance app widgets ───────────────────────────────────────────────────────
# Receivers and the GlanceAppWidget subclasses are instantiated by the system.
-keep class * extends androidx.glance.appwidget.GlanceAppWidget { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }
-keep class com.truesummit.android.widget.** { *; }

# ── Wear data layer ──────────────────────────────────────────────────────────
-keep class * extends com.google.android.gms.wearable.WearableListenerService { *; }
-dontwarn com.google.android.gms.**

# ── Entry points instantiated by the framework ───────────────────────────────
-keep class com.truesummit.android.TrueSummitApplication { *; }
-keep class * extends android.app.Service { *; }
-keep class * extends android.content.BroadcastReceiver { *; }

# ── slf4j ────────────────────────────────────────────────────────────────────
# Pulled in transitively by Ktor/Supabase. The binder is a compile-time
# artifact of slf4j's static binding; there is no Android implementation and
# nothing calls it at runtime.
-dontwarn org.slf4j.impl.StaticLoggerBinder

# ── ML Kit text recognition ──────────────────────────────────────────────────
-dontwarn com.google.mlkit.**
-keep class com.google.mlkit.** { *; }
