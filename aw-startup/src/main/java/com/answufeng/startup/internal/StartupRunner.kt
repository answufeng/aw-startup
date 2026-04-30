package com.answufeng.startup.internal

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.MessageQueue
import com.answufeng.startup.StartupInitializer
import com.answufeng.startup.FailStrategy
import com.answufeng.startup.InitPriority
import com.answufeng.startup.InitResult
import com.answufeng.startup.SkipReason
import com.answufeng.startup.StartupConfig
import com.answufeng.startup.StartupLogger
import com.answufeng.startup.SuspendInitializer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * 初始化器执行器，负责按优先级和依赖关系执行初始化任务。
 *
 * 执行策略：
 * - SYNC（IMMEDIATELY/NORMAL）：主线程顺序执行
 * - IDLE（DEFERRED）：IdleHandler 延迟执行
 * - CONCURRENT（BACKGROUND/Custom+executor）：线程池并发执行
 *
 * 默认线程池使用无界 [LinkedBlockingQueue] 与 [ThreadPoolExecutor.AbortPolicy]，避免在同步阶段
 * 向主线程「借线程」执行后台任务（`CallerRunsPolicy` 的典型风险）。
 */
class StartupRunner(
    private val graph: Graph,
    private val context: Context,
    private val config: StartupConfig? = null
) {

    private val failedInitializers = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var syncStartTime: Long = 0L
    private var syncCostMillis: Long = 0L
    private val totalCount = graph.getGroups().sumOf { it.initializers.size }

    private val log: StartupLogger = config?.startupLogger ?: StartupLogger.DEFAULT

    private val backgroundThreadCount = config?.backgroundThreadCount
        ?: min(4, Runtime.getRuntime().availableProcessors())

    private val ownsExecutor: Boolean = config?.customExecutor == null
    private val executor: ExecutorService = config?.customExecutor
        ?: ThreadPoolExecutor(
            backgroundThreadCount, backgroundThreadCount,
            30L, TimeUnit.SECONDS,
            LinkedBlockingQueue(),
            StartupThreadFactory(),
            ThreadPoolExecutor.AbortPolicy()
        ).also { it.allowCoreThreadTimeOut(true) }

    private var concurrentLatch: CountDownLatch? = null
    private lateinit var report: StartupReport

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

    private fun executorFor(init: StartupInitializer): ExecutorService {
        val customExec = (init.priority as? InitPriority.Custom)?.executor
        return if (customExec is ExecutorService) customExec else executor
    }

    fun run(): StartupReport {
        syncStartTime = System.currentTimeMillis()

        report = StartupReport(log)

        val groups = graph.getGroups()
        val idleInitializers = mutableListOf<StartupInitializer>()
        val concurrentInitializers = mutableListOf<StartupInitializer>()

        // SYNC groups are executed sequentially on the calling thread (main thread).
        // This guarantees that all SYNC initializers (IMMEDIATELY, NORMAL) are completed
        // before any IDLE or CONCURRENT initializers are submitted.
        // Therefore, BACKGROUND tasks that depend on SYNC tasks do NOT need to wait
        // via latchMap — the dependency is implicitly satisfied by execution order.
        for (group in groups) {
            when (execStrategy(group.priority)) {
                ExecStrategy.SYNC -> runSyncGroup(group.initializers)
                ExecStrategy.IDLE -> idleInitializers.addAll(group.initializers)
                ExecStrategy.CONCURRENT -> concurrentInitializers.addAll(group.initializers)
            }
        }

        syncCostMillis = System.currentTimeMillis() - syncStartTime
        report.syncCostMillis = syncCostMillis

        if (idleInitializers.isNotEmpty()) {
            scheduleIdle(idleInitializers)
        }

        if (concurrentInitializers.isNotEmpty()) {
            submitConcurrent(concurrentInitializers)
        }

        report.backgroundLatch = concurrentLatch
        return report
    }

    fun shutdown() {
        if (ownsExecutor) {
            executor.shutdown()
        }
    }

    fun awaitTermination(timeoutMillis: Long) {
        if (ownsExecutor) {
            executor.awaitTermination(timeoutMillis, TimeUnit.MILLISECONDS)
        }
    }

    private fun runSyncGroup(initializers: List<StartupInitializer>) {
        for (init in initializers) {
            executeInitializer(init)
        }
    }

    private fun scheduleIdle(initializers: List<StartupInitializer>) {
        val looper = Looper.myLooper()
        check(looper == Looper.getMainLooper()) {
            "DEFERRED 初始化必须在主线程调度（当前线程：${Thread.currentThread().name}）"
        }
        val queue = looper.queue
        val index = java.util.concurrent.atomic.AtomicInteger(0)
        val size = initializers.size
        val deferredTimeout = config?.deferredTimeoutMillis ?: 0L
        val idleLatch = CountDownLatch(size)
        report.idleLatch = idleLatch

        fun runIdleInitializer(init: StartupInitializer) {
            try {
                executeInitializer(init)
            } finally {
                idleLatch.countDown()
            }
        }

        // Use a mutable reference to hold the idleHandler so timeoutRunnable can access it
        val idleHandlerRef = arrayOfNulls<MessageQueue.IdleHandler>(1)
        
        val timeoutRunnable = Runnable {
            val left = size - index.get()
            if (left > 0) {
                log.w(
                    "AwStartup",
                    "DEFERRED 任务超时（${deferredTimeout}ms），还有 $left 个未执行，强制执行"
                )
                idleHandlerRef[0]?.let { queue.removeIdleHandler(it) }
                var i = index.getAndIncrement()
                while (i < size) {
                    runIdleInitializer(initializers[i])
                    i = index.getAndIncrement()
                }
            }
        }

        val idleHandler = object : MessageQueue.IdleHandler {
            override fun queueIdle(): Boolean {
                var i = index.getAndIncrement()
                while (i < size) {
                    runIdleInitializer(initializers[i])
                    i = index.getAndIncrement()
                }
                mainHandler.removeCallbacks(timeoutRunnable)
                return false
            }
        }
        idleHandlerRef[0] = idleHandler
        queue.addIdleHandler(idleHandler)

        if (deferredTimeout > 0) {
            mainHandler.postDelayed(timeoutRunnable, deferredTimeout)
        }
    }

    private fun submitConcurrent(initializers: List<StartupInitializer>) {
        val latch = CountDownLatch(initializers.size)
        concurrentLatch = latch

        val futureMap = mutableMapOf<String, CompletableFuture<Void>>()
        for (init in initializers) {
            futureMap[init.name] = CompletableFuture()
        }

        for (init in initializers) {
            val targetExecutor = executorFor(init)

            targetExecutor.submit {
                try {
                    for (dep in init.dependencies) {
                        futureMap[dep]?.join()
                    }
                    executeInitializer(init)
                } finally {
                    futureMap[init.name]?.complete(null)
                    latch.countDown()
                }
            }
        }
    }

    private fun executeInitializer(init: StartupInitializer) {
        if (!init.enabled) {
            val r = InitResult(
                init.name, init.priority, 0, false,
                skipped = true,
                skipReason = SkipReason.DISABLED
            )
            report.addResult(r)
            notifyResult(r)
            return
        }

        val effectiveFailStrategy =
            init.failStrategy ?: config?.failStrategy ?: FailStrategy.CONTINUE

        if (effectiveFailStrategy == FailStrategy.ABORT_DEPENDENTS) {
            val hasFailedDep = init.dependencies.any { it in failedInitializers }
            if (hasFailedDep) {
                val r = InitResult(
                    init.name, init.priority, 0, false,
                    IllegalStateException("依赖的初始化器失败，跳过执行"),
                    skipped = true,
                    skipReason = SkipReason.DEPENDENCY_FAILED
                )
                report.addResult(r)
                failedInitializers.add(init.name)
                notifyResult(r)
                return
            }
        }

        val effectiveTimeout = when {
            init.timeoutMillis > 0 -> init.timeoutMillis
            config?.defaultTimeoutMillis?.let { it > 0 } == true -> config.defaultTimeoutMillis
            else -> 0L
        }

        val maxRetries = init.retryCount
        var lastError: Exception? = null
        val start = System.currentTimeMillis()

        for (attempt in 0..maxRetries) {
            try {
                val strategy = execStrategy(init.priority)
                if (effectiveTimeout > 0 && strategy == ExecStrategy.CONCURRENT) {
                    if (init is SuspendInitializer) {
                        doExecute(init, effectiveTimeout)
                    } else {
                        val execForInit = executorFor(init)
                        val future: Future<*> = execForInit.submit {
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
                    }
                } else if (effectiveTimeout > 0 && init is SuspendInitializer) {
                    doExecute(init, effectiveTimeout)
                } else {
                    doExecute(init)
                }
                val cost = System.currentTimeMillis() - start
                if (effectiveTimeout > 0 && cost > effectiveTimeout && strategy != ExecStrategy.CONCURRENT) {
                    log.w(
                        "AwStartup",
                        "初始化器 ${init.name} 耗时 ${cost}ms 超过超时阈值 ${effectiveTimeout}ms（${strategy} 策略无法强制取消）"
                    )
                }
                val r = InitResult(init.name, init.priority, cost, true)
                report.addResult(r)
                notifyResult(r)
                try {
                    init.onCompleted()
                } catch (e: Exception) {
                    log.e("AwStartup", "初始化器 ${init.name} onCompleted 回调异常", e)
                }
                return
            } catch (e: Exception) {
                lastError = e
                if (attempt < maxRetries) {
                    val interval = init.retryIntervalMillis
                    log.w(
                        "AwStartup",
                        "初始化器 ${init.name} 第${attempt + 1}次执行失败，准备重试" +
                                if (interval > 0) "（${interval}ms 后）" else ""
                    )
                    if (interval > 0) {
                        Thread.sleep(interval)
                    }
                }
            }
        }

        val cost = System.currentTimeMillis() - start
        val r = InitResult(init.name, init.priority, cost, false, lastError)
        report.addResult(r)
        failedInitializers.add(init.name)
        notifyResult(r)
        try {
            init.onFailed(lastError ?: RuntimeException("Unknown error"))
        } catch (e: Exception) {
            log.e("AwStartup", "初始化器 ${init.name} onFailed 回调异常", e)
        }
    }

    private fun doExecute(init: StartupInitializer, timeoutMillis: Long = 0) {
        if (init is SuspendInitializer) {
            val run: suspend () -> Unit = {
                if (timeoutMillis > 0) {
                    withTimeout(timeoutMillis) { init.onCreateSuspend(context) }
                } else {
                    init.onCreateSuspend(context)
                }
            }
            if (Looper.myLooper() == Looper.getMainLooper()) {
                var thrown: Throwable? = null
                val worker = Thread({
                    try {
                        runBlocking { run() }
                    } catch (t: Throwable) {
                        thrown = t
                    }
                }, "aw-startup-suspend-${init.name}")
                worker.start()
                val joinTimeout = if (timeoutMillis > 0) timeoutMillis else 60_000L
                worker.join(joinTimeout)
                if (worker.isAlive) {
                    worker.interrupt()
                    log.w("AwStartup", "初始化器 ${init.name} 的挂起执行线程在 ${joinTimeout}ms 后未完成，已中断")
                }
                thrown?.let { throw it }
            } else {
                runBlocking { run() }
            }
        } else {
            init.onCreate(context)
        }
    }

    private fun notifyResult(result: InitResult) {
        val callback = config?.resultCallback
        if (callback != null) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                callback(result)
            } else {
                mainHandler.post { callback(result) }
            }
        }

        val progressCallback = config?.progressCallback
        if (progressCallback != null) {
            val completed = report.results.size
            if (Looper.myLooper() == Looper.getMainLooper()) {
                progressCallback(completed, totalCount)
            } else {
                mainHandler.post { progressCallback(completed, totalCount) }
            }
        }
    }

    internal class StartupThreadFactory : ThreadFactory {
        private val counter = AtomicInteger(0)
        override fun newThread(r: Runnable): Thread {
            return Thread(r, "aw-startup-bg-${counter.incrementAndGet()}")
        }
    }
}
