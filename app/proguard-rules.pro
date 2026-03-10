# Add project specific ProGuard rules here.
# Keep ARCore classes
-keep class com.google.ar.** { *; }
-keep class com.google.ar.core.** { *; }

# Keep SceneView / Filament classes
-keep class io.github.sceneview.** { *; }
-keep class com.google.android.filament.** { *; }

# Keep Media3 / ExoPlayer classes
-keep class androidx.media3.** { *; }
-keep class com.google.android.exoplayer2.** { *; }

# Keep Kotlin coroutines
-keep class kotlinx.coroutines.** { *; }
