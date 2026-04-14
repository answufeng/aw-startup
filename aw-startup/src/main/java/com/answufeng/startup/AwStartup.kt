package com.answufeng.startup

import android.content.Context
import android.os.Looper
import androidx.annotation.MainThread
import com.answufeng.startup.internal.Graph
import com.answufeng.startup.internal.StartupReport
import com.answufeng.startup.internal.StartupRunner
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android 应用启动初始化管理器。
 *
 * 支持按优先级分组、依赖感知、拓扑排序的启动任务调度。
 *
 * 使用方式：
 * ```kotlin
 * AwStartup.init(this) {
 *     immediately("Logger") { AwLogger.init() }
 *     normal("Network", deps = listOf("Logger")) { AwNet.init(it) }
 *     deferred("Analytics", deps = listOf("Network")) { AwAnalytics.init(it) }
 *     background("CacheCleaner") { AwCache.clean(it) }
 *     onResult { result ->
 *         Log.d("Startup", "${result.name} ${result.costMillis}ms")
 *     }
 * }
 * ```
 *
 * 或使用 `register()` + `start()` 分步调用：
 * ```kotlin
 * AwStartup.register(LoggerInit())
 * AwStartup.register(NetworkInit())
 * AwStartup.start(application)
 * ```
 *
 * 初始化器按 [InitPriority] 分为四个优先级：
 * - [InitPriority.IMMEDIATELY] — 主线程同步执行，用于最关键的初始化
 * - [InitPriority.NORMAL] — 主线程同步执行，用于常规初始化
 * - [InitPriority.DEFERRED] — 主线程 IdleHandler 延迟执行，用于非关键初始化
 * - [InitPriority.BACKGROUND] — 后台线程池并发执行，用于耗时操作
 *
 * 同一优先级内的初始化器按依赖关系拓扑排序执行。
 */
object AwStartup {

    private val started = AtomicBoolean(false)
    private val initializers = mutableListOf<AppInitializer>()
    private var report: StartupReport? = null
    private var config: StartupConfig? = null
    private var runner: StartupRunner? = null

    /** 是否已启动。 */
    val isStarted: Boolean get() = started.get()

    /**
     * DSL 方式初始化并启动（推荐）。
     *
     * 同时完成配置和启动，等价于 `register()` + `start()`。
     * 必须在主线程调用，通常在 [Application.onCreate][android.app.Application.onCreate] 中。
     *
     * @param context 应用上下文
     * @param block DSL 配置块，参见 [StartupConfig]
     */
    @MainThread
    fun init(context: Context, block: StartupConfig.() -> Unit) {
        val cfg = StartupConfig().apply(block)
        config = cfg
        initializers.clear()
        initializers.addAll(cfg.initializers)
        start(context)
    }

    /**
     * 注册单个初始化器。
     *
     * 可在 [init] 之前、[start] 之前调用，追加初始化器。
     * 启动后调用将抛出 [IllegalStateException]。
     * 重复名称将抛出 [IllegalArgumentException]。
     *
     * @param initializer 初始化器实例
     * @throws IllegalStateException 已启动后注册
     * @throws IllegalArgumentException 名称重复
     */
    fun register(initializer: AppInitializer) {
        synchronized(initializers) {
            check(!started.get()) { "AwStartup 已启动，不能再注册初始化器" }
            require(initializers.none { it.name == initializer.name }) {
                "初始化器名称重复：${initializer.name}"
            }
            initializers.add(initializer)
        }
    }

    /**
     * 启动所有已注册的初始化器。
     *
     * 按优先级分组，同组内按依赖拓扑排序执行。
     * IMMEDIATELY 和 NORMAL 在主线程同步执行；
     * DEFERRED 通过 IdleHandler 在主线程空闲时执行；
     * BACKGROUND 在后台线程池并发执行。
     *
     * @param context 应用上下文
     * @throws IllegalStateException 重复调用时抛出
     */
    @MainThread
    fun start(context: Context) {
        check(started.compareAndSet(false, true)) { "AwStartup already started" }

        val appContext = context.applicationContext
        val graph = Graph(initializers.toList())
        graph.validate()

        val r = StartupRunner(graph, appContext, config)
        runner = r
        report = r.run()

        if (config?.logger == true) {
            report?.log()
        }
    }

    /**
     * 获取启动报告，包含每个初始化器的执行耗时和结果。
     *
     * @return 启动报告列表，未启动时返回空列表
     */
    fun getReport(): List<InitResult> = report?.results ?: emptyList()

    /**
     * 获取同步初始化（IMMEDIATELY + NORMAL）的总耗时（毫秒）。
     *
     * @return 总耗时，未启动时返回 0
     */
    fun getSyncCostMillis(): Long = report?.syncCostMillis ?: 0L

    /**
     * 无限等待所有后台初始化器执行完成。
     *
     * 适用于需要在后台初始化完成后才能执行的操作。
     * 注意：在主线程调用时会阻塞主线程，请谨慎使用。
     */
    fun await() {
        report?.awaitBackground()
    }

    /**
     * 带超时等待所有后台初始化器执行完成。
     *
     * 在主线程调用时会输出 Warn 日志提醒。
     *
     * @param timeoutMillis 超时时间（毫秒）
     * @return 是否在超时前完成，未启动时返回 true
     */
    fun await(timeoutMillis: Long): Boolean {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            android.util.Log.w("AwStartup", "在主线程调用 await() 会阻塞 UI，请考虑使用 onResult 回调")
        }
        return report?.awaitBackground(timeoutMillis) ?: true
    }

    /**
     * 重置所有状态，关闭线程池。
     *
     * 主要用于测试场景，允许重新初始化。
     */
    fun reset() {
        synchronized(initializers) {
            runner?.shutdown()
            runner = null
            started.set(false)
            initializers.clear()
            report = null
            config = null
        }
    }
}
