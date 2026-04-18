# Changelog

## 1.1.0

### Added
- `isInitialized(name)` 便捷查询初始化器是否已完成
- `isMainProcess(context)` 主进程判断工具方法
- `init(context, mainProcessOnly, block)` 支持仅主进程初始化
- `InitResult.skipped` 字段区分失败和跳过状态
- `AppInitializer.failStrategy` 初始化器级别失败策略
- `AppInitializer.timeoutMillis` 单个初始化器超时控制
- `AppInitializer.retryCount` 初始化器重试支持
- `StartupConfig.timeout(millis)` 全局默认超时配置
- `StartupConfig.deferredTimeout(millis)` DEFERRED 超时保护
- DSL 快捷方法增加 `onCompleted` / `onFailed` 可选回调参数

### Fixed
- **Custom 优先级初始化器不执行**：`Graph` 和 `StartupRunner` 现在正确处理 `InitPriority.Custom`
- **无 BACKGROUND 任务时 `await()` 永远阻塞**：`concurrentLatch` 仅在有并发任务时创建
- **StartupReport 的 results 在后台任务完成前不完整**：`results` 改为实时快照
- **`init()` 会清除之前 `register()` 注册的初始化器**：改为追加方式
- **线程安全问题**：`results`、`completedNames`、`failedInitializers` 统一使用同一把锁

### Changed
- 后台线程池从 `Executors.newFixedThreadPool` 改为 `ThreadPoolExecutor` + 有界队列 + `CallerRunsPolicy`
- 后台线程命名从 `pool-N-thread-M` 改为 `aw-startup-bg-{N}`
- DEFERRED 任务从逐个 IdleHandler 调度改为批量执行
- `StartupReport.results` 从 `val` 改为计算属性，每次返回实时快照

## 1.0.0

- 4 级优先级：IMMEDIATELY、NORMAL、DEFERRED、BACKGROUND
- 拓扑排序，带循环依赖检测
- 后台线程池，使用 CountDownLatch 保证依赖顺序
- DEFERRED 优先级使用 IdleHandler 延迟到空闲时执行
- 错误隔离：单个初始化器失败不影响后续
- DSL 配置方式，支持结果回调
- 初始化报告，含耗时统计
- 可配置后台线程数
- await 等待后台任务完成
