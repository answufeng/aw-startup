package com.answufeng.startup

import android.content.Context
import kotlin.math.min

/**
 * DSL 标记注解，用于限定 [StartupConfig] DSL 的作用域。
 *
 * 该注解确保 DSL 函数只能在 [StartupConfig] 的 lambda 块内调用，
 * 避免 DSL 方法被误用在其他上下文中。
 *
 * @see StartupConfig
 */
@DslMarker
annotation class AwStartupDsl

/**
 * 启动初始化 DSL 配置类。
 *
 * 通过 DSL 方式声明应用启动时需要执行的初始化任务及其配置。
 * 通常在 [AwStartup.init] 或 [AwStartup.register] 的 block 中使用。
 *
 * ### 用法
 * ```kotlin
 * AwStartup.init(this) {
 *     // 全局配置
 *     logger(true)
 *     failStrategy(FailStrategy.ABORT_DEPENDENTS)
 *     timeout(5000)
 *     backgroundThreads(2)
 *
 *     // 同步初始化（主线程，按优先级顺序执行）
 *     immediately("Logger") { AwLogger.init() }
 *     normal("Network", "Logger") { AwNet.init(it) }
 *     deferred("Analytics") { AwAnalytics.init(it) }
 *
 *     // 异步初始化（后台线程池执行）
 *     background("CacheCleaner") { AwCache.clean(it) }
 *
 *     // 协程初始化
 *     suspendBackground("DbWarmup") { warmUpDatabase(it) }
 *
 *     // 回调
 *     onResult { result -> logResult(result) }
 *     onProgress { completed, total -> updateProgress(completed, total) }
 * }
 * ```
 *
 * @see AwStartup.init
 * @see AwStartup.register
 * @see StartupInitializer
 */
@AwStartupDsl
class StartupConfig {

    internal val initializers = mutableListOf<StartupInitializer>()
    internal var resultCallback: ((InitResult) -> Unit)? = null
    internal var progressCallback: ((completed: Int, total: Int) -> Unit)? = null
    internal var logger: Boolean = false
    internal var startupLogger: StartupLogger? = null
    internal var loggerExplicit: Boolean = false
    internal var backgroundThreadCount: Int = min(4, Runtime.getRuntime().availableProcessors())
    internal var backgroundThreadCountExplicit: Boolean = false
    internal var customExecutor: java.util.concurrent.ExecutorService? = null
    internal var customExecutorExplicit: Boolean = false
    internal var failStrategy: FailStrategy = FailStrategy.CONTINUE
    internal var failStrategyExplicit: Boolean = false
    internal var defaultTimeoutMillis: Long = 0
    internal var defaultTimeoutExplicit: Boolean = false
    internal var deferredTimeoutMillis: Long = 0
    internal var deferredTimeoutExplicit: Boolean = false

    /**
     * 添加自定义初始化器实例。
     *
     * @param initializer 初始化器实例
     */
    fun add(initializer: StartupInitializer) {
        initializers.add(initializer)
    }

    /**
     * 设置初始化结果回调。每个初始化器完成（成功或失败）时触发。
     *
     * @param callback 结果回调，接收 [InitResult]
     */
    fun onResult(callback: (InitResult) -> Unit) {
        resultCallback = callback
    }

    /**
     * 设置初始化进度回调。每完成一个初始化器时触发。
     *
     * @param callback 进度回调，参数为 (已完成数, 总数)
     */
    fun onProgress(callback: (completed: Int, total: Int) -> Unit) {
        progressCallback = callback
    }

    /**
     * 启用/禁用内置日志输出。
     *
     * @param enabled 是否启用，默认 true
     */
    fun logger(enabled: Boolean = true) {
        logger = enabled
        loggerExplicit = true
    }

    /**
     * 设置自定义日志输出器。
     *
     * @param logger 日志输出器实例，设置后自动启用日志
     */
    fun logger(logger: StartupLogger) {
        startupLogger = logger
        this.logger = true
        loggerExplicit = true
    }

    /**
     * 设置后台线程池大小。
     *
     * 默认值为 `min(4, CPU核心数)`。仅影响 [InitPriority.BACKGROUND] 任务的并发数。
     *
     * @param count 线程数，必须大于 0
     * @throws IllegalArgumentException 如果 count <= 0
     */
    fun backgroundThreads(count: Int) {
        require(count > 0) { "后台线程数必须大于 0，当前值：$count" }
        backgroundThreadCount = count
        backgroundThreadCountExplicit = true
    }

