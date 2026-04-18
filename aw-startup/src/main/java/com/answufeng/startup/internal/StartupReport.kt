package com.answufeng.startup.internal

import android.util.Log
import com.answufeng.startup.InitResult
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class StartupReport(
    private val resultsRef: MutableList<InitResult>,
    private val lock: Any,
    val syncCostMillis: Long,
    private val backgroundLatch: CountDownLatch?,
    private val completedNamesRef: Set<String>
) {

    val results: List<InitResult>
        get() = synchronized(lock) { resultsRef.toList() }

    fun isInitialized(name: String): Boolean =
        synchronized(lock) { name in completedNamesRef }

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
        Log.d("AwStartup", sb.toString())
    }
}
