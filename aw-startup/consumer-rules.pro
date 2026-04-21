# aw-startup consumer ProGuard rules

# AppInitializer interface and implementations
-keep class * implements com.answufeng.startup.AppInitializer {
    <methods>;
}
-keep class * implements com.answufeng.startup.AppInitializer

# SuspendAppInitializer
-keep class * extends com.answufeng.startup.SuspendAppInitializer {
    <methods>;
}
-keepclassmembers class * extends com.answufeng.startup.SuspendAppInitializer {
    *** onCreateSuspend(android.content.Context, kotlinx.coroutines.Continuation);
}

# DSL 创建的匿名子类（关键！DSL 方式注册时会创建 AppInitializer 的匿名子类）
-keepclassmembers class * implements com.answufeng.startup.AppInitializer {
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

# InitResult
-keep class com.answufeng.startup.InitResult { *; }
-keep class com.answufeng.startup.InitResult$* { *; }

# FailStrategy
-keep class com.answufeng.startup.FailStrategy { *; }

# InitPriority
-keep class com.answufeng.startup.InitPriority { *; }
-keep class com.answufeng.startup.InitPriority$Custom { *; }
-keep class com.answufeng.startup.InitPriority$* { *; }

# AwStartup (入口类)
-keep class com.answufeng.startup.AwStartup { *; }
-keep class com.answufeng.startup.AwStartup$* { *; }

# StartupConfig (DSL 配置)
-keep class com.answufeng.startup.StartupConfig { *; }
-keep class com.answufeng.startup.StartupConfig$* { *; }

# StartupStore (初始化器间数据共享)
-keep class com.answufeng.startup.StartupStore { *; }
-keep class com.answufeng.startup.StartupStore$* { *; }

# StartupReport
-keep class com.answufeng.startup.StartupReport { *; }
-keep class com.answufeng.startup.StartupReport$* { *; }

# StartupLogger (自定义日志接口)
-keep interface com.answufeng.startup.StartupLogger { *; }
-keep class com.answufeng.startup.DefaultStartupLogger { *; }

# Kotlin metadata
-keepattributes Signature, *Annotation*
-keep class kotlin.Metadata { *; }