    /**
     * 设置自定义线程池执行器，替代内置线程池。
     *
     * @param executor 自定义执行器实例
     */
    fun executor(executor: java.util.concurrent.ExecutorService) {
        customExecutor = executor
        customExecutorExplicit = true
    }

    /**
     * 设置全局失败策略。可被单个初始化器的 [StartupInitializer.failStrategy] 覆盖。
     *
     * @param strategy 失败策略，默认 [FailStrategy.CONTINUE]
     */
    fun failStrategy(strategy: FailStrategy) {
        failStrategy = strategy
        failStrategyExplicit = true
    }

    /**
     * 设置全局默认超时时间（毫秒）。0 表示不超时。
     *
     * 作用于未单独设置 [StartupInitializer.timeoutMillis] 的初始化器。
     *
     * @param millis 超时时间（毫秒），不能为负数
     * @throws IllegalArgumentException 如果 millis < 0
     */
    fun timeout(millis: Long) {
        require(millis >= 0) { "超时时间不能为负数，当前值：$millis" }
        defaultTimeoutMillis = millis
        defaultTimeoutExplicit = true
    }

    /**
     * 设置 DEFERRED 优先级初始化器的超时时间（毫秒）。0 表示不超时。
     *
     * DEFERRED 任务在 Idle 回调中执行；超时后会移除 IdleHandler 并在主线程上强制执行剩余任务（并输出警告日志）。
     *
     * @param millis 超时时间（毫秒），不能为负数
     * @throws IllegalArgumentException 如果 millis < 0
     */
    fun deferredTimeout(millis: Long) {
        require(millis >= 0) { "DEFERRED 超时时间不能为负数，当前值：$millis" }
        deferredTimeoutMillis = millis
        deferredTimeoutExplicit = true
    }

    /**
     * 添加 IMMEDIATELY 优先级初始化器（同步，主线程，最先执行）。
     *
     * 适用于必须在 Application.onCreate 返回前完成的核心初始化（如日志、崩溃收集）。
     *
     * @param name          初始化器唯一名称
     * @param deps          依赖的初始化器名称
     * @param enabled       是否启用，默认 true
     * @param timeoutMillis 超时时间（毫秒），0 表示不超时
     * @param retryCount    失败重试次数，0 表示不重试
     * @param onCompleted   成功回调
     * @param onFailed      失败回调
     * @param init          初始化逻辑
     */
    fun immediately(
        name: String,
        vararg deps: String,
        enabled: Boolean = true,
        timeoutMillis: Long = 0,
        retryCount: Int = 0,
        retryIntervalMillis: Long = 0,
        onCompleted: () -> Unit = {},
        onFailed: (Throwable) -> Unit = {},
        init: (Context) -> Unit
    ) {
        add(object : StartupInitializer() {
            override val name = name
            override val priority = InitPriority.IMMEDIATELY
            override val dependencies = deps.toList()
            override val enabled = enabled
            override val timeoutMillis = timeoutMillis
            override val retryCount = retryCount
            override val retryIntervalMillis = retryIntervalMillis
            override fun onCreate(context: Context) = init(context)
            override fun onCompleted() = onCompleted()
            override fun onFailed(error: Throwable) = onFailed(error)
        })
    }

    /**
     * 添加 NORMAL 优先级初始化器（同步，主线程，在 IMMEDIATELY 之后执行）。
     *
     * 适用于需要在 Application.onCreate 中完成但非核心的初始化（如网络框架）。
     *
     * @param name          初始化器唯一名称
     * @param deps          依赖的初始化器名称
     * @param enabled       是否启用，默认 true
     * @param timeoutMillis 超时时间（毫秒），0 表示不超时
     * @param retryCount    失败重试次数，0 表示不重试
     * @param onCompleted   成功回调
     * @param onFailed      失败回调
     * @param init          初始化逻辑
     */
    fun normal(
        name: String,
        vararg deps: String,
        enabled: Boolean = true,
        timeoutMillis: Long = 0,
        retryCount: Int = 0,
        retryIntervalMillis: Long = 0,
        onCompleted: () -> Unit = {},
        onFailed: (Throwable) -> Unit = {},
        init: (Context) -> Unit
    ) {
        add(object : StartupInitializer() {
            override val name = name
            override val priority = InitPriority.NORMAL
            override val dependencies = deps.toList()
            override val enabled = enabled
            override val timeoutMillis = timeoutMillis
            override val retryCount = retryCount
            override val retryIntervalMillis = retryIntervalMillis
            override fun onCreate(context: Context) = init(context)
            override fun onCompleted() = onCompleted()
            override fun onFailed(error: Throwable) = onFailed(error)
        })
    }

