# aw-startup

[![](https://jitpack.io/v/answufeng/aw-startup.svg)](https://jitpack.io/#answufeng/aw-startup)

Android 应用启动初始化库，提供优先级分级、依赖感知的组件初始化，内置拓扑排序与循环依赖检测。

## 特性

- **4 级优先级**：IMMEDIATELY → NORMAL → DEFERRED → BACKGROUND
- **自定义优先级**：`InitPriority.Custom` 支持自定义执行器
- **拓扑排序**：同一优先级内按依赖关系排序，带循环依赖检测
- **后台线程池**：BACKGROUND 优先级使用 CountDownLatch 保证依赖顺序
- **空闲执行**：DEFERRED 优先级通过 IdleHandler 延迟到主线程空闲时执行
- **错误隔离**：单个初始化器失败不影响后续初始化
- **失败策略**：`FailStrategy.ABORT_DEPENDENTS` 自动跳过依赖失败者的任务
- **DSL 配置**：简洁的 Kotlin DSL 方式注册初始化器（`immediately` / `normal` / `deferred` / `background`）
- **结果回调**：每个初始化器完成后触发回调，回调保证在主线程执行
- **初始化报告**：含耗时统计，可随时获取
- **可配置线程数**：支持自定义后台线程池大小或自定义 ExecutorService
- **await 支持**：可等待所有后台任务完成，支持超时
- **协程兼容**：`SuspendAppInitializer` 支持 `suspend` 初始化逻辑
- **onFailed 回调**：初始化器可自行处理失败（降级/重试）

## 与 AndroidX Startup 对比

| 特性 | AndroidX Startup | aw-startup |
|------|-----------------|------------|
| 优先级分级 | ❌ | ✅ 4 级 + 自定义 |
| 依赖排序 | ✅ | ✅ |
| 循环依赖检测 | ❌ | ✅ |
| 后台线程执行 | ❌ | ✅ |
| 空闲时执行 | ❌ | ✅ IdleHandler |
| DSL 配置 | ❌ | ✅ |
| 初始化耗时报告 | ❌ | ✅ |
| 错误隔离 | ❌ | ✅ |
| 失败策略 | ❌ | ✅ |
| 等待完成 | ❌ | ✅ 带超时 |
| 协程兼容 | ❌ | ✅ |
| 自定义执行器 | ❌ | ✅ |

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

### 1. DSL 快捷方式（推荐）

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AwStartup.init(this) {
            immediately("Logger") { AwLogger.init() }
            normal("Network", deps = listOf("Logger")) { AwNet.init(it) }
            deferred("Analytics", deps = listOf("Network")) { AwAnalytics.init(it) }
            background("CacheCleaner") { AwCache.clean(it) }
            onResult { result ->
                Log.d("Startup", "${result.name} [${result.priority}] ${result.costMillis}ms")
            }
        }
    }
}
```

### 2. 完整类声明方式

继承 `AppInitializer` 抽象类：

```kotlin
class LoggerInit : AppInitializer() {
    override val name = "Logger"
    override val priority = InitPriority.IMMEDIATELY
    override fun onCreate(context: Context) {
        AwLogger.init { debug = BuildConfig.DEBUG }
    }
}

class NetworkInit : AppInitializer() {
    override val name = "Network"
    override val priority = InitPriority.NORMAL
    override val dependencies = listOf("Logger")
    override fun onCreate(context: Context) {
        AwNet.init(context)
    }
    override fun onFailed(error: Throwable) {
        AwLogger.e("Network init failed", error)
    }
}
```

注册并启动：

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AwStartup.register(LoggerInit())
        AwStartup.register(NetworkInit())
        AwStartup.start(this)
    }
}
```

### 3. 优先级说明

| 优先级 | 执行时机 | 线程 | 适用场景 |
|--------|----------|------|----------|
| `IMMEDIATELY` | 最先执行 | 主线程同步 | 崩溃收集、日志 |
| `NORMAL` | IMMEDIATELY 之后 | 主线程同步 | 网络、图片、存储 |
| `DEFERRED` | 主线程空闲后 | 主线程异步（IdleHandler） | 统计上报、推送 |
| `BACKGROUND` | 子线程异步 | 后台线程池 | 缓存清理、数据预热 |
| `Custom` | 自定义 | 自定义 Executor | 特殊调度需求 |

### 4. 自定义优先级

