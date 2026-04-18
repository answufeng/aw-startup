# aw-startup

[![](https://jitpack.io/v/answufeng/aw-startup.svg)](https://jitpack.io/#answufeng/aw-startup)

Android 应用启动初始化库，提供优先级分级、依赖感知的组件初始化，内置拓扑排序与循环依赖检测。

## 特性

- **4 级优先级**：IMMEDIATELY → NORMAL → DEFERRED → BACKGROUND
- **自定义优先级**：`InitPriority.Custom` 支持自定义执行器
- **拓扑排序**：同一优先级内按依赖关系排序，带循环依赖检测
- **后台线程池**：BACKGROUND 优先级使用 CountDownLatch 保证依赖顺序
- **空闲执行**：DEFERRED 优先级通过 IdleHandler 延迟到主线程空闲时执行
- **DEFERRED 超时保护**：可配置超时时间，超时后强制执行
- **错误隔离**：单个初始化器失败不影响后续初始化
- **失败策略**：`FailStrategy.ABORT_DEPENDENTS` 自动跳过依赖失败者的任务
- **初始化器级别失败策略**：每个初始化器可单独配置失败策略
- **重试支持**：初始化器可配置重试次数
- **超时控制**：初始化器可配置超时时间，超时自动取消
- **DSL 配置**：简洁的 Kotlin DSL 方式注册初始化器（`immediately` / `normal` / `deferred` / `background`）
- **DSL 协程支持**：`suspendImmediately` / `suspendNormal` / `suspendDeferred` / `suspendBackground` 支持 suspend 函数
- **DSL 回调**：DSL 方式支持 `onCompleted` / `onFailed` 回调
- **结果回调**：每个初始化器完成后触发回调，回调保证在主线程执行
- **进度回调**：`onProgress(completed, total)` 实时追踪初始化进度
- **初始化报告**：含耗时统计，可随时获取（实时更新）
- **状态查询**：`isInitialized(name)` 便捷查询初始化器是否已完成
- **可配置线程数**：支持自定义后台线程池大小或自定义 ExecutorService
- **有界线程池**：使用有界队列 + CallerRunsPolicy，防止 OOM
- **线程命名**：后台线程命名为 `aw-startup-bg-{N}`，便于调试
- **await 支持**：可等待所有后台任务完成，支持超时
- **协程兼容**：`SuspendAppInitializer` 支持 `suspend` 初始化逻辑
- **条件执行**：`enabled` 属性支持运行时条件控制初始化器是否执行
- **数据共享**：`StartupStore` 支持初始化器间传递初始化产物
- **自定义日志**：`StartupLogger` 接口支持自定义日志实现
- **主进程判断**：`mainProcessOnly` 参数自动跳过子进程初始化
- **onFailed 回调**：初始化器可自行处理失败（降级/重试）
- **跳过状态**：`InitResult.skipped` 区分失败和跳过

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
| 重试支持 | ❌ | ✅ |
| 超时控制 | ❌ | ✅ |
| 主进程判断 | ❌ | ✅ |
| 状态查询 | ❌ | ✅ |
| DEFERRED 超时保护 | ❌ | ✅ |
| 条件执行 | ❌ | ✅ |
| 初始化器间数据传递 | ❌ | ✅ |
| 进度回调 | ❌ | ✅ |
| 自定义日志 | ❌ | ✅ |

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
    implementation("com.github.answufeng:aw-startup:1.1.0")
}
```

## 快速开始

### 1. DSL 快捷方式（推荐）

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AwStartup.init(this, mainProcessOnly = true) {
            immediately("Logger") { AwLogger.init() }
            normal("Network", "Logger") { AwNet.init(it) }
            deferred("Analytics", "Network") { AwAnalytics.init(it) }
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
    override val retryCount = 2
    override val timeoutMillis = 5000
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

Custom 优先级的执行策略：
- 若提供 `executor`，使用自定义执行器并发执行
- 若未提供 `executor`，根据 `ordinal` 决定：
  - `ordinal <= 1`（NORMAL 及以下）：主线程同步执行
  - `ordinal <= 2`（DEFERRED）：IdleHandler 延迟执行
  - `ordinal > 2`（BACKGROUND 及以上）：后台线程池并发执行

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
    // 全局策略
    failStrategy(FailStrategy.ABORT_DEPENDENTS)
    immediately("Config") { loadConfig() }
    normal("Service", deps = listOf("Config")) { initService() }
}

// 或初始化器级别策略
class ServiceInit : AppInitializer() {
    override val name = "Service"
    override val priority = InitPriority.NORMAL
    override val dependencies = listOf("Config")
    override val failStrategy = FailStrategy.ABORT_DEPENDENTS
    override fun onCreate(context: Context) { initService() }
}
```

### 7. 重试与超时

```kotlin
// 重试：失败后自动重试
class NetworkInit : AppInitializer() {
    override val name = "Network"
    override val priority = InitPriority.NORMAL
    override val retryCount = 2  // 最多重试2次（共执行3次）
    override fun onCreate(context: Context) { AwNet.init(context) }
}

// 超时：单个初始化器超时
class SlowInit : AppInitializer() {
    override val name = "Slow"
    override val priority = InitPriority.BACKGROUND
    override val timeoutMillis = 5000  // 5秒超时
    override fun onCreate(context: Context) { slowOperation() }
}

// 全局超时配置
AwStartup.init(this) {
    timeout(3000)              // 全局默认超时3秒
    deferredTimeout(5000)      // DEFERRED 任务5秒后强制执行
}
```

### 8. 查看启动报告

```kotlin
val report = AwStartup.getReport()
report.forEach {
    val status = when {
        it.skipped -> "SKIPPED"
        it.success -> "OK"
        else -> "FAIL"
    }
    Log.d("Startup", "${it.name} [${it.priority}] ${it.costMillis}ms $status")
}
Log.d("Startup", "同步总耗时: ${AwStartup.getSyncCostMillis()}ms")
```

### 9. 查询初始化器状态

```kotlin
if (AwStartup.isInitialized("Network")) {
    // 网络已初始化，可以发起请求
}
```

### 10. 等待后台任务完成

```kotlin
// 无限等待
AwStartup.await()

// 带超时（毫秒），返回是否在超时前完成
val completed = AwStartup.await(3000)
```

### 11. 主进程判断

```kotlin
// 仅主进程初始化
AwStartup.init(this, mainProcessOnly = true) {
    // ...
}

// 手动判断
if (AwStartup.isMainProcess(this)) {
    // 主进程
}
```

### 12. DSL 回调

```kotlin
AwStartup.init(this) {
    immediately("Logger",
        onCompleted = { Log.d("Startup", "Logger ready") }
    ) { AwLogger.init() }

    normal("Network",
        "Logger",
        onFailed = { Log.e("Startup", "Network failed", it) }
    ) { AwNet.init(it) }
}
```

### 13. 高级配置

```kotlin
AwStartup.init(this) {
    backgroundThreads(4)                              // 自定义线程数，默认 CPU 核心数
    executor(Executors.newFixedThreadPool(2))          // 自定义线程池
    failStrategy(FailStrategy.ABORT_DEPENDENTS)        // 失败策略
    timeout(3000)                                     // 全局默认超时
    deferredTimeout(5000)                             // DEFERRED 超时保护
    logger(true)                                      // 输出启动日志到 Logcat
    onResult { result ->                              // 结果回调（主线程）
        Log.d("Startup", "${result.name} ${result.costMillis}ms")
    }
}
```

## 架构设计

### 执行流程

```
AwStartup.init/start
    │
    ▼
Graph.validate()  ← 名称唯一性 / 依赖存在性 / 优先级一致性 / 无循环依赖
    │
    ▼
Graph.getGroups() ← 全局拓扑排序（Kahn BFS）→ 按优先级 ordinal 分组
    │
    ▼
StartupRunner.run()
    ├── SYNC 组（IMMEDIATELY, NORMAL, Custom≤1）→ 主线程顺序执行
    ├── IDLE 组（DEFERRED, Custom≤2）→ IdleHandler 空闲执行（可配超时保护）
    └── CONCURRENT 组（BACKGROUND, Custom>2, Custom+executor）→ 线程池并发执行
```

### 依赖排序

使用 Kahn 算法（BFS）进行全局拓扑排序，时间复杂度 O(V+E)。循环依赖检测使用 DFS + 着色法。

### 线程安全

- `results`、`completedNames`、`failedInitializers` 共享同一把锁
- `StartupReport.results` 返回实时快照
- `onResult` 回调保证在主线程执行

### ProGuard

库已内置 `consumer-rules.pro`，无需额外配置。keep 规则保留 `AppInitializer` 实现类及其方法，确保反射和 DSL 创建的匿名子类正常工作。

## API

### AwStartup

| 方法 | 说明 |
|------|------|
| `init(context, block)` | DSL 方式初始化并启动（推荐） |
| `init(context, mainProcessOnly, block)` | DSL 方式，可指定仅主进程初始化 |
| `register(initializer)` | 手动注册初始化器 |
| `register(block)` | DSL 方式注册初始化器，返回自身支持链式调用 |
| `start(context)` | 启动初始化流程 |
| `getReport()` | 获取初始化结果列表（实时快照） |
| `getSyncCostMillis()` | 获取同步初始化耗时 |
| `isInitialized(name)` | 查询初始化器是否已完成 |
| `getStore()` | 获取初始化器间数据共享存储 |
| `isStarted` | 是否已启动 |
| `await()` | 无限等待所有后台任务完成（已废弃，建议使用带超时版本） |
| `await(timeoutMillis)` | 带超时等待后台任务完成 |
| `isMainProcess(context)` | 判断当前是否主进程 |
| `reset()` | 重置状态（测试用） |

### StartupConfig DSL

| 方法 | 说明 |
|------|------|
| `add(initializer)` | 添加初始化器实例 |
| `immediately(name, vararg deps, enabled, init, onCompleted, onFailed)` | 快捷添加 IMMEDIATELY 初始化器 |
| `normal(name, vararg deps, enabled, init, onCompleted, onFailed)` | 快捷添加 NORMAL 初始化器 |
| `deferred(name, vararg deps, enabled, init, onCompleted, onFailed)` | 快捷添加 DEFERRED 初始化器 |
| `background(name, vararg deps, enabled, init, onCompleted, onFailed)` | 快捷添加 BACKGROUND 初始化器 |
| `suspendImmediately(name, vararg deps, enabled, init, onCompleted, onFailed)` | 快捷添加 IMMEDIATELY 协程初始化器 |
| `suspendNormal(name, vararg deps, enabled, init, onCompleted, onFailed)` | 快捷添加 NORMAL 协程初始化器 |
| `suspendDeferred(name, vararg deps, enabled, init, onCompleted, onFailed)` | 快捷添加 DEFERRED 协程初始化器 |
| `suspendBackground(name, vararg deps, enabled, init, onCompleted, onFailed)` | 快捷添加 BACKGROUND 协程初始化器 |
| `onResult(callback)` | 设置结果回调 |
| `onProgress(callback)` | 设置进度回调 |
| `backgroundThreads(count)` | 设置后台线程数（默认 min(4, CPU核心数)） |
| `executor(executorService)` | 设置自定义线程池 |
| `failStrategy(strategy)` | 设置失败策略 |
| `timeout(millis)` | 设置全局默认超时 |
| `deferredTimeout(millis)` | 设置 DEFERRED 超时保护 |
| `logger(enabled)` | 是否输出启动日志 |
| `logger(startupLogger)` | 设置自定义日志实现 |

### AppInitializer

| 属性/方法 | 说明 |
|-----------|------|
| `name` | 唯一标识，用于依赖引用和日志 |
| `priority` | 初始化优先级 |
| `dependencies` | 依赖的初始化器名称列表 |
| `failStrategy` | 初始化器级别失败策略（可选，优先于全局） |
| `timeoutMillis` | 超时时间（毫秒，0=不超时） |
| `retryCount` | 重试次数（0=不重试） |
| `enabled` | 是否启用（false 时跳过执行，视为已完成） |
| `onCreate(context)` | 执行初始化逻辑 |
| `onCompleted()` | 初始化成功回调（可选） |
| `onFailed(error)` | 初始化失败回调（可选） |

### SuspendAppInitializer

| 属性/方法 | 说明 |
|-----------|------|
| `onCreateSuspend(context)` | 协程初始化逻辑 |

### InitResult

| 属性 | 说明 |
|------|------|
| `name` | 初始化器名称 |
| `priority` | 执行优先级 |
| `costMillis` | 执行耗时（毫秒） |
| `success` | 是否执行成功 |
| `error` | 执行失败的异常 |
| `skipped` | 是否因依赖失败或 disabled 而跳过 |

### FailStrategy

| 值 | 说明 |
|----|------|
| `CONTINUE` | 单个失败后继续执行（默认） |
| `ABORT_DEPENDENTS` | 依赖失败者的任务跳过执行 |

## 最佳实践

1. **IMMEDIATELY** 仅用于最关键的初始化（崩溃收集、日志），避免耗时操作
2. **NORMAL** 用于必须在首帧前完成的初始化（网络、图片库），控制总耗时
3. **DEFERRED** 用于非关键初始化（统计、推送），配合 `deferredTimeout` 防止长期不执行
4. **BACKGROUND** 用于耗时操作（缓存预热、数据库迁移），利用 `timeoutMillis` 防止卡死
5. 使用 `mainProcessOnly = true` 避免子进程重复初始化
6. 使用 `isInitialized()` 在业务代码中检查依赖是否就绪
7. 使用 `retryCount` 为网络等不稳定初始化配置重试
8. 使用初始化器级别 `failStrategy` 精细控制失败传播
9. 使用 `enabled` 属性根据运行时条件控制初始化器是否执行
10. 使用 `StartupStore` 在初始化器间传递初始化产物（如数据库实例）
11. 使用 `StartupLogger` 自定义日志实现，便于 APM 监控
12. 使用 `onProgress` 回调展示初始化进度

## 从 AndroidX Startup 迁移

### 1. 替换依赖

```kotlin
// 移除
implementation("androidx.startup:startup-runtime:1.1.1")

// 添加
implementation("com.github.answufeng:aw-startup:1.2.0")
```

### 2. 替换 Initializer

```kotlin
// AndroidX Startup
class LoggerInitializer : Initializer<Unit> {
    override fun create(context: Context) { AwLogger.init() }
    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}

// aw-startup
class LoggerInit : AppInitializer() {
    override val name = "Logger"
    override val priority = InitPriority.IMMEDIATELY
    override fun onCreate(context: Context) { AwLogger.init() }
}
```

### 3. 替换 AndroidManifest 配置

```xml
<!-- 移除 -->
<provider
    android:name="androidx.startup.InitializationProvider"
    android:authorities="${applicationId}.androidx-startup"
    android:exported="false"
    tools:node="merge">
    <meta-data
        android:name="com.example.LoggerInitializer"
        android:value="androidx.startup" />
</provider>
```

```kotlin
// 改为在 Application.onCreate 中初始化
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AwStartup.init(this, mainProcessOnly = true) {
            immediately("Logger") { AwLogger.init() }
        }
    }
}
```

### 4. 依赖声明方式变更

```kotlin
// AndroidX Startup — 类型依赖
class NetworkInitializer : Initializer<Unit> {
    override fun dependencies() = listOf(LoggerInitializer::class.java)
}

// aw-startup — 字符串名称依赖
class NetworkInit : AppInitializer() {
    override val name = "Network"
    override val dependencies = listOf("Logger")
}
```

## FAQ

**Q: 超时控制对哪些优先级生效？**
A: 超时强制取消对 `BACKGROUND` 和 `SuspendAppInitializer` 生效。对于 `IMMEDIATELY`、`NORMAL`、`DEFERRED`，超时后仅输出警告日志（主线程执行无法强制取消）。

**Q: BACKGROUND 任务依赖 NORMAL 任务时，是否需要额外配置？**
A: 不需要。同步任务（IMMEDIATELY/NORMAL）在并发任务（BACKGROUND）提交前已全部完成，依赖关系由执行顺序隐式保证。

**Q: 如何在子进程中执行不同的初始化？**
A: 使用 `enabled` 属性结合 `AwStartup.isMainProcess()` 判断：
```kotlin
AwStartup.init(this) {
    immediately("Logger") { AwLogger.init() }
    normal("Push", enabled = AwStartup.isMainProcess(this@MyApp)) { initPush() }
}
```

**Q: 如何在初始化器间传递数据？**
A: 使用 `StartupStore`：
```kotlin
// 存储方
immediately("Database") { ctx ->
    val db = Room.databaseBuilder(ctx, AppDb::class.java, "app.db").build()
    AwStartup.getStore().put("database", db)
}
// 获取方
normal("Repository", "Database") { ctx ->
    val db = AwStartup.getStore().get<RoomDatabase>("database")
}
```

**Q: 如何自定义日志输出？**
A: 实现 `StartupLogger` 接口并通过 `logger()` 配置：
```kotlin
AwStartup.init(this) {
    logger(object : StartupLogger {
        override fun d(tag: String, msg: String) { myLogger.d(tag, msg) }
        override fun w(tag: String, msg: String, t: Throwable?) { myLogger.w(tag, msg, t) }
        override fun e(tag: String, msg: String, t: Throwable?) { myLogger.e(tag, msg, t) }
    })
}
```

**Q: ProGuard 需要额外配置吗？**
A: 不需要。库已内置 `consumer-rules.pro`，会自动保留 `AppInitializer` 及其子类。如果你使用代码混淆，确保 `AppInitializer` 子类的 `name` 属性不被混淆（因为它用于依赖引用）。

**Q: 为什么 `await()` 被标记为废弃？**
A: 无超时的 `await()` 可能永久阻塞线程。建议使用 `await(timeoutMillis)` 替代。

## 许可证

Apache License 2.0，详见 [LICENSE](LICENSE)。