    /**
     * 添加 DEFERRED 优先级初始化器（同步，主线程，在首屏渲染后执行）。
     *
     * 适用于非紧急初始化（如统计 SDK），延迟执行可加快首屏显示速度。
     *
     * @param name          初始化器唯一名称
     * @param deps          依赖的初始化器名称
     * @param enabled       是否启用，默认 true
     * @param timeoutMillis 超时时间（毫秒），0 表示不超时
     * @param retryCount    失败重试次数，0 表示不重试
     * @param onCompleted   成功回调
     * @param onFailed      失败回调
     * @param init          初始化逻辑
     */
    fun deferred(
        name: String,
        vararg deps: String,
        enabled: Boolean = true,
        timeoutMillis: Long = 0,
        retryCount: Int = 0,
        retryIntervalMillis: Long = 0,
        onCompleted: () -> Unit = {},
        onFailed: (Throwable) -> Unit = {},
        init: (Context) -> Unit
    ) {
        add(object : StartupInitializer() {
            override val name = name
            override val priority = InitPriority.DEFERRED
            override val dependencies = deps.toList()
            override val enabled = enabled
            override val timeoutMillis = timeoutMillis
            override val retryCount = retryCount
            override val retryIntervalMillis = retryIntervalMillis
            override fun onCreate(context: Context) = init(context)
            override fun onCompleted() = onCompleted()
            override fun onFailed(error: Throwable) = onFailed(error)
        })
    }

    /**
     * 添加 BACKGROUND 优先级初始化器（异步，后台线程池执行）。
     *
     * 适用于耗时操作（如数据库预热、缓存清理），不阻塞主线程。
     *
     * @param name          初始化器唯一名称
     * @param deps          依赖的初始化器名称
     * @param enabled       是否启用，默认 true
     * @param timeoutMillis 超时时间（毫秒），0 表示不超时
     * @param retryCount    失败重试次数，0 表示不重试
     * @param onCompleted   成功回调
     * @param onFailed      失败回调
     * @param init          初始化逻辑
     */
    fun background(
        name: String,
        vararg deps: String,
        enabled: Boolean = true,
        timeoutMillis: Long = 0,
        retryCount: Int = 0,
        retryIntervalMillis: Long = 0,
        onCompleted: () -> Unit = {},
        onFailed: (Throwable) -> Unit = {},
        init: (Context) -> Unit
    ) {
        add(object : StartupInitializer() {
            override val name = name
            override val priority = InitPriority.BACKGROUND
            override val dependencies = deps.toList()
            override val enabled = enabled
            override val timeoutMillis = timeoutMillis
            override val retryCount = retryCount
            override val retryIntervalMillis = retryIntervalMillis
            override fun onCreate(context: Context) = init(context)
            override fun onCompleted() = onCompleted()
            override fun onFailed(error: Throwable) = onFailed(error)
        })
    }

    /**
     * 添加挂起版本的 IMMEDIATELY 优先级初始化器。
     *
     * @param name          初始化器唯一名称
     * @param deps          依赖的初始化器名称
     * @param enabled       是否启用，默认 true
     * @param timeoutMillis 超时时间（毫秒），0 表示不超时
     * @param retryCount    失败重试次数，0 表示不重试
     * @param onCompleted   成功回调
     * @param onFailed      失败回调
     * @param init          挂起初始化逻辑
     */
    fun suspendImmediately(
        name: String,
        vararg deps: String,
        enabled: Boolean = true,
        timeoutMillis: Long = 0,
        retryCount: Int = 0,
        retryIntervalMillis: Long = 0,
        onCompleted: () -> Unit = {},
        onFailed: (Throwable) -> Unit = {},
        init: suspend (Context) -> Unit
    ) {
        add(object : SuspendInitializer() {
            override val name = name
            override val priority = InitPriority.IMMEDIATELY
            override val dependencies = deps.toList()
            override val enabled = enabled
            override val timeoutMillis = timeoutMillis
            override val retryCount = retryCount
            override val retryIntervalMillis = retryIntervalMillis
            override suspend fun onCreateSuspend(context: Context) = init(context)
            override fun onCompleted() = onCompleted()
            override fun onFailed(error: Throwable) = onFailed(error)
        })
    }

