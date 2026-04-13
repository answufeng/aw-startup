package com.answufeng.startup

import android.content.Context
import android.util.Log
import androidx.annotation.MainThread
import com.answufeng.startup.internal.Graph
import com.answufeng.startup.internal.StartupReport
import com.answufeng.startup.internal.StartupRunner
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android 应用启动初始化管理器。
 *
 * 支持按优先级分组、依赖感知、拓扑排序的启动任务调度。
 *
 * 使用方式：
 * ```kotlin
 * AwStartup.init {
 *     register(MyDbInitializer())
 *     register(MyNetworkInitializer())
 * }
 * AwStartup.start(application)
 * ```
 *
 * 初始化器按 [InitPriority] 分为四个优先级：
 * - [InitPriority.IMMEDIATELY] — 主线程同步执行，用于最关键的初始化
 * - [InitPriority.NORMAL] — 主线程同步执行，用于常规初始化
 * - [InitPriority.DEFERRED] — 主线程延迟执行，用于非关键初始化
 * - [InitPriority.BACKGROUND] — 后台线程执行，用于耗时操作
 *
 * 同一优先级内的初始化器按依赖关系拓扑排序执行。
 */
object AwStartup {

    private val started = AtomicBoolean(false)
    private val initializers = mutableListOf<AppInitializer>()
    private val backgroundLatch = CountDownLatch(0)
    private var report: StartupReport? = null
    private var config: StartupConfig? = null

    private val backgroundLatches = CopyOnWriteArrayList<CountDownLatch>()

    /**
     * 初始化配置并注册初始化器。
     *
     * 必须在 [start] 之前调用，通常在 [Application.onCreate] 中。
     *
     * @param block DSL 配置块
     */
    @MainThread
    fun init(block: StartupConfig.() -> Unit) {
        config = StartupConfig().apply(block)
        initializers.clear()
        initializers.addAll(config!!.initializers)
    }

    /**
     * 注册单个初始化器。
     *
     * 可在 [init] 之后、[start] 之前调用，追加初始化器。
     *
     * @param initializer 初始化器实例
     */
    fun register(initializer: AppInitializer) {
        initializers.add(initializer)
    }

    /**
     * 启动所有已注册的初始化器。
     *
     * 按优先级分组，同组内按依赖拓扑排序执行。
     * IMMEDIATELY 和 NORMAL 在主线程同步执行；
     * DEFERRED 在主线程延迟执行；
     * BACKGROUND 在后台线程并发执行。
     *
     * @param context 应用上下文
     * @throws IllegalStateException 重复调用时抛出
     */
    @MainThread
    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) {
            throw IllegalStateException("AwStartup already started")
        }

        val appContext = context.applicationContext
        val graph = Graph(initializers.toList())
        graph.validate()

        val runner = StartupRunner(graph, appContext)
        report = runner.run()

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
     * 等待所有后台初始化器执行完成。
     *
     * 适用于需要在后台初始化完成后才能执行的操作。
     * 注意：在主线程调用时会阻塞主线程，请谨慎使用。
     */
    fun await() {
        report?.awaitBackground()
    }
}
