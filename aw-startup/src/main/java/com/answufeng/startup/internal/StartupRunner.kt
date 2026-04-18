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
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking

class StartupRunner(
    private val graph: Graph,
    private val context: Context,
    private val config: StartupConfig? = null
) {

    private val lock = Any()
    private val results = mutableListOf<InitResult>()
    private val completedNames = mutableSetOf<String>()
    private val failedInitializers = mutableSetOf<String>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var syncStartTime: Long = 0L
    private var syncCostMillis: Long = 0L

    private val backgroundThreadCount = config?.backgroundThreadCount
        ?: Runtime.getRuntime().availableProcessors()

    private val ownsExecutor: Boolean = config?.customExecutor == null
    private val executor: ExecutorService = config?.customExecutor
        ?: ThreadPoolExecutor(
            backgroundThreadCount, backgroundThreadCount,
            0L, TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(128),
            StartupThreadFactory(),
            ThreadPoolExecutor.CallerRunsPolicy()
        )

    private var concurrentLatch: CountDownLatch? = null

    private enum class ExecStrategy { SYNC, IDLE, CONCURRENT }

    private fun execStrategy(priority: InitPriority): ExecStrategy = when (priority) {
        InitPriority.IMMEDIATELY, InitPriority.NORMAL -> ExecStrategy.SYNC
        InitPriority.DEFERRED -> ExecStrategy.IDLE
        InitPriority.BACKGROUND -> ExecStrategy.CONCURRENT
        is InitPriority.Custom -> when {
            priority.executor != null -> ExecStrategy.CONCURRENT
            priority.ordinal <= InitPriority.NORMAL.ordinal -> ExecStrategy.SYNC
            priority.ordinal <= InitPriority.DEFERRED.ordinal -> ExecStrategy.IDLE
            else -> ExecStrategy.CONCURRENT
        }
    }

    fun run(): StartupReport {
        syncStartTime = System.currentTimeMillis()

        val groups = graph.getGroups()
        val idleInitializers = mutableListOf<AppInitializer>()
        val concurrentInitializers = mutableListOf<AppInitializer>()

        for (group in groups) {
            when (execStrategy(group.priority)) {
                ExecStrategy.SYNC -> runSyncGroup(group.initializers)
                ExecStrategy.IDLE -> idleInitializers.addAll(group.initializers)
                ExecStrategy.CONCURRENT -> concurrentInitializers.addAll(group.initializers)
            }
        }

        syncCostMillis = System.currentTimeMillis() - syncStartTime

        if (idleInitializers.isNotEmpty()) {
            scheduleIdle(idleInitializers)
        }

        if (concurrentInitializers.isNotEmpty()) {
            submitConcurrent(concurrentInitializers)
        }

        return StartupReport(results, lock, syncCostMillis, concurrentLatch, completedNames)
    }

    fun shutdown() {
        if (ownsExecutor) {
            executor.shutdown()
        }
    }

    private fun runSyncGroup(initializers: List<AppInitializer>) {
        for (init in initializers) {
            executeInitializer(init)
        }
    }

    private fun scheduleIdle(initializers: List<AppInitializer>) {
        val queue = Looper.myLooper()!!.queue
        val remaining = java.util.concurrent.atomic.AtomicInteger(initializers.size)
        val iterator = initializers.iterator()

        val idleHandler = object : MessageQueue.IdleHandler {
            override fun queueIdle(): Boolean {
                while (iterator.hasNext()) {
                    executeInitializer(iterator.next())
                    remaining.decrementAndGet()
                    if (!iterator.hasNext()) return false
                }
                return false
            }
        }
        queue.addIdleHandler(idleHandler)

        val deferredTimeout = config?.deferredTimeoutMillis ?: 0L
        if (deferredTimeout > 0) {
            mainHandler.postDelayed({
                val left = remaining.get()
                if (left > 0) {
                    android.util.Log.w(
                        "AwStartup",
                        "DEFERRED 任务超时（${deferredTimeout}ms），还有 $left 个未执行，强制执行"
                    )
                    queue.removeIdleHandler(idleHandler)
                    while (iterator.hasNext()) {
                        executeInitializer(iterator.next())
                        remaining.decrementAndGet()
                    }
                }
            }, deferredTimeout)
        }
    }

    private fun submitConcurrent(initializers: List<AppInitializer>) {
        val latch = CountDownLatch(initializers.size)
        concurrentLatch = latch

        val latchMap = mutableMapOf<String, CountDownLatch>()
        for (init in initializers) {
            latchMap[init.name] = CountDownLatch(1)
        }

        for (init in initializers) {
            val customExec = (init.priority as? InitPriority.Custom)?.executor
            val targetExecutor: ExecutorService =
                if (customExec is ExecutorService) customExec else executor

            targetExecutor.submit {
                try {
                    for (dep in init.dependencies) {
                        latchMap[dep]?.await()
                    }
                    executeInitializer(init)
                } finally {
                    latchMap[init.name]?.countDown()
                    latch.countDown()
                }
            }
        }
    }

    private fun executeInitializer(init: AppInitializer) {
        val effectiveFailStrategy =
            init.failStrategy ?: config?.failStrategy ?: FailStrategy.CONTINUE

        if (effectiveFailStrategy == FailStrategy.ABORT_DEPENDENTS) {
            synchronized(lock) {
                val hasFailedDep = init.dependencies.any { it in failedInitializers }
                if (hasFailedDep) {
                    val r = InitResult(
                        init.name, init.priority, 0, false,
                        IllegalStateException("依赖的初始化器失败，跳过执行"),
                        skipped = true
                    )
                    results.add(r)
                    failedInitializers.add(init.name)
                    notifyResult(r)
                    return
                }
            }
        }

        val effectiveTimeout = if (init.timeoutMillis > 0) init.timeoutMillis
        else if (config?.defaultTimeoutMillis ?: 0 > 0) config!!.defaultTimeoutMillis
        else 0L

        val maxRetries = init.retryCount
        var lastError: Exception? = null
        val start = System.currentTimeMillis()

        for (attempt in 0..maxRetries) {
            try {
                if (effectiveTimeout > 0 && execStrategy(init.priority) == ExecStrategy.CONCURRENT) {
                    val future: Future<*> = executor.submit {
                        doExecute(init)
                    }
                    try {
                        future.get(effectiveTimeout, TimeUnit.MILLISECONDS)
                    } catch (e: TimeoutException) {
                        future.cancel(true)
                        throw TimeoutException(
                            "初始化器 ${init.name} 执行超时（${effectiveTimeout}ms）"
                        )
                    }
                } else {
                    doExecute(init)
                }
                val cost = System.currentTimeMillis() - start
                val r = InitResult(init.name, init.priority, cost, true)
                synchronized(lock) {
                    results.add(r)
                    completedNames.add(init.name)
                }
                notifyResult(r)
                try { init.onCompleted() } catch (_: Exception) {}
                return
            } catch (e: Exception) {
                lastError = e
                if (attempt < maxRetries) {
                    android.util.Log.w(
                        "AwStartup",
                        "初始化器 ${init.name} 第${attempt + 1}次执行失败，准备重试"
                    )
                }
            }
        }

        val cost = System.currentTimeMillis() - start
        val r = InitResult(init.name, init.priority, cost, false, lastError)
        synchronized(lock) {
            results.add(r)
            failedInitializers.add(init.name)
        }
        notifyResult(r)
        try { init.onFailed(lastError ?: RuntimeException("Unknown error")) } catch (_: Exception) {}
    }

    private fun doExecute(init: AppInitializer) {
        if (init is SuspendAppInitializer) {
            runBlocking { init.onCreateSuspend(context) }
        } else {
            init.onCreate(context)
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

    internal class StartupThreadFactory : ThreadFactory {
        private val counter = AtomicInteger(0)
        override fun newThread(r: Runnable): Thread {
            return Thread(r, "aw-startup-bg-${counter.incrementAndGet()}")
        }
    }
}
