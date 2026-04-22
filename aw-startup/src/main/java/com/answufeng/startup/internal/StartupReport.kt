package com.answufeng.startup.internal

import com.answufeng.startup.InitResult
import com.answufeng.startup.StartupLogger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 初始化报告，记录每个初始化器的执行结果和耗时。
 *
 * 线程安全，支持实时更新。结果通过 [ConcurrentLinkedQueue] 收集，
 * 可通过 [results] 获取实时快照。
 */
class StartupReport(
    private val logger: StartupLogger = StartupLogger.DEFAULT
) {

    private val _results = ConcurrentLinkedQueue<InitResult>()
    private val _completedNames = ConcurrentHashMap.newKeySet<String>()

    var syncCostMillis: Long = 0L
        internal set

    var idleLatch: CountDownLatch? = null
        internal set

    var backgroundLatch: CountDownLatch? = null
        internal set

    val results: List<InitResult>
        get() = _results.toList()

    fun isInitialized(name: String): Boolean = name in _completedNames

    internal fun addResult(result: InitResult) {
        _results.add(result)
        _completedNames.add(result.name)
    }

    /**
     * 等待 DEFERRED（Idle 队列）与 BACKGROUND 线程池任务全部结束。
     * 不包含已在 [StartupRunner.run] 返回前完成的同步阶段（IMMEDIATELY / NORMAL）。
     */
    fun awaitBackground() {
        idleLatch?.await()
        backgroundLatch?.await()
    }

    /**
     * 在超时内等待 DEFERRED 与 BACKGROUND 全部结束。
     *
     * @return 是否在截止前全部完成（无对应 latch 时视为成功）
     */
    fun awaitBackground(timeoutMillis: Long): Boolean {
        if (timeoutMillis <= 0L) {
            awaitBackground()
            return true
        }
        val deadlineNs = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        fun remainingMillis(): Long {
            val left = deadlineNs - System.nanoTime()
            return TimeUnit.NANOSECONDS.toMillis(left.coerceAtLeast(0L))
        }
        idleLatch?.let {
            if (!it.await(remainingMillis(), TimeUnit.MILLISECONDS)) return false
        }
        backgroundLatch?.let {
            if (!it.await(remainingMillis(), TimeUnit.MILLISECONDS)) return false
        }
        return true
    }

    fun log() {
        val snapshot = results
        val sb = StringBuilder("=== AwStartup Report ===\n")
        sb.append("Sync cost: ${syncCostMillis}ms\n")
        for (r in snapshot) {
            val status = if (r.success) "OK" else "FAIL: ${r.error?.message}"
            sb.append("  ${r.name} [${r.priority}] ${r.costMillis}ms $status\n")
        }
        logger.d("AwStartup", sb.toString())
    }
}
