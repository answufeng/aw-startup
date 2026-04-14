package com.answufeng.startup.internal

import android.util.Log
import com.answufeng.startup.InitResult
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 启动报告，包含每个初始化器的执行结果和耗时统计。
 */
class StartupReport(
    /** 每个初始化器的执行结果列表。 */
    val results: List<InitResult>,
    /** 同步初始化（IMMEDIATELY + NORMAL）的总耗时（毫秒）。 */
    val syncCostMillis: Long,
    private val backgroundLatch: CountDownLatch?
) {

    /** 无限等待所有后台初始化器完成。 */
    fun awaitBackground() {
        backgroundLatch?.await()
    }

    /**
     * 带超时等待所有后台初始化器完成。
     *
     * @param timeoutMillis 超时时间（毫秒）
     * @return 是否在超时前完成
     */
    fun awaitBackground(timeoutMillis: Long): Boolean {
        return backgroundLatch?.await(timeoutMillis, TimeUnit.MILLISECONDS) ?: true
    }

    /** 输出启动报告到 Logcat。 */
    fun log() {
        val sb = StringBuilder("=== AwStartup Report ===\n")
        sb.append("Sync cost: ${syncCostMillis}ms\n")
        for (r in results) {
            val status = if (r.success) "OK" else "FAIL: ${r.error?.message}"
            sb.append("  ${r.name} [${r.priority}] ${r.costMillis}ms $status\n")
        }
        Log.d("AwStartup", sb.toString())
    }
}
