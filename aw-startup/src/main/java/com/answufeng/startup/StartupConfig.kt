package com.answufeng.startup

import android.content.Context
import kotlin.math.min

class StartupConfig {

    internal val initializers = mutableListOf<AppInitializer>()
    internal var resultCallback: ((InitResult) -> Unit)? = null
    internal var progressCallback: ((completed: Int, total: Int) -> Unit)? = null
    internal var logger: Boolean = false
    internal var startupLogger: StartupLogger? = null
    internal var backgroundThreadCount: Int = min(4, Runtime.getRuntime().availableProcessors())
    internal var customExecutor: java.util.concurrent.ExecutorService? = null
    internal var failStrategy: FailStrategy = FailStrategy.CONTINUE
    internal var defaultTimeoutMillis: Long = 0
    internal var deferredTimeoutMillis: Long = 0

    fun add(initializer: AppInitializer) {
        initializers.add(initializer)
    }

    fun onResult(callback: (InitResult) -> Unit) {
        resultCallback = callback
    }

    fun onProgress(callback: (completed: Int, total: Int) -> Unit) {
        progressCallback = callback
    }

    fun logger(enabled: Boolean = true) {
        logger = enabled
    }

    fun logger(logger: StartupLogger) {
        startupLogger = logger
        this.logger = true
    }

    fun backgroundThreads(count: Int) {
        require(count > 0) { "后台线程数必须大于 0，当前值：$count" }
        backgroundThreadCount = count
    }

    fun executor(executor: java.util.concurrent.ExecutorService) {
        customExecutor = executor
    }

    fun failStrategy(strategy: FailStrategy) {
        failStrategy = strategy
    }

    fun timeout(millis: Long) {
        require(millis >= 0) { "超时时间不能为负数，当前值：$millis" }
        defaultTimeoutMillis = millis
    }

    fun deferredTimeout(millis: Long) {
        require(millis >= 0) { "DEFERRED 超时时间不能为负数，当前值：$millis" }
        deferredTimeoutMillis = millis
    }

    fun immediately(
        name: String,
        vararg deps: String,
        enabled: Boolean = true,
        onCompleted: () -> Unit = {},
        onFailed: (Throwable) -> Unit = {},
        init: (Context) -> Unit
    ) {
        add(object : AppInitializer() {
            override val name = name
            override val priority = InitPriority.IMMEDIATELY
            override val dependencies = deps.toList()
            override val enabled = enabled
            override fun onCreate(context: Context) = init(context)
            override fun onCompleted() = onCompleted()
            override fun onFailed(error: Throwable) = onFailed(error)
        })
    }

    fun normal(
        name: String,
        vararg deps: String,
        enabled: Boolean = true,
        onCompleted: () -> Unit = {},
        onFailed: (Throwable) -> Unit = {},
        init: (Context) -> Unit
    ) {
        add(object : AppInitializer() {
            override val name = name
            override val priority = InitPriority.NORMAL
            override val dependencies = deps.toList()
            override val enabled = enabled
            override fun onCreate(context: Context) = init(context)
            override fun onCompleted() = onCompleted()
            override fun onFailed(error: Throwable) = onFailed(error)
        })
    }

    fun deferred(
        name: String,
        vararg deps: String,
        enabled: Boolean = true,
        onCompleted: () -> Unit = {},
        onFailed: (Throwable) -> Unit = {},
        init: (Context) -> Unit
    ) {
        add(object : AppInitializer() {
            override val name = name
            override val priority = InitPriority.DEFERRED
            override val dependencies = deps.toList()
            override val enabled = enabled
            override fun onCreate(context: Context) = init(context)
            override fun onCompleted() = onCompleted()
            override fun onFailed(error: Throwable) = onFailed(error)
        })
    }

    fun background(
        name: String,
        vararg deps: String,
        enabled: Boolean = true,
        onCompleted: () -> Unit = {},
        onFailed: (Throwable) -> Unit = {},
        init: (Context) -> Unit
    ) {
        add(object : AppInitializer() {
            override val name = name
            override val priority = InitPriority.BACKGROUND
            override val dependencies = deps.toList()
            override val enabled = enabled
            override fun onCreate(context: Context) = init(context)
            override fun onCompleted() = onCompleted()
            override fun onFailed(error: Throwable) = onFailed(error)
        })
    }

    fun suspendImmediately(
        name: String,
        vararg deps: String,
        enabled: Boolean = true,
        onCompleted: () -> Unit = {},
        onFailed: (Throwable) -> Unit = {},
        init: suspend (Context) -> Unit
    ) {
        add(object : SuspendAppInitializer() {
            override val name = name
            override val priority = InitPriority.IMMEDIATELY
            override val dependencies = deps.toList()
            override val enabled = enabled
            override suspend fun onCreateSuspend(context: Context) = init(context)
            override fun onCompleted() = onCompleted()
            override fun onFailed(error: Throwable) = onFailed(error)
        })
    }

    fun suspendNormal(
        name: String,
        vararg deps: String,
        enabled: Boolean = true,
        onCompleted: () -> Unit = {},
        onFailed: (Throwable) -> Unit = {},
        init: suspend (Context) -> Unit
    ) {
        add(object : SuspendAppInitializer() {
            override val name = name
            override val priority = InitPriority.NORMAL
            override val dependencies = deps.toList()
            override val enabled = enabled
            override suspend fun onCreateSuspend(context: Context) = init(context)
            override fun onCompleted() = onCompleted()
            override fun onFailed(error: Throwable) = onFailed(error)
        })
    }

    fun suspendDeferred(
        name: String,
        vararg deps: String,
        enabled: Boolean = true,
        onCompleted: () -> Unit = {},
        onFailed: (Throwable) -> Unit = {},
        init: suspend (Context) -> Unit
    ) {
        add(object : SuspendAppInitializer() {
            override val name = name
            override val priority = InitPriority.DEFERRED
            override val dependencies = deps.toList()
            override val enabled = enabled
            override suspend fun onCreateSuspend(context: Context) = init(context)
            override fun onCompleted() = onCompleted()
            override fun onFailed(error: Throwable) = onFailed(error)
        })
    }

    fun suspendBackground(
        name: String,
        vararg deps: String,
        enabled: Boolean = true,
        onCompleted: () -> Unit = {},
        onFailed: (Throwable) -> Unit = {},
        init: suspend (Context) -> Unit
    ) {
        add(object : SuspendAppInitializer() {
            override val name = name
            override val priority = InitPriority.BACKGROUND
            override val dependencies = deps.toList()
            override val enabled = enabled
            override suspend fun onCreateSuspend(context: Context) = init(context)
            override fun onCompleted() = onCompleted()
            override fun onFailed(error: Throwable) = onFailed(error)
        })
    }
}
