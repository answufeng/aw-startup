package com.answufeng.startup

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Looper
import androidx.annotation.MainThread
import com.answufeng.startup.internal.Graph
import com.answufeng.startup.internal.StartupReport
import com.answufeng.startup.internal.StartupRunner
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

/**
 * 应用启动初始化入口。
 *
 * 提供优先级分级、依赖感知的组件初始化，内置拓扑排序与循环依赖检测。
 *
 * 使用方式：
 * ```kotlin
 * class MyApp : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *         AwStartup.init(this, mainProcessOnly = true) {
 *             immediately("Logger") { AwLogger.init() }
 *             normal("Network", deps = listOf("Logger")) { AwNet.init(it) }
 *             deferred("Analytics", deps = listOf("Network")) { AwAnalytics.init(it) }
 *             background("CacheCleaner") { AwCache.clean(it) }
 *         }
 *     }
 * }
 * ```
 */
object AwStartup {

    private val started = AtomicBoolean(false)
    private val initializers = mutableListOf<StartupInitializer>()
    @Volatile
    private var report: StartupReport? = null
    @Volatile
    private var config: StartupConfig? = null
    private var runner: StartupRunner? = null
    private val store = StartupStore()

    private val log: StartupLogger get() = config?.startupLogger ?: StartupLogger.DEFAULT

    /** 是否已启动。 */
    val isStarted: Boolean get() = started.get()

    /**
     * DSL 方式初始化并启动（推荐）。
     *
     * @param context 应用上下文
     * @param block DSL 配置块
     */
    @MainThread
    fun init(context: Context, block: StartupConfig.() -> Unit) {
        init(context, mainProcessOnly = false, block = block)
    }

    /**
     * DSL 方式初始化并启动，可指定仅主进程初始化。
     *
     * @param context 应用上下文
     * @param mainProcessOnly 是否仅主进程初始化
     * @param block DSL 配置块
     */
    @MainThread
    fun init(context: Context, mainProcessOnly: Boolean, block: StartupConfig.() -> Unit) {
        if (mainProcessOnly && !isMainProcess(context)) {
            log.d("AwStartup", "当前非主进程，跳过初始化")
            return
        }
        val cfg = StartupConfig().apply(block)
        config = cfg
        synchronized(initializers) {
            check(!started.get()) { "AwStartup 已启动，不能再注册初始化器" }
            for (init in cfg.initializers) {
                require(initializers.none { it.name == init.name }) {
                    "初始化器名称重复：${init.name}"
                }
                initializers.add(init)
            }
        }
        start(context)
    }

    /**
     * 手动注册初始化器。需在 [start] 之前调用。
     *
     * @param initializer 初始化器实例
     * @throws IllegalStateException 如果已经启动
     * @throws IllegalArgumentException 如果名称重复
     */
    fun register(initializer: StartupInitializer) {
        synchronized(initializers) {
            check(!started.get()) { "AwStartup 已启动，不能再注册初始化器" }
            require(initializers.none { it.name == initializer.name }) {
                "初始化器名称重复：${initializer.name}"
            }
            initializers.add(initializer)
        }
    }

