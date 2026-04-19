# aw-startup consumer ProGuard rules

-keepclassmembers class * implements com.answufeng.startup.AppInitializer {
    <methods>;
}
-keep class * implements com.answufeng.startup.AppInitializer

-keepclassmembers class * extends com.answufeng.startup.SuspendAppInitializer {
    *** onCreateSuspend(android.content.Context, kotlinx.coroutines.Continuation);
}

-keep class com.answufeng.startup.InitPriority$Custom {
    *;
}

-keep class com.answufeng.startup.InitResult { *; }
-keep class com.answufeng.startup.FailStrategy { *; }
-keep class com.answufeng.startup.InitPriority { *; }
-keep class com.answufeng.startup.InitPriority$* { *; }

# Kotlin metadata
-keepattributes Signature, *Annotation*
