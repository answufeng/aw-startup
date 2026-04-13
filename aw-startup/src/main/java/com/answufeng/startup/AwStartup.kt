package com.answufeng.startup

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.MainThread
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object AwStartup {

    private const val TAG = "aw-startup"

    private val initializers = mutableListOf<AppInitializer>()
    private val results = Collections.synchronizedList(mutableListOf<InitResult>())
    private val started = AtomicBoolean(false)
    private var resultCallback: ((InitResult) -> Unit)? = null

    @Volatile
    private var syncCostMillis = 0L

    private val executorLock = Any()

    @Volatile
    private var backgroundExecutor: ExecutorService? = null

    private var threadCount = 2

    @Volatile
    private var allCompletedLatch = CountDownLatch(0)

    private val threadNumber = AtomicInteger(1)

    private fun createBackgroundExecutor(): ExecutorService {
        return Executors.newFixedThreadPool(threadCount) { r ->
            Thread(r, "aw-startup-BG-${threadNumber.getAndIncrement()}").apply { isDaemon = true }
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

    @MainThread
    fun init(context: Context, block: StartupConfig.() -> Unit) {
        val config = StartupConfig().apply(block)
        resultCallback = config.resultCallback
        if (config.threadCount > 0) {
            threadCount = config.threadCount
        }
        config.initializers.forEach { register(it) }
        start(context)
    }

    fun register(initializer: AppInitializer) {
        check(!started.get()) { "aw-startup 已启动，无法再注册初始化器" }
        require(initializers.none { it.name == initializer.name }) {
            "初始化器名称重复: '${initializer.name}'，每个 AppInitializer.name 必须唯一"
        }
        initializers.add(initializer)
    }

    @MainThread
    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) {
            Log.w(TAG, "AwStartup 已经启动过，忽略重复调用")
            return
        }

        val appContext = context.applicationContext
        val syncStart = System.currentTimeMillis()

        val grouped = initializers.groupBy { it.priority }

        val immediately = sortByDependencies(grouped[InitPriority.IMMEDIATELY].orEmpty())
        immediately.forEach { runInitializer(it, appContext) }

        val normal = sortByDependencies(grouped[InitPriority.NORMAL].orEmpty())
        normal.forEach { runInitializer(it, appContext) }

        syncCostMillis = System.currentTimeMillis() - syncStart

        val deferred = sortByDependencies(grouped[InitPriority.DEFERRED].orEmpty())
        if (deferred.isNotEmpty()) {
            val mainHandler = Handler(Looper.getMainLooper())
            Looper.myQueue().addIdleHandler {
                deferred.forEach { initializer ->
                    mainHandler.post { runInitializer(initializer, appContext) }
                }
                false
            }
        }

        val background = sortByDependencies(grouped[InitPriority.BACKGROUND].orEmpty())
        if (background.isNotEmpty()) {
            allCompletedLatch = CountDownLatch(background.size)
            val bgNames = background.map { it.name }.toSet()
            val latchMap = background.associate { it.name to CountDownLatch(1) }
            val remaining = AtomicInteger(background.size)
            background.forEach { initializer ->
                executeBackgroundTask {
                    for (dep in initializer.dependencies) {
                        if (dep in bgNames) {
                            latchMap[dep]?.await()
                        }
                    }
                    try {
                        runInitializer(initializer, appContext)
                    } finally {
                        latchMap[initializer.name]?.countDown()
                        allCompletedLatch.countDown()
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

    fun getReport(): List<InitResult> = synchronized(results) { results.toList() }

    fun getSyncCostMillis(): Long = syncCostMillis

    val isStarted: Boolean get() = started.get()

    fun await(timeoutMillis: Long = 0): Boolean {
        if (!started.get()) return false
        return if (timeoutMillis > 0) {
            allCompletedLatch.await(timeoutMillis, TimeUnit.MILLISECONDS)
        } else {
            allCompletedLatch.await()
            true
        }
    }

    internal fun reset() {
        shutdownBackgroundExecutor(immediately = true)
        started.set(false)
        initializers.clear()
        synchronized(results) { results.clear() }
        resultCallback = null
        syncCostMillis = 0
        threadCount = 2
        allCompletedLatch = CountDownLatch(0)
        threadNumber.set(1)
    }

    private fun runInitializer(initializer: AppInitializer, context: Context) {
        val start = System.currentTimeMillis()
        var error: Throwable? = null
        try {
            initializer.onCreate(context)
        } catch (e: Exception) {
            error = e
            Log.e(TAG, "初始化失败: ${initializer.name}", e)
        }
        try {
            initializer.onCompleted()
        } catch (e: Exception) {
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

    private fun sortByDependencies(list: List<AppInitializer>): List<AppInitializer> {
        if (list.size <= 1) return list

        val nameMap = list.associateBy { it.name }
        val sorted = mutableListOf<AppInitializer>()
        val visited = mutableSetOf<String>()
        val visiting = mutableSetOf<String>()

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

class StartupConfig {
    internal val initializers = mutableListOf<AppInitializer>()
    internal var resultCallback: ((InitResult) -> Unit)? = null
    internal var threadCount = 0

    fun add(initializer: AppInitializer) {
        initializers.add(initializer)
    }

    fun onResult(callback: (InitResult) -> Unit) {
        resultCallback = callback
    }

    fun backgroundThreads(count: Int) {
        require(count > 0) { "backgroundThreads 必须大于 0" }
        threadCount = count
    }
}
