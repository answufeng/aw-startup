# aw-startup ProGuard Rules
# 此文件用于库自身的 release 构建混淆规则
# Consumer-facing rules（供使用者混淆时使用）位于 consumer-rules.pro

# ===========================================================
# 保留公共 API
# ===========================================================

# 保留所有公共类
-keep class com.answufeng.startup.** { *; }

# ===========================================================
# 保留 AppInitializer 实现类
# ===========================================================

-keepclassmembers class * implements com.answufeng.startup.AppInitializer {
    <methods>;
}
-keep class * implements com.answufeng.startup.AppInitializer

# ===========================================================
# 保留 SuspendAppInitializer 实现类
# ===========================================================

-keepclassmembers class * extends com.answufeng.startup.SuspendAppInitializer {
    *** onCreateSuspend(android.content.Context, kotlinx.coroutines.Continuation);
}

# ===========================================================
# 保留 InitPriority 和 InitResult 相关类
# ===========================================================

-keep class com.answufeng.startup.InitPriority { *; }
-keep class com.answufeng.startup.InitPriority$* { *; }
-keep class com.answufeng.startup.InitResult { *; }
-keep class com.answufeng.startup.FailStrategy { *; }

# ===========================================================
# 保留 StartupStore 相关类
# ===========================================================

-keep class com.answufeng.startup.StartupStore { *; }
-keep class com.answufeng.startup.internal.** { *; }

# ===========================================================
# 保留 Kotlin 反射和元数据
# ===========================================================

-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keepattributes Signature
-keepattributes Exceptions

# 保留 Kotlin Metadata
-keep class kotlin.Metadata { *; }

# ===========================================================
# 保留枚举和 sealed class
# ===========================================================

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
