package com.answufeng.startup.internal

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.MessageQueue
import com.answufeng.startup.AppInitializer
import com.answufeng.startup.FailStrategy
import com.answufeng.startup.InitPriority
import com.answufeng.startup.InitResult
import com.answufeng.startup.StartupConfig
import com.answufeng.startup.SuspendAppInitializer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking

/**
 * 启动执行器，负责按优先级分组执行初始化器。
 *
 * - IMMEDIATELY / NORMAL：主线程同步执行
 * - DEFERRED：通过 [MessageQueue.IdleHandler] 在主线程空闲时逐个执行
 * - BACKGROUND：后台线程池并发执行，使用 [CountDownLatch] 保证依赖顺序
 */
class StartupRunner(
    private val graph: Graph,
    private val context: Context,
    private val config: StartupConfig? = null
) {

    private val results = mutableListOf<InitResult>()
    private val failedInitializers = mutableSetOf<String>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var syncStartTime: Long = 0L
    private var syncCostMillis: Long = 0L

    private val backgroundThreadCount = config?.backgroundThreadCount
        ?: Runtime.getRuntime().availableProcessors()

    private val ownsExecutor: Boolean = config?.customExecutor == null
    private val executor: ExecutorService = config?.customExecutor
        ?: Executors.newFixedThreadPool(backgroundThreadCount)

    private val backgroundLatch: CountDownLatch by lazy {
        val bgCount = graph.getSorted(InitPriority.BACKGROUND).size
        CountDownLatch(bgCount.coerceAtLeast(1))
    }

    /**
     * 执行所有初始化器，返回启动报告。
     *
     * 执行顺序：IMMEDIATELY → NORMAL（同步）→ DEFERRED（IdleHandler）→ BACKGROUND（线程池）
     */
    fun run(): StartupReport {
        syncStartTime = System.currentTimeMillis()

        runGroup(InitPriority.IMMEDIATELY)
        runGroup(InitPriority.NORMAL)

        syncCostMillis = System.currentTimeMillis() - syncStartTime

        scheduleDeferred()
        submitBackground()

        return StartupReport(results.toList(), syncCostMillis, backgroundLatch)
    }

    /**
     * 关闭线程池（仅关闭自创建的线程池）。
     */
    fun shutdown() {
        if (ownsExecutor) {
            executor.shutdown()
        }
    }

    private fun runGroup(priority: InitPriority) {
        val sorted = graph.getSorted(priority)
        for (init in sorted) {
            executeInitializer(init)
        }
    }

    private fun scheduleDeferred() {
        val deferred = graph.getSorted(InitPriority.DEFERRED)
        if (deferred.isEmpty()) return

        val queue = Looper.myLooper()!!.queue
        val iterator = deferred.iterator()

        val idleHandler = object : MessageQueue.IdleHandler {
            override fun queueIdle(): Boolean {
                if (iterator.hasNext()) {
                    executeInitializer(iterator.next())
                }
                return iterator.hasNext()
            }
        }
        queue.addIdleHandler(idleHandler)
    }

    private fun submitBackground() {
        val background = graph.getSorted(InitPriority.BACKGROUND)
        if (background.isEmpty()) return

        val latchMap = mutableMapOf<String, CountDownLatch>()
        for (init in background) {
            latchMap[init.name] = CountDownLatch(1)
        }

        for (init in background) {
            val customExec = (init.priority as? InitPriority.Custom)?.executor
            val targetExecutor: ExecutorService = if (customExec is ExecutorService) customExec else executor

            targetExecutor.submit {
                try {
                    for (dep in init.dependencies) {
                        latchMap[dep]?.await()
                    }
                    executeInitializer(init)
                } finally {
                    latchMap[init.name]?.countDown()
                    backgroundLatch.countDown()
                }
            }
        }
    }

    private fun executeInitializer(init: AppInitializer) {
        if (config?.failStrategy == FailStrategy.ABORT_DEPENDENTS) {
            val hasFailedDep = init.dependencies.any { it in failedInitializers }
            if (hasFailedDep) {
                val r = InitResult(
                    init.name, init.priority, 0, false,
                    IllegalStateException("依赖的初始化器失败，跳过执行")
                )
                synchronized(results) { results.add(r) }
                synchronized(failedInitializers) { failedInitializers.add(init.name) }
                notifyResult(r)
                return
            }
        }

        val start = System.currentTimeMillis()
        try {
            if (init is SuspendAppInitializer) {
                runBlocking { init.onCreateSuspend(context) }
            } else {
                init.onCreate(context)
            }
            val cost = System.currentTimeMillis() - start
            val r = InitResult(init.name, init.priority, cost, true)
            synchronized(results) { results.add(r) }
            notifyResult(r)
            try { init.onCompleted() } catch (_: Exception) {}
        } catch (e: Exception) {
            val cost = System.currentTimeMillis() - start
            val r = InitResult(init.name, init.priority, cost, false, e)
            synchronized(results) { results.add(r) }
            synchronized(failedInitializers) { failedInitializers.add(init.name) }
            notifyResult(r)
            try { init.onFailed(e) } catch (_: Exception) {}
        }
    }

    private fun notifyResult(result: InitResult) {
        val callback = config?.resultCallback ?: return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            callback(result)
        } else {
            mainHandler.post { callback(result) }
        }
    }
}
