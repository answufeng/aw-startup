# aw-startup ProGuard Rules
# This file is used for the library's own release builds.
# Consumer-facing rules are located in consumer-rules.pro

# ===========================================================
# Keep Kotlin metadata and annotations
# ===========================================================

-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keepattributes Signature
-keepattributes Exceptions
-keep class kotlin.Metadata { *; }

# ===========================================================
# Keep enums and Serializable
# ===========================================================

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient *;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ===========================================================
# Keep DslMarker annotation
# ===========================================================

-keep class com.answufeng.startup.AwStartupDsl { *; }

# ===========================================================
# Keep internal thread factory for stack traces
# ===========================================================

-keep class com.answufeng.startup.internal.StartupThreadFactory { *; }