```kotlin
val ioPriority = InitPriority.Custom(5, Executors.newSingleThreadExecutor())

class DbPreloadInit : AppInitializer() {
    override val name = "DbPreload"
    override val priority = ioPriority
    override fun onCreate(context: Context) { ... }
}
```

### 5. 协程初始化器

```kotlin
class DbInit : SuspendAppInitializer() {
    override val name = "Database"
    override val priority = InitPriority.BACKGROUND
    override suspend fun onCreateSuspend(context: Context) {
        val db = Room.databaseBuilder(context, AppDb::class.java, "app.db").build()
        db.openHelper.writableDatabase
    }
}
```

### 6. 失败策略

```kotlin
AwStartup.init(this) {
    // 默认 CONTINUE：单个失败不影响后续
    // ABORT_DEPENDENTS：依赖失败者的任务自动跳过
    failStrategy(FailStrategy.ABORT_DEPENDENTS)
    immediately("Config") { loadConfig() }
    normal("Service", deps = listOf("Config")) { initService() }
}
```

### 7. 查看启动报告

```kotlin
val report = AwStartup.getReport()
report.forEach {
    Log.d("Startup", "${it.name} [${it.priority}] ${it.costMillis}ms ${if (it.success) "OK" else "FAIL"}")
}
Log.d("Startup", "同步总耗时: ${AwStartup.getSyncCostMillis()}ms")
```

### 8. 等待后台任务完成

```kotlin
// 无限等待
AwStartup.await()

// 带超时（毫秒），返回是否在超时前完成
val completed = AwStartup.await(3000)
```

### 9. 高级配置

```kotlin
AwStartup.init(this) {
    backgroundThreads(4)                              // 自定义线程数，默认 CPU 核心数
    executor(Executors.newFixedThreadPool(2))          // 自定义线程池
    failStrategy(FailStrategy.ABORT_DEPENDENTS)        // 失败策略
    logger(true)                                      // 输出启动日志到 Logcat
    onResult { result ->                              // 结果回调（主线程）
        Log.d("Startup", "${result.name} ${result.costMillis}ms")
    }
}
```

## API

### AwStartup

| 方法 | 说明 |
|------|------|
| `init(context, block)` | DSL 方式初始化并启动（推荐） |
| `register(initializer)` | 手动注册初始化器 |
| `start(context)` | 启动初始化流程 |
| `getReport()` | 获取初始化结果列表 |
| `getSyncCostMillis()` | 获取同步初始化耗时 |
| `isStarted` | 是否已启动 |
| `await()` | 无限等待所有后台任务完成 |
| `await(timeoutMillis)` | 带超时等待后台任务完成 |
| `reset()` | 重置状态（测试用） |

### StartupConfig DSL

| 方法 | 说明 |
|------|------|
| `add(initializer)` | 添加初始化器实例 |
| `immediately(name, deps, init)` | 快捷添加 IMMEDIATELY 初始化器 |
| `normal(name, deps, init)` | 快捷添加 NORMAL 初始化器 |
| `deferred(name, deps, init)` | 快捷添加 DEFERRED 初始化器 |
| `background(name, deps, init)` | 快捷添加 BACKGROUND 初始化器 |
| `onResult(callback)` | 设置结果回调 |
| `backgroundThreads(count)` | 设置后台线程数（默认 CPU 核心数） |
| `executor(executorService)` | 设置自定义线程池 |
| `failStrategy(strategy)` | 设置失败策略 |
| `logger(enabled)` | 是否输出启动日志 |

### AppInitializer

| 属性/方法 | 说明 |
|-----------|------|
| `name` | 唯一标识，用于依赖引用和日志 |
| `priority` | 初始化优先级 |
| `dependencies` | 依赖的初始化器名称列表 |
| `onCreate(context)` | 执行初始化逻辑 |
| `onCompleted()` | 初始化成功回调（可选） |
| `onFailed(error)` | 初始化失败回调（可选） |

### SuspendAppInitializer

| 属性/方法 | 说明 |
|-----------|------|
| `onCreateSuspend(context)` | 协程初始化逻辑 |

### FailStrategy

| 值 | 说明 |
|----|------|
| `CONTINUE` | 单个失败后继续执行（默认） |
| `ABORT_DEPENDENTS` | 依赖失败者的任务跳过执行 |

## 许可证

Apache License 2.0，详见 [LICENSE](LICENSE)。
