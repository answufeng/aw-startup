# aw-startup

[![](https://jitpack.io/v/answufeng/aw-startup.svg)](https://jitpack.io/#answufeng/aw-startup)

Android 应用启动初始化库，提供优先级分级、依赖感知的组件初始化，内置拓扑排序与循环依赖检测。

## 特性

- **4 级优先级**：IMMEDIATELY → NORMAL → DEFERRED → BACKGROUND
- **拓扑排序**：同一优先级内按依赖关系排序，带循环依赖检测
- **后台线程池**：BACKGROUND 优先级使用 CountDownLatch 保证依赖顺序
- **空闲执行**：DEFERRED 优先级通过 IdleHandler 延迟到主线程空闲时执行
- **错误隔离**：单个初始化器失败不影响后续初始化
- **DSL 配置**：简洁的 Kotlin DSL 方式注册初始化器
- **结果回调**：每个初始化器完成后触发回调，回调保证在主线程执行
- **初始化报告**：含耗时统计，可随时获取
- **可配置线程数**：支持自定义后台线程池大小
- **await 支持**：可等待所有后台任务完成

## 与 AndroidX Startup 对比

| 特性 | AndroidX Startup | aw-startup |
|------|-----------------|------------|
| 优先级分级 | ❌ | ✅ 4 级 |
| 依赖排序 | ✅ | ✅ |
| 循环依赖检测 | ❌ | ✅ |
| 后台线程执行 | ❌ | ✅ |
| 空闲时执行 | ❌ | ✅ |
| DSL 配置 | ❌ | ✅ |
| 初始化耗时报告 | ❌ | ✅ |
| 错误隔离 | ❌ | ✅ |
| 等待完成 | ❌ | ✅ |

## 引入

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://jitpack.io") }
    }
}

// app/build.gradle.kts
dependencies {
    implementation("com.github.answufeng:aw-startup:1.0.0")
}
```

## 使用

### 1. 定义初始化器

实现 `AppInitializer` 接口：

```kotlin
class LoggerInit : AppInitializer {
    override val name = "Logger"
    override val priority = InitPriority.IMMEDIATELY
    override fun onCreate(context: Context) {
        AwLogger.init { debug = BuildConfig.DEBUG }
    }
}
```

### 2. 带依赖的初始化器

```kotlin
class NetworkInit : AppInitializer {
    override val name = "Network"
    override val priority = InitPriority.NORMAL
    override val dependencies = listOf("Logger")
    override fun onCreate(context: Context) {
        AwNet.init(context)
    }
}
```

### 3. 在 Application 中初始化

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AwStartup.init(this) {
            add(LoggerInit())
            add(NetworkInit())
            add(AnalyticsInit())   // DEFERRED
            add(CacheInit())       // BACKGROUND
            backgroundThreads(4)  // 可选：自定义后台线程数，默认 2
            onResult { result ->
                Log.d("Startup", "${result.name} [${result.priority}] ${result.costMillis}ms")
            }
        }
    }
}
```

### 4. 优先级说明

| 优先级 | 执行时机 | 线程 | 适用场景 |
|--------|----------|------|----------|
| `IMMEDIATELY` | 最先执行 | 主线程同步 | 崩溃收集、日志 |
| `NORMAL` | IMMEDIATELY 之后 | 主线程同步 | 网络、图片、存储 |
| `DEFERRED` | 主线程空闲后 | 主线程异步 | 统计上报、推送 |
| `BACKGROUND` | 子线程异步 | IO 线程 | 缓存清理、数据预热 |

### 5. 查看启动报告

```kotlin
val report = AwStartup.getReport()
report.forEach {
    Log.d("Startup", "${it.name} [${it.priority}] ${it.costMillis}ms ${if (it.success) "OK" else "FAIL"}")
}
Log.d("Startup", "同步总耗时: ${AwStartup.getSyncCostMillis()}ms")
```

### 6. 等待后台任务完成

```kotlin
// 无限等待
AwStartup.await()

// 带超时（毫秒），返回是否在超时前完成
val completed = AwStartup.await(3000)
```

## API

### AwStartup

| 方法 | 说明 |
|------|------|
| `init(context, block)` | DSL 方式初始化（推荐） |
| `register(initializer)` | 手动注册初始化器 |
| `start(context)` | 启动初始化流程 |
| `getReport()` | 获取初始化结果列表 |
| `getSyncCostMillis()` | 获取同步初始化耗时 |
| `isStarted` | 是否已启动 |
| `await(timeoutMillis)` | 等待所有后台任务完成 |

### StartupConfig DSL

| 方法 | 说明 |
|------|------|
| `add(initializer)` | 添加初始化器 |
| `onResult(callback)` | 设置结果回调 |
| `backgroundThreads(count)` | 设置后台线程数（默认 2） |

### AppInitializer

| 属性/方法 | 说明 |
|-----------|------|
| `name` | 唯一标识，用于依赖引用和日志 |
| `priority` | 初始化优先级 |
| `dependencies` | 依赖的初始化器名称列表 |
| `onCreate(context)` | 执行初始化逻辑 |
| `onCompleted()` | 初始化完成回调（可选） |

## 许可证

Apache License 2.0，详见 [LICENSE](LICENSE)。