    /**
     * DSL 方式注册初始化器，返回自身以支持链式调用 [start]。
     *
     * 使用方式：
     * ```kotlin
     * AwStartup.register {
     *     immediately("Logger") { AwLogger.init() }
     *     background("CacheCleaner") { AwCache.clean(it) }
     * }.start(this)
     * ```
     */
    fun register(block: StartupConfig.() -> Unit): AwStartup {
        synchronized(initializers) {
            check(!started.get()) { "AwStartup 已启动，不能再注册初始化器" }
            val cfg = StartupConfig().apply(block)
            if (config == null) {
                config = cfg
            } else {
                val existing = config!!
                cfg.resultCallback?.let { existing.resultCallback = it }
                cfg.progressCallback?.let { existing.progressCallback = it }
                if (cfg.logger) existing.logger = cfg.logger
                cfg.startupLogger?.let { existing.startupLogger = it }
                if (cfg.backgroundThreadCount != min(4, Runtime.getRuntime().availableProcessors())) {
                    existing.backgroundThreadCount = cfg.backgroundThreadCount
                }
                cfg.customExecutor?.let { existing.customExecutor = it }
                if (cfg.failStrategy != FailStrategy.CONTINUE) existing.failStrategy = cfg.failStrategy
                if (cfg.defaultTimeoutMillis > 0) existing.defaultTimeoutMillis = cfg.defaultTimeoutMillis
                if (cfg.deferredTimeoutMillis > 0) existing.deferredTimeoutMillis = cfg.deferredTimeoutMillis
            }
            for (init in cfg.initializers) {
                require(initializers.none { it.name == init.name }) {
                    "初始化器名称重复：${init.name}"
                }
                initializers.add(init)
            }
        }
        return this
    }

    /**
     * 启动初始化流程。只能调用一次。
     *
     * @param context 应用上下文
     * @throws IllegalStateException 重复启动
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

    /** 获取初始化结果列表（实时快照）。 */
    fun getReport(): List<InitResult> = report?.results ?: emptyList()

    /** 获取同步初始化耗时（毫秒）。 */
    fun getSyncCostMillis(): Long = report?.syncCostMillis ?: 0L

    /**
     * 查询初始化器是否已完成。
     *
     * @param name 初始化器名称
     * @return 是否已完成（包括成功、失败、跳过）
     */
    fun isInitialized(name: String): Boolean = report?.isInitialized(name) ?: false

    /**
     * 获取初始化器间数据共享存储。
     *
     * 初始化器可以在 [StartupInitializer.onCreate] 中存储产物，
     * 后续初始化器可以通过 [StartupStore.get] 获取。
     */
    fun getStore(): StartupStore = store

    /**
     * 无限等待所有后台任务完成。
     *
     * **注意**：不要在主线程调用，否则会阻塞 UI。
     * 建议使用带超时的 [await] 替代。
     */
    @Deprecated(
        message = "可能永久阻塞，建议使用 await(timeoutMillis) 替代",
        level = DeprecationLevel.WARNING
    )
    fun await() {
        report?.awaitBackground()
    }

    /**
     * 带超时等待所有后台任务完成。
     *
     * @param timeoutMillis 超时时间（毫秒）
     * @return 是否在超时前完成
     */
    fun await(timeoutMillis: Long): Boolean {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            log.w("AwStartup", "在主线程调用 await() 会阻塞 UI，请考虑使用 onResult 回调")
        }
        return report?.awaitBackground(timeoutMillis) ?: true
    }

    /**
     * 判断当前是否主进程。
     *
     * 优先使用 [ApplicationInfo.processName]（API 28+），
     * 后备使用 [ActivityManager.runningAppProcesses]。
     *
     * @param context 应用上下文
     */
    fun isMainProcess(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val appName = context.applicationInfo.processName
            if (appName != null) {
                return appName == context.packageName
            }
        }
        val pid = android.os.Process.myPid()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val processInfo = am?.runningAppProcesses?.find { it.pid == pid }
        return processInfo?.processName == context.packageName
    }

    /**
     * 重置状态（主要用于测试）。
     *
     * 会等待后台任务完成（最多 [awaitTimeoutMillis] 毫秒），然后清除所有状态。
     *
     * @param awaitTimeoutMillis 等待后台任务完成的超时时间（毫秒），默认 2000
     */
    fun reset(awaitTimeoutMillis: Long = 2000) {
        synchronized(initializers) {
            runner?.shutdown()
            try {
                runner?.awaitTermination(awaitTimeoutMillis)
            } catch (_: Exception) {}
            runner = null
            started.set(false)
            initializers.clear()
            report = null
            config = null
            store.clear()
        }
    }
}
