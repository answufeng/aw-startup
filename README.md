# aw-startup

应用启动初始化库，提供优先级分级、依赖感知的组件初始化，内置拓扑排序。

## 引入

```kotlin
dependencies {
    implementation("com.github.answufeng:aw-startup:1.0.0")
}
```

## 功能特性

- 4 级优先级：IMMEDIATELY、NORMAL、DEFERRED、BACKGROUND
- 拓扑排序，带循环依赖检测
- 后台线程池，使用 CountDownLatch 保证依赖顺序
- DEFERRED 优先级使用 IdleHandler 延迟到空闲时执行
- 错误隔离：单个初始化器失败不影响后续
- DSL 配置方式，支持结果回调
- 初始化报告，含耗时统计

## 使用示例

```kotlin
// 定义初始化器
class LoggerInit : AppInitializer {
    override val name = "Logger"
    override val priority = InitPriority.IMMEDIATELY
    override fun onCreate(context: Context) { /* 初始化日志 */ }
}

class NetworkInit : AppInitializer {
    override val name = "Network"
    override val priority = InitPriority.NORMAL
    override val dependencies = listOf("Logger")
    override fun onCreate(context: Context) { /* 初始化网络 */ }
}

// 初始化
BrickStartup.init(this) {
    add(LoggerInit())
    add(NetworkInit())
    add(AnalyticsInit())   // DEFERRED
    add(CacheInit())       // BACKGROUND
    onResult { result ->
        Log.d("Startup", "${result.name} ${result.costMillis}ms")
    }
}

// 获取报告
val report = BrickStartup.getReport()
val syncCost = BrickStartup.getSyncCostMillis()
```

## 许可证

Apache License 2.0，详见 [LICENSE](LICENSE)。
