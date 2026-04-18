package com.answufeng.startup

import android.app.ActivityManager
import android.content.Context
import android.os.Looper
import androidx.annotation.MainThread
import com.answufeng.startup.internal.Graph
import com.answufeng.startup.internal.StartupReport
import com.answufeng.startup.internal.StartupRunner
import java.util.concurrent.atomic.AtomicBoolean

object AwStartup {

    private val started = AtomicBoolean(false)
    private val initializers = mutableListOf<AppInitializer>()
    private var report: StartupReport? = null
    private var config: StartupConfig? = null
    private var runner: StartupRunner? = null

    val isStarted: Boolean get() = started.get()

    @MainThread
    fun init(context: Context, block: StartupConfig.() -> Unit) {
        init(context, mainProcessOnly = false, block = block)
    }

    @MainThread
    fun init(context: Context, mainProcessOnly: Boolean, block: StartupConfig.() -> Unit) {
        if (mainProcessOnly && !isMainProcess(context)) {
            android.util.Log.d("AwStartup", "当前非主进程，跳过初始化")
            return
        }
        val cfg = StartupConfig().apply(block)
        config = cfg
        synchronized(initializers) {
            check(!started.get()) { "AwStartup 已启动，不能再注册初始化器" }
            initializers.addAll(cfg.initializers)
        }
        start(context)
    }

    fun register(initializer: AppInitializer) {
        synchronized(initializers) {
            check(!started.get()) { "AwStartup 已启动，不能再注册初始化器" }
            require(initializers.none { it.name == initializer.name }) {
                "初始化器名称重复：${initializer.name}"
            }
            initializers.add(initializer)
        }
    }

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

    fun getReport(): List<InitResult> = report?.results ?: emptyList()

    fun getSyncCostMillis(): Long = report?.syncCostMillis ?: 0L

    fun isInitialized(name: String): Boolean = report?.isInitialized(name) ?: false

    fun await() {
        report?.awaitBackground()
    }

    fun await(timeoutMillis: Long): Boolean {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            android.util.Log.w("AwStartup", "在主线程调用 await() 会阻塞 UI，请考虑使用 onResult 回调")
        }
        return report?.awaitBackground(timeoutMillis) ?: true
    }

    fun isMainProcess(context: Context): Boolean {
        val pid = android.os.Process.myPid()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val processInfo = am?.runningAppProcesses?.find { it.pid == pid }
        return processInfo?.processName == context.packageName
    }

    fun reset() {
        synchronized(initializers) {
            runner?.shutdown()
            runner = null
            started.set(false)
            initializers.clear()
            report = null
            config = null
        }
    }
}
