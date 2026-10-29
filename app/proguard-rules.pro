# =============================================================================
# SineMiner ProGuard rules
# =============================================================================

# --- General Android ---
-dontwarn android.support.**
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod, SourceFile, LineNumberTable

# --- Kotlin ---
-dontwarn kotlin.**
-keepclassmembers class kotlin.Metadata { *; }
-keep class kotlin.coroutines.** { *; }

# --- Hilt / Dagger ---
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.Hilt_AndroidApp
-keepclassmembers class * { @dagger.hilt.android.Hilt_AndroidApp *; }
-dontwarn javax.inject.**
-dontwarn dagger.internal.**

# --- Room ---
-keep class androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class *
-dontwarn androidx.room.**

# --- WorkManager ---
-keep class androidx.work.** { *; }

# --- Coroutines ---
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# --- Serialization ---
-keepattributes RuntimeVisibleAnnotations, AnnotationDefault
-keepclassmembers class **$$serializer { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}

# --- Security / Crypto ---
-keep class androidx.security.crypto.** { *; }

# --- JNI / Native bridge ---
-keep class com.secretarrow.sineminer.core.native.NativeBridge { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# --- Compose ---
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# --- App models ---
-keep class com.secretarrow.sineminer.data.model.** { *; }

# --- Suppress warnings on third-party libs ---
-dontwarn org.slf4j.**
-dontwarn org.bouncycastle.**
-dontwarn javax.naming.**

# --- Tink (pulled in by androidx.security.crypto) references errorprone
#     annotations that aren't in the runtime classpath. R8 by default treats
#     these as errors; suppress the warnings so the release minify step can
#     complete.
-dontwarn com.google.errorprone.annotations.**

# --- kotlinx.serialization may reference classes via reflection ---
-dontwarn kotlinx.serialization.**
