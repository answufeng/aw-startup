# aw-startup

[![JitPack](https://jitpack.io/v/answufeng/aw-startup.svg)](https://jitpack.io/#answufeng/aw-startup)

> **aw-\*** 生态：同组织内另有多个传统 **View / XML** 向的基础库；本工程基线 **minSdk 24**、**JDK 17**（demo 验证 **compileSdk 35**）。

在 `Application` 中集中管理应用启动阶段初始化：多优先级、**依赖有向图、拓扑排序、环检测**；支持主线程同步、**Idle 延后**、**后台池并发**、协程、失败策略与可观测性。

---

## 目录

| 想做的事 | 跳转 |
|----------|------|
| 一行依赖 + 最小示例 | [依赖与 Quick Start](#依赖与-quick-start) |
| 发版/CI、Demo 手测 | [工程与发版](#工程与发版) |
| 先读：失败、超时、DEFERRED 坑 | [集成前必读](#集成前必读) |
| 全量能力说明 | [能力一览](#能力一览) |
| 与 AndroidX Startup 区别 | [对比表](#与-androidx-startup-对比) |
| 类/协程/DSL/迁移 | [进阶与示例](#进阶与示例) |
| 执行流、并发、R8 | [架构与线程安全 / ProGuard](#架构设计) |
| 表格式 API | [API 参考](#api-参考) |
| 排错与细则 | [FAQ](#faq) |

---

## 依赖与 Quick Start

### 依赖

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://jitpack.io") }
    }
}
```

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("com.github.answufeng:aw-startup:1.0.0")
}
```

### 三步接入

1. 添加上方依赖。  
2. 在 `Application.onCreate` 中调用 `AwStartup.init`（主进程可 `mainProcessOnly = true`）。  
3. 用 `getReport()` / `getSyncCostMillis()` 查看结果（可选 `logger(true)` 打全量报告）。

**示例：**

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AwStartup.init(this, mainProcessOnly = true) {
            immediately("Logger") { AwLogger.init() }
            normal("Network", "Logger") { AwNet.init(it) }
            deferred("Analytics", "Network") { AwAnalytics.init(it) }
            background("CacheCleaner") { AwCache.clean(it) }
        }
    }
}
```

```kotlin
val report = AwStartup.getReport()
report.forEach {
    Log.d("Startup", "${it.name} [${it.priority}] ${it.costMillis}ms")
}
Log.d("Startup", "同步总耗时: ${AwStartup.getSyncCostMillis()}ms")
```

### 工程与发版

| 项 | 说明 |
|----|------|
| CI | [`.github/workflows/ci.yml`](.github/workflows/ci.yml)：`assembleRelease`、`ktlintCheck`、`lintRelease`、`:demo:assembleRelease` |
| 本地 | `./gradlew :aw-startup:assembleRelease :aw-startup:ktlintCheck :aw-startup:lintRelease :demo:assembleRelease` |
| 演示 | [demo/DEMO_MATRIX.md](demo/DEMO_MATRIX.md) · 应用内 **「演示清单」** |
| 发版前 | 搞清 **DEFERRED 超时** 与 **FailStrategy**；低内存、后台冷启动、多进程各手测一次 |

---

## 集成前必读

### 能力对照（失败、超时、DEFERRED）

| 项 | 说明 |
|----|------|
| **FailStrategy** | `CONTINUE`：单任务失败不挡后续。`ABORT_DEPENDENTS`：依赖链上依赖失败方则**跳过**（与 `InitResult.skipped` 等配合）。 |
| **单任务 timeout** | 对 `BACKGROUND`、以及走执行器的 `SuspendInitializer` 等可强约束；**主线程阶段**的硬超时以日志/策略为主（无法像线程任务一样硬杀）。 |
| **DEFERRED + `deferredTimeoutMillis`** | Idle 未在时限内排完时，**移除 IdleHandler 并在主线程把剩余项跑完**，避免永久饿死。 |
| **建议手测** | 仓库 [demo](demo) 可对照；发版前看 **低内存、后台冷启动** 下报告与 `await`。 |

### 常见误用

| 误用 | 后果 | 建议 |
|------|------|------|
| 大 IO/网络放进 `IMMEDIATELY` | 启动卡、ANR | 只放极轻量；重活 `NORMAL` / `BACKGROUND` |
| 以为 DEFERRED 必「首帧后马上」 | 主线程一直忙时一直推迟 | 配好 `deferredTimeoutMillis`；关键路径不绑 DEFERRED |
| `await` 超时当「全失败」 | 后台可能仍在跑 | 超时作**未就绪**；日志对齐 `getReport()` |

---

## 能力一览

**调度**

- 四级内置：`IMMEDIATELY` → `NORMAL` → `DEFERRED` → `BACKGROUND`；`InitPriority.Custom` 可带自定义 `Executor`
- 同优先组内**依赖拓扑排序**；**环**与非法依赖在启动时校验
- `DEFERRED`：在**第一次**主线程 `queueIdle` 中**按序跑完**本组（可配**超时**后于主线程继续执行未完成任务）
- `BACKGROUND`：线程池 + `CountDownLatch` 等保证与依赖/失败策略一致
- 默认池：**无界队列** + `AbortPolicy`（拒绝时**不**用 `CallerRunsPolicy` 把任务挤回**调用方线程**，避免在同步段误上主线程）

**可靠性与可观测性**

- 单任务失败**可**不阻断全链（`FailStrategy`）；**按初始化器**再覆写策略
- **重试**、**超时**、**`enabled`** 条件执行、**`StartupStore` 产物流转**
- **`onResult` / `onProgress`**、**`isInitialized`、报告与耗时、自定义 `StartupLogger`**
- **`mainProcessOnly`、**`await(timeout)`** 等

**开发体验**

- **DSL**：`immediately` / `normal` / `deferred` / `background` 及 `suspend*`
- **DSL 回调** `onCompleted` / `onFailed`；**类**继承 `StartupInitializer` / `SuspendInitializer`

---

## 与 AndroidX Startup 对比

| 特性 | AndroidX Startup | aw-startup |
|------|-----------------|-----------:|
| 优先级分级 | 无 | 4 级 + Custom |
| 依赖排序 | 有 | 有 |
| 环检测 | 无 | 有 |
| 后台/Idle/DSL/报告/失败策略/主进程/进度/重试/超时/Store/… | 无或弱 | 有（见上） |

更细的逐项对比见历史说明；**迁移**见文末 [从 AndroidX Startup 迁移](#从-androidx-startup-迁移)。

---

## 演示

仓库 **demo** 覆盖报告、`await`、`StartupStore`、多优先级等；手测项见 [demo/DEMO_MATRIX.md](demo/DEMO_MATRIX.md)。

---

## 进阶与示例

### 子类 + `register` / `start`

```kotlin
class LoggerInit : StartupInitializer() {
    override val name = "Logger"
    override val priority = InitPriority.IMMEDIATELY
    override fun onCreate(context: Context) { AwLogger.init { debug = BuildConfig.DEBUG } }
}

// Application 中
AwStartup.register(LoggerInit())
AwStartup.register(/* … */)
AwStartup.start(this)
```

### 优先级与场景

| 优先级 | 执行时机 | 线程 | 典型 |
|--------|----------|------|------|
| `IMMEDIATELY` | 最早 | 主同步 | 日志、监控 |
| `NORMAL` | 其后 | 主同步 | 网络、大库、图片 |
| `DEFERRED` | Idle 组 | 主（Idle/超时兜底） | 非关键、可晚点 |
| `BACKGROUND` | 组内并发 | 池 | 预热、重 IO |
| `Custom` + `executor` | 自定义 | 该执行器 | 特殊队列 |

`Custom` 无 `executor` 时，按 `ordinal` 与内置档对齐，落在 SYNC / IDLE / CONCURRENT 之一（见 KDoc）。

### 自定义 `Custom(ordinal, executor?)`

```kotlin
val ioPriority = InitPriority.Custom(5, Executors.newSingleThreadExecutor())

class DbPreloadInit : StartupInitializer() {
    override val name = "DbPreload"
    override val priority = ioPriority
    override fun onCreate(context: Context) { /* ... */ }
}
```

### 协程 `SuspendInitializer`

```kotlin
class DbInit : SuspendInitializer() {
    override val name = "Database"
    override val priority = InitPriority.BACKGROUND
    override suspend fun onCreateSuspend(context: Context) { /* ... */ }
}
```

### 失败策略

```kotlin
AwStartup.init(this) {
    failStrategy(FailStrategy.ABORT_DEPENDENTS)
    immediately("Config") { loadConfig() }
    normal("Service", "Config") { initService() }
}

class ServiceInit : StartupInitializer() {
    override val name = "Service"
    override val priority = InitPriority.NORMAL
    override val dependencies = listOf("Config")
    override val failStrategy = FailStrategy.ABORT_DEPENDENTS
    override fun onCreate(context: Context) { initService() }
}
```

### 重试与全局/延后超时

```kotlin
class NetworkInit : StartupInitializer() {
    override val name = "Network"
    override val priority = InitPriority.NORMAL
    override val retryCount = 2
    override fun onCreate(context: Context) { AwNet.init(context) }
}

class SlowInit : StartupInitializer() {
    override val name = "Slow"
    override val priority = InitPriority.BACKGROUND
    override val timeoutMillis = 5000
    override fun onCreate(context: Context) { slowOperation() }
}

AwStartup.init(this) {
    timeout(3000)
    deferredTimeout(5000)
}
```

### DSL 回调

```kotlin
AwStartup.init(this) {
    immediately("Logger", onCompleted = { Log.d("Startup", "Logger ready") }) { AwLogger.init() }
    normal("Network", "Logger", onFailed = { Log.e("Startup", "Network failed", it) }) { AwNet.init(it) }
}
```

### 高级 `init` 块

```kotlin
AwStartup.init(this) {
    backgroundThreads(4)
    executor(Executors.newFixedThreadPool(2))
    failStrategy(FailStrategy.ABORT_DEPENDENTS)
    timeout(3000)
    deferredTimeout(5000)
    logger(true)
    onResult { r -> Log.d("Startup", "${r.name} ${r.costMillis}ms") }
}
```

### 其它速查

- **状态**：`isInitialized(name)`  
- **进度分母**：`getReport().size` 为**当前已出结果条**；`getTotalInitializerCount()` 为**计划总数**（与 `onProgress` 的 `total` 一致）  
- **等后台**：`AwStartup.await(3000)`，勿在主线程死等无超时版（已标 `@Deprecated`）  
- **主进程**：`AwStartup.isMainProcess(this)` 与 `init(..., mainProcessOnly = true)`  
- **高级 `init` 块**：`backgroundThreads`、`executor`、`logger`、`onResult` 等见下文 **API 参考**

---

## 架构设计

### 执行流（简图）

```
init / start
  → Graph 校验（名唯一、依赖存在、优先级可执行、无环）
  → 全局拓扑序 + 按优先级分组
  → StartupRunner
      SYNC  (IMMEDIATELY / NORMAL / 部分 Custom)     主线程顺序
      IDLE  (DEFERRED / 部分 Custom)                 Idle，首段 idle 可跑完一组；可超时兜底
      CONCURRENT (BACKGROUND / Custom+pool)          池内并发，依赖用 Future/join 等配合
```

### 依赖与复杂度

- 拓扑：Kahn（BFS），O(V+E)  
- 环：DFS + 染色

### 线程安全

- 结果用 `ConcurrentLinkedQueue` 等，多线程/Idle 下追加安全  
- `getReport()` 为**快照**；未跑完时条数会小于 `getTotalInitializerCount()`  
- `onResult`：在**主线程**调你的闭包（若内部在非主完成会先 post）

### ProGuard

库随 **AAR 带上 `consumer-rules.pro`**，一般**无需在宿主里再写一份**。会保留你继承的 `StartupInitializer` / `SuspendInitializer` 等；`internal` 包下报告、嵌套类名以 JVM 全名/ `$` 形式 keep。  
若自带 `Executor`：需保证**拒绝策略**、**shutdown 时机**与 [FAQ 自定义池](#faq) 一致，否则会出现「不执行 / await 不结束」类问题。  
**子类**若混淆，**勿混淆 `name` 串**（依赖名依赖此字符串）。

---

## API 参考

### `AwStartup`

| 方法 / 属性 | 说明 |
|-------------|------|
| `init` / `register` / `start` | 见上 |
| `getReport` | 当前结果快照（含耗时等） |
| `getSyncCostMillis` | 同步段耗时 |
| `getTotalInitializerCount` | 与 `onProgress` 的 `total` 一致 |
| `isInitialized` | 名是否已产出终态（成/败/跳） |
| `getStore` | 各初始化器间共享数据 |
| `isStarted` | 是否已 `start` |
| `await(timeoutMs)` / `await()`（弃用） | 等 Idle+后台 完毕 |
| `isMainProcess` | 主进程判断 |
| `reset` | 主要给测试/特殊场景，清状态 + 等后台 + 关池 |

### `StartupConfig`（DSL 块内）

`immediately` / `normal` / `deferred` / `background`、对应 `suspend*`、`onResult` / `onProgress`、`backgroundThreads` / `executor`、`failStrategy`、`timeout` / `deferredTimeout`、`logger` 等 —— 同上文及 IDE 补全。

### `StartupInitializer` / `SuspendInitializer` / `InitResult` / `FailStrategy`

见源码 KDoc 与下表同名的「属性/方法」列（`name`、`priority`、`dependencies`、`failStrategy`、`timeoutMillis`、`retryCount`、`enabled`、`onCreate`、`onCreateSuspend`、…）。

---

## 最佳实践

1. `IMMEDIATELY` 尽量短；`NORMAL` 控制**同步段总**耗时。  
2. `DEFERRED` 配 `deferredTimeout`；`BACKGROUND` 配 per-task/全局 `timeout` 防挂死。  
3. 多进程用 `mainProcessOnly` + `isMainProcess` + `enabled`。  
4. 强依赖用 `isInitialized` / Store；波动 IO 上 `retryCount`。  
5. 大流量后台用**合适池与队列**，慎 `CallerRunsPolicy` 让重任务跑回主路径。

---

## 从 AndroidX Startup 迁移

**1. 依赖**

```kotlin
// 移除
implementation("androidx.startup:startup-runtime:1.1.1")
// 添加
implementation("com.github.answufeng:aw-startup:1.0.0")
```

**2. Initializer 写法**

```kotlin
// 旧：AndroidX Startup
class LoggerInitializer : Initializer<Unit> {
    override fun create(context: Context) { AwLogger.init() }
    override fun dependencies() = emptyList<Class<out Initializer<*>>>()
}

// 新：aw-startup
class LoggerInit : StartupInitializer() {
    override val name = "Logger"
    override val priority = InitPriority.IMMEDIATELY
    override fun onCreate(context: Context) { AwLogger.init() }
}
```

**3. 移除 `InitializationProvider` 等 AndroidX 清单配置，改在 `Application` 中：**

```kotlin
AwStartup.init(this, mainProcessOnly = true) {
    immediately("Logger") { AwLogger.init() }
}
```

**4. 依赖从「类」改为「名」：**

```kotlin
class NetworkInit : StartupInitializer() {
    override val name = "Network"
    override val dependencies = listOf("Logger")
    override fun onCreate(context: Context) { }
}
```

---

## FAQ

**Q: 超时在哪些阶段真正「能砍断」？**  
A: 以 **BACKGROUND/池上跑的任务** 与 **可放到执行器上的 Suspend 路径** 为主；纯主线程块只能 warn + 策略，无法像取消 Future 一样硬停。

**Q: DEFERRED 是「多轮闲一下跑一个」还是「一轮跑完」？**  
A: 与当前实现一致：**第一次**主线程 `queueIdle` 里**按序连续跑**本组；超时未跑完的由框架在主线程**接着跑完**（并移除后续 Idle 依赖）。

**Q: `BACKGROUND` 依赖 `NORMAL` 要不要再写啥？**  
A: 不用。提交后台任务**之前**所有 SYNC 已跑完。

**Q: 子进程、Store、自定义日志、不混淆 `name`？**  
A: 子进程/Store/日志见上文**进阶**与 `StartupStore` / `StartupLogger`；混淆保留 **子类**与 **`name` 字面值**可解析。

**Q: 为何弃用无参 `await()`？**  
A: 可能**永久阻塞**；请用 `await(timeout)`。

**Q: 自定义 `executor` 不执行或 `await` 永远不完？**  
A: 队列满、**拒绝**、**已 shutdown** 等会按失败/结束路径处理；请检查容与拒绝策略、生命周期，或先用库**默认**池。  

**Q: ProGuard 要再加吗？**  
A: 一般**不用**加；继承类勿混淆**名称依赖用字符串**的 `name`。

---

## 许可

Apache License 2.0，见 [LICENSE](LICENSE).
