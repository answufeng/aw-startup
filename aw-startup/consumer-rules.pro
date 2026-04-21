# aw-startup consumer ProGuard rules

# ===========================================================
# StartupInitializer and SuspendInitializer
# ===========================================================

# Keep the abstract class and its members for reflection and DSL anonymous subclasses
-keepclassmembers class * extends com.answufeng.startup.StartupInitializer {
    java.lang.String name;
    com.answufeng.startup.InitPriority priority;
    java.util.List dependencies;
    com.answufeng.startup.FailStrategy failStrategy;
    long timeoutMillis;
    int retryCount;
    boolean enabled;
    void onCreate(android.content.Context);
    void onCompleted();
    void onFailed(java.lang.Throwable);
}

# Keep SuspendAppInitializer's coroutine continuation parameter method signature
-keepclassmembers class * extends com.answufeng.startup.SuspendInitializer {
    kotlin.coroutines.jvm.internal.Continuation onCreateSuspend(android.content.Context, kotlin.coroutines.jvm.internal.Continuation);
}

# ===========================================================
# Data classes and enums
# ===========================================================

-keep class com.answufeng.startup.InitResult { *; }
-keep class com.answufeng.startup.InitResult$* { *; }

-keep class com.answufeng.startup.FailStrategy { *; }

-keep class com.answufeng.startup.InitPriority { *; }
-keep class com.answufeng.startup.InitPriority$Custom { *; }
-keep class com.answufeng.startup.InitPriority$* { *; }

# ===========================================================
# Public API classes
# ===========================================================

-keep class com.answufeng.startup.AwStartup { *; }
-keep class com.answufeng.startup.AwStartup$* { *; }

-keep class com.answufeng.startup.StartupConfig { *; }
-keep class com.answufeng.startup.StartupConfig$* { *; }

-keep class com.answufeng.startup.StartupStore { *; }
-keep class com.answufeng.startup.StartupStore$* { *; }

-keep class com.answufeng.startup.StartupReport { *; }
-keep class com.answufeng.startup.StartupReport$* { *; }

-keep interface com.answufeng.startup.StartupLogger { *; }
-keep class com.answufeng.startup.DefaultStartupLogger { *; }

-keep class com.answufeng.startup.SuspendInitializer { *; }
-keep class com.answufeng.startup.SuspendInitializer$* { *; }

# ===========================================================
# DslMarker annotation
# ===========================================================

-keep class com.answufeng.startup.AwStartupDsl { *; }

# ===========================================================
# Internal classes (for stack traces and error messages)
# ===========================================================

-keep class com.answufeng.startup.internal.StartupThreadFactory { *; }

# ===========================================================
# Kotlin metadata (avoid duplication with proguard-rules.pro)
# ===========================================================

-keepattributes Signature, *Annotation*
-keep class kotlin.Metadata { *; }
