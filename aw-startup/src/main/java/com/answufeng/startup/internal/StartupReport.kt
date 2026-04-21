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

    var backgroundLatch: CountDownLatch? = null
        internal set

    val results: List<InitResult>
        get() = _results.toList()

    fun isInitialized(name: String): Boolean = name in _completedNames

    internal fun addResult(result: InitResult) {
        _results.add(result)
        _completedNames.add(result.name)
    }

    fun awaitBackground() {
        backgroundLatch?.await()
    }

    fun awaitBackground(timeoutMillis: Long): Boolean {
        return backgroundLatch?.await(timeoutMillis, TimeUnit.MILLISECONDS) ?: true
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
