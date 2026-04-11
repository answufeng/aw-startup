package com.answufeng.startup

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.MainThread
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 应用启动初始化管理器，支持按优先级分级初始化，优化应用启动速度。
 *
 * ### 设计理念
 * - **IMMEDIATELY**：主线程同步，最先执行（崩溃收集、日志）
 * - **NORMAL**：主线程同步，常规初始化（网络、图片、存储）
 * - **DEFERRED**：主线程空闲时执行（统计上报、推送）
 * - **BACKGROUND**：子线程异步执行（缓存清理、数据预热）
 *
 * ### 用法
 *
 * #### DSL 方式（推荐）
 * ```kotlin
 * class MyApp : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *         BrickStartup.init(this) {
 *             add(LogInitializer())
 *             add(StoreInitializer())
 *             add(ImageInitializer())
 *             add(AnalyticsInitializer())
 *
 *             // 可选：监听初始化结果
 *             onResult { result ->
 *                 Log.d("Startup", "${result.name}: ${result.costMillis}ms")
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * #### 手动注册方式
 * ```kotlin
 * BrickStartup.register(LogInitializer())
 * BrickStartup.register(StoreInitializer())
 * BrickStartup.start(this)
 * ```
 *
 * ### 查看启动报告
 * ```kotlin
 * val report = BrickStartup.getReport()
 * report.forEach { Log.d("Startup", "${it.name} [${it.priority}] ${it.costMillis}ms") }
 * Log.d("Startup", "同步总耗时: ${BrickStartup.getSyncCostMillis()}ms")
 * ```
 */
object BrickStartup {

    private const val TAG = "aw-startup"

    private val initializers = mutableListOf<AppInitializer>()
    private val results = java.util.Collections.synchronizedList(mutableListOf<InitResult>())
    private val started = AtomicBoolean(false)
    private var resultCallback: ((InitResult) -> Unit)? = null
    private var syncCostMillis = 0L
    private val executorLock = Any()

    @Volatile
    private var backgroundExecutor: ExecutorService? = null

    private fun createBackgroundExecutor(): ExecutorService {
        return Executors.newFixedThreadPool(2) { r ->
            Thread(r, "aw-startup-BG").apply { isDaemon = true }
        }
    }

    private fun ensureBackgroundExecutor(): ExecutorService = synchronized(executorLock) {
        val current = backgroundExecutor
        if (current != null && !current.isShutdown && !current.isTerminated) {
            return current
        }
        return createBackgroundExecutor().also { backgroundExecutor = it }
    }

    private fun shutdownBackgroundExecutor(immediately: Boolean) = synchronized(executorLock) {
        val executor = backgroundExecutor ?: return
        if (immediately) {
            executor.shutdownNow()
        } else {
            executor.shutdown()
        }
        backgroundExecutor = null
    }

    private fun executeBackgroundTask(task: () -> Unit) {
        var lastError: RejectedExecutionException? = null
        repeat(2) {
            val executor = ensureBackgroundExecutor()
            try {
                executor.execute(task)
                return
            } catch (error: RejectedExecutionException) {
                lastError = error
                synchronized(executorLock) {
                    if (backgroundExecutor === executor) {
                        backgroundExecutor = null
                    }
                }
            }
        }
        throw lastError ?: RejectedExecutionException("background executor rejected task")
    }

    /**
     * DSL 方式初始化。
     *
     * ```kotlin
     * BrickStartup.init(context) {
     *     add(LogInitializer())
     *     add(ImageInitializer())
     *     onResult { result -> Log.d("Startup", "${result.name}: ${result.costMillis}ms") }
     * }
     * ```
     *
     * @param context Application Context
     * @param block 配置 DSL
     */
    @MainThread
    fun init(context: Context, block: StartupConfig.() -> Unit) {
        val config = StartupConfig().apply(block)
        resultCallback = config.resultCallback
        config.initializers.forEach { register(it) }
        start(context)
    }

    /**
     * 注册初始化器。
     *
     * @param initializer 初始化器实例
     * @throws IllegalStateException 如果已经调用过 [start]
     */
    fun register(initializer: AppInitializer) {
        check(!started.get()) { "aw-startup 已启动，无法再注册初始化器" }
        require(initializers.none { it.name == initializer.name }) {
            "初始化器名称重复: '${initializer.name}'，每个 AppInitializer.name 必须唯一"
        }
        initializers.add(initializer)
    }

    /**
     * 启动初始化流程。
     *
     * 按优先级分组执行：
     * 1. [InitPriority.IMMEDIATELY] — 主线程同步
     * 2. [InitPriority.NORMAL] — 主线程同步
     * 3. [InitPriority.DEFERRED] — 主线程空闲时
     * 4. [InitPriority.BACKGROUND] — 子线程异步
     *
     * @param context Application Context
     */
    @MainThread
    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) {
            Log.w(TAG, "BrickStartup 已经启动过，忽略重复调用")
            return
        }

        val appContext = context.applicationContext
        val syncStart = System.currentTimeMillis()

        // 按优先级分组
        val grouped = initializers.groupBy { it.priority }

        // 1. IMMEDIATELY — 主线程同步执行
        val immediately = sortByDependencies(grouped[InitPriority.IMMEDIATELY].orEmpty())
        immediately.forEach { runInitializer(it, appContext) }

        // 2. NORMAL — 主线程同步执行
        val normal = sortByDependencies(grouped[InitPriority.NORMAL].orEmpty())
        normal.forEach { runInitializer(it, appContext) }

        syncCostMillis = System.currentTimeMillis() - syncStart

        // 3. DEFERRED — 主线程空闲时执行
        val deferred = sortByDependencies(grouped[InitPriority.DEFERRED].orEmpty())
        if (deferred.isNotEmpty()) {
            val mainHandler = Handler(Looper.getMainLooper())
            Looper.myQueue().addIdleHandler {
                deferred.forEach { initializer ->
                    mainHandler.post { runInitializer(initializer, appContext) }
                }
                false // 只执行一次
            }
        }

        // 4. BACKGROUND — 子线程异步执行，按依赖关系保证顺序
        val background = sortByDependencies(grouped[InitPriority.BACKGROUND].orEmpty())
        if (background.isNotEmpty()) {
            // 为每个 BACKGROUND initializer 创建 latch，依赖完成后才能执行
            val bgNames = background.map { it.name }.toSet()
            val latchMap = background.associate { it.name to CountDownLatch(1) }
            val remaining = java.util.concurrent.atomic.AtomicInteger(background.size)
            background.forEach { initializer ->
                executeBackgroundTask {
                    // 仅等待同为 BACKGROUND 优先级的依赖（跨优先级依赖已在前序阶段完成）
                    for (dep in initializer.dependencies) {
                        if (dep in bgNames) {
                            latchMap[dep]?.await()
                        }
                    }
                    try {
                        runInitializer(initializer, appContext)
                    } finally {
                        latchMap[initializer.name]?.countDown()
                        if (remaining.decrementAndGet() == 0) {
                            shutdownBackgroundExecutor(immediately = false)
                        }
                    }
                }
            }
        }

        Log.i(TAG, buildString {
            appendLine("┌── aw-startup 初始化完成 ──")
            appendLine("│ 同步耗时: ${syncCostMillis}ms")
            appendLine("│ IMMEDIATELY: ${immediately.size} 个")
            appendLine("│ NORMAL: ${normal.size} 个")
            appendLine("│ DEFERRED: ${deferred.size} 个（空闲执行）")
            appendLine("│ BACKGROUND: ${background.size} 个（异步）")
            append("└────────────────────────────")
        })
    }

    /**
     * 获取所有已完成的初始化结果。
     *
     * @return 结果列表（按完成时间顺序）
     */
    fun getReport(): List<InitResult> = synchronized(results) { results.toList() }

    /**
     * 获取同步初始化（IMMEDIATELY + NORMAL）总耗时。
     *
     * @return 毫秒
     */
    fun getSyncCostMillis(): Long = syncCostMillis

    /**
     * 是否已启动。
     */
    val isStarted: Boolean get() = started.get()

    /**
     * 重置（仅用于测试）。
     */
    internal fun reset() {
        shutdownBackgroundExecutor(immediately = true)
        started.set(false)
        initializers.clear()
        synchronized(results) { results.clear() }
        resultCallback = null
        syncCostMillis = 0
    }

    private fun runInitializer(initializer: AppInitializer, context: Context) {
        val start = System.currentTimeMillis()
        var error: Throwable? = null
        try {
            initializer.onCreate(context)
        } catch (e: Throwable) {
            error = e
            Log.e(TAG, "初始化失败: ${initializer.name}", e)
        }
        try {
            initializer.onCompleted()
        } catch (e: Throwable) {
            if (error == null) {
                error = e
                Log.e(TAG, "onCompleted 失败: ${initializer.name}", e)
            } else {
                Log.w(TAG, "onCompleted 异常被忽略（onCreate 已失败）: ${initializer.name}", e)
            }
        }
        val cost = System.currentTimeMillis() - start
        val result = InitResult(
            name = initializer.name,
            priority = initializer.priority,
            costMillis = cost,
            success = error == null,
            error = error
        )
        synchronized(results) { results.add(result) }

        // 确保回调在主线程执行（BACKGROUND 线程也安全）
        val callback = resultCallback
        if (callback != null) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                callback.invoke(result)
            } else {
                Handler(Looper.getMainLooper()).post { callback.invoke(result) }
            }
        }

        if (error == null) {
            Log.d(TAG, "✓ ${initializer.name} [${initializer.priority}] ${cost}ms")
        }
    }

    /**
     * 按依赖关系拓扑排序（同一优先级内），带循环依赖检测。
     *
     * @throws IllegalStateException 检测到循环依赖时抛出
     */
    private fun sortByDependencies(list: List<AppInitializer>): List<AppInitializer> {
        if (list.size <= 1) return list

        val nameMap = list.associateBy { it.name }
        val sorted = mutableListOf<AppInitializer>()
        val visited = mutableSetOf<String>()
        val visiting = mutableSetOf<String>() // 正在访问的节点，用于检测环

        fun visit(initializer: AppInitializer) {
            if (initializer.name in visited) return
            check(initializer.name !in visiting) {
                "检测到循环依赖: ${initializer.name} → ${initializer.dependencies.filter { it in visiting }}"
            }
            visiting.add(initializer.name)
            try {
                for (depName in initializer.dependencies) {
                    nameMap[depName]?.let { visit(it) }
                        ?: Log.w(TAG, "${initializer.name} 依赖 $depName 不在当前优先级组中（可能在更高优先级组已执行）")
                }
            } finally {
                visiting.remove(initializer.name)
            }
            visited.add(initializer.name)
            sorted.add(initializer)
        }

        list.forEach { visit(it) }
        return sorted
    }
}

/**
 * 启动配置 DSL。
 */
class StartupConfig {
    internal val initializers = mutableListOf<AppInitializer>()
    internal var resultCallback: ((InitResult) -> Unit)? = null

    /**
     * 添加初始化器。
     *
     * @param initializer 初始化器实例
     */
    fun add(initializer: AppInitializer) {
        initializers.add(initializer)
    }

    /**
     * 设置初始化结果回调，每个初始化器完成后都会触发。
     *
     * @param callback 结果回调
     */
    fun onResult(callback: (InitResult) -> Unit) {
        resultCallback = callback
    }
}
