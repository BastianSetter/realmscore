# ProGuard / R8 rules — Fantasy Realms Scoring App

# ---- Kotlin metadata ----
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keep class kotlin.Metadata { *; }

# ---- Jetpack Compose ----
# The Compose runtime + tooling keeps most of what's needed via its own
# consumer rules. We just keep our @Composable entry points safe.
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}
-keep class androidx.compose.runtime.** { *; }

# ---- Room ----
-keep class androidx.room.** { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# ---- kotlinx.serialization ----
-keepattributes RuntimeVisibleAnnotations, AnnotationDefault
-keep,includedescriptorclasses class de.morzo.realmscore.**$$serializer { *; }
-keepclassmembers class de.morzo.realmscore.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class de.morzo.realmscore.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---- DataStore Preferences ----
-keep class androidx.datastore.** { *; }

# ---- App-specific models referenced by reflection (none today, kept defensively) ----
-keep class de.morzo.realmscore.domain.model.** { *; }
