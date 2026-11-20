# AstraTouch R8 / ProGuard rules
# Keep line numbers for readable local stack traces (no remote telemetry).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod

# ---- Kotlin ---- 
-dontwarn kotlin.**
-keepclassmembers class kotlin.Metadata { public <methods>; }

# ---- Kotlin Serialization ---- 
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class **$$serializer { *; }
-keep,includedescriptorclasses class com.astratouch.app.**$$serializer { *; }
-keepclasseswithmembers class com.astratouch.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---- Room ---- 
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ---- Hilt / Dagger ---- 
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper
-dontwarn dagger.hilt.**

# ---- Compose ---- 
-dontwarn androidx.compose.**

# ---- AstraTouch manifest-declared components ---- 
-keep class com.astratouch.app.service.** { *; }
-keep class com.astratouch.app.accessibility.** { *; }
-keep class com.astratouch.app.receiver.** { *; }
-keep class com.astratouch.app.AstraTouchApplication { *; }
-keep class com.astratouch.app.MainActivity { *; }

# ---- Enum values used reflectively in action registry ---- 
-keepclassmembers enum com.astratouch.app.domain.model.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Strip verbose/debug logging from release builds while keeping warnings.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
