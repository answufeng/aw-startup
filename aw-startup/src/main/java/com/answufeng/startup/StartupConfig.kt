package com.answufeng.startup

import android.content.Context

/**
 * 初始化失败策略。
 *
 * - [CONTINUE] — 单个初始化器失败后，后续初始化器继续执行（默认）
 * - [ABORT_DEPENDENTS] — 初始化器失败后，依赖它的初始化器跳过执行
 */
enum class FailStrategy {

    /** 单个初始化器失败后，后续初始化器继续执行。 */
    CONTINUE,

    /** 初始化器失败后，依赖它的初始化器跳过执行。 */
    ABORT_DEPENDENTS
}

/**
 * 启动配置 DSL。
 *
 * 在 [AwStartup.init] 中使用：
 * ```kotlin
 * AwStartup.init(this) {
 *     immediately("Logger") { AwLogger.init() }
 *     normal("Network", deps = listOf("Logger")) { AwNet.init(it) }
 *     deferred("Analytics") { AwAnalytics.init(it) }
 *     background("CacheCleaner") { AwCache.clean(it) }
 *     backgroundThreads(4)
 *     failStrategy(FailStrategy.ABORT_DEPENDENTS)
 *     onResult { Log.d("Startup", "${it.name} ${it.costMillis}ms") }
 *     logger(true)
 * }
 * ```
 */
class StartupConfig {

    internal val initializers = mutableListOf<AppInitializer>()
    internal var resultCallback: ((InitResult) -> Unit)? = null
    internal var logger: Boolean = false
    internal var backgroundThreadCount: Int = Runtime.getRuntime().availableProcessors()
    internal var customExecutor: java.util.concurrent.ExecutorService? = null
    internal var failStrategy: FailStrategy = FailStrategy.CONTINUE

    /**
     * 添加初始化器实例。
     *
     * @param initializer 初始化器实例
     */
    fun add(initializer: AppInitializer) {
        initializers.add(initializer)
    }

    /**
     * 设置初始化结果回调，保证在主线程执行。
     *
     * @param callback 结果回调
     */
    fun onResult(callback: (InitResult) -> Unit) {
        resultCallback = callback
    }

    /**
     * 是否输出启动日志。
     *
     * @param enabled 是否启用，默认 true
     */
    fun logger(enabled: Boolean = true) {
        logger = enabled
    }

    /**
     * 设置后台线程池大小。
     *
     * @param count 线程数，必须大于 0，默认为 CPU 核心数
     * @throws IllegalArgumentException count <= 0
     */
    fun backgroundThreads(count: Int) {
        require(count > 0) { "后台线程数必须大于 0，当前值：$count" }
        backgroundThreadCount = count
    }

    /**
     * 设置自定义后台线程池。
     *
     * 设置后 [backgroundThreads] 将被忽略。
     *
     * @param executor 自定义线程池
     */
    fun executor(executor: java.util.concurrent.ExecutorService) {
        customExecutor = executor
    }

    /**
     * 设置初始化失败策略。
     *
     * @param strategy 失败策略，默认 [FailStrategy.CONTINUE]
     */
    fun failStrategy(strategy: FailStrategy) {
        failStrategy = strategy
    }

    /**
     * DSL 快捷方式：添加 IMMEDIATELY 优先级初始化器。
     *
     * @param name 初始化器名称
     * @param deps 依赖列表
     * @param init 初始化逻辑
     */
    fun immediately(name: String, deps: List<String> = emptyList(), init: (Context) -> Unit) {
        add(object : AppInitializer() {
            override val name = name
            override val priority = InitPriority.IMMEDIATELY
            override val dependencies = deps
            override fun onCreate(context: Context) = init(context)
        })
    }

    /**
     * DSL 快捷方式：添加 NORMAL 优先级初始化器。
     *
     * @param name 初始化器名称
     * @param deps 依赖列表
     * @param init 初始化逻辑
     */
    fun normal(name: String, deps: List<String> = emptyList(), init: (Context) -> Unit) {
        add(object : AppInitializer() {
            override val name = name
            override val priority = InitPriority.NORMAL
            override val dependencies = deps
            override fun onCreate(context: Context) = init(context)
        })
    }

    /**
     * DSL 快捷方式：添加 DEFERRED 优先级初始化器。
     *
     * DEFERRED 初始化器通过 IdleHandler 在主线程空闲时执行，
     * 不会阻塞首帧渲染和输入事件。
     *
     * @param name 初始化器名称
     * @param deps 依赖列表
     * @param init 初始化逻辑
     */
    fun deferred(name: String, deps: List<String> = emptyList(), init: (Context) -> Unit) {
        add(object : AppInitializer() {
            override val name = name
            override val priority = InitPriority.DEFERRED
            override val dependencies = deps
            override fun onCreate(context: Context) = init(context)
        })
    }

    /**
     * DSL 快捷方式：添加 BACKGROUND 优先级初始化器。
     *
     * BACKGROUND 初始化器在后台线程池中并发执行，
     * 使用 CountDownLatch 保证依赖顺序。
     *
     * @param name 初始化器名称
     * @param deps 依赖列表
     * @param init 初始化逻辑
     */
    fun background(name: String, deps: List<String> = emptyList(), init: (Context) -> Unit) {
        add(object : AppInitializer() {
            override val name = name
            override val priority = InitPriority.BACKGROUND
            override val dependencies = deps
            override fun onCreate(context: Context) = init(context)
        })
    }
}