    /**
     * 添加挂起版本的 NORMAL 优先级初始化器。
     *
     * @param name          初始化器唯一名称
     * @param deps          依赖的初始化器名称
     * @param enabled       是否启用，默认 true
     * @param timeoutMillis 超时时间（毫秒），0 表示不超时
     * @param retryCount    失败重试次数，0 表示不重试
     * @param onCompleted   成功回调
     * @param onFailed      失败回调
     * @param init          挂起初始化逻辑
     */
    fun suspendNormal(
        name: String,
        vararg deps: String,
        enabled: Boolean = true,
        timeoutMillis: Long = 0,
        retryCount: Int = 0,
        retryIntervalMillis: Long = 0,
        onCompleted: () -> Unit = {},
        onFailed: (Throwable) -> Unit = {},
        init: suspend (Context) -> Unit
    ) {
        add(object : SuspendInitializer() {
            override val name = name
            override val priority = InitPriority.NORMAL
            override val dependencies = deps.toList()
            override val enabled = enabled
            override val timeoutMillis = timeoutMillis
            override val retryCount = retryCount
            override val retryIntervalMillis = retryIntervalMillis
            override suspend fun onCreateSuspend(context: Context) = init(context)
            override fun onCompleted() = onCompleted()
            override fun onFailed(error: Throwable) = onFailed(error)
        })
    }

    /**
     * 添加挂起版本的 DEFERRED 优先级初始化器。
     *
     * @param name          初始化器唯一名称
     * @param deps          依赖的初始化器名称
     * @param enabled       是否启用，默认 true
     * @param timeoutMillis 超时时间（毫秒），0 表示不超时
     * @param retryCount    失败重试次数，0 表示不重试
     * @param onCompleted   成功回调
     * @param onFailed      失败回调
     * @param init          挂起初始化逻辑
     */
    fun suspendDeferred(
        name: String,
        vararg deps: String,
        enabled: Boolean = true,
        timeoutMillis: Long = 0,
        retryCount: Int = 0,
        retryIntervalMillis: Long = 0,
        onCompleted: () -> Unit = {},
        onFailed: (Throwable) -> Unit = {},
        init: suspend (Context) -> Unit
    ) {
        add(object : SuspendInitializer() {
            override val name = name
            override val priority = InitPriority.DEFERRED
            override val dependencies = deps.toList()
            override val enabled = enabled
            override val timeoutMillis = timeoutMillis
            override val retryCount = retryCount
            override val retryIntervalMillis = retryIntervalMillis
            override suspend fun onCreateSuspend(context: Context) = init(context)
            override fun onCompleted() = onCompleted()
            override fun onFailed(error: Throwable) = onFailed(error)
        })
    }

    /**
     * 添加挂起版本的 BACKGROUND 优先级初始化器。
     *
     * @param name          初始化器唯一名称
     * @param deps          依赖的初始化器名称
     * @param enabled       是否启用，默认 true
     * @param timeoutMillis 超时时间（毫秒），0 表示不超时
     * @param retryCount    失败重试次数，0 表示不重试
     * @param onCompleted   成功回调
     * @param onFailed      失败回调
     * @param init          挂起初始化逻辑
     */
    fun suspendBackground(
        name: String,
        vararg deps: String,
        enabled: Boolean = true,
        timeoutMillis: Long = 0,
        retryCount: Int = 0,
        retryIntervalMillis: Long = 0,
        onCompleted: () -> Unit = {},
        onFailed: (Throwable) -> Unit = {},
        init: suspend (Context) -> Unit
    ) {
        add(object : SuspendInitializer() {
            override val name = name
            override val priority = InitPriority.BACKGROUND
            override val dependencies = deps.toList()
            override val enabled = enabled
            override val timeoutMillis = timeoutMillis
            override val retryCount = retryCount
            override val retryIntervalMillis = retryIntervalMillis
            override suspend fun onCreateSuspend(context: Context) = init(context)
            override fun onCompleted() = onCompleted()
            override fun onFailed(error: Throwable) = onFailed(error)
        })
    }
}
