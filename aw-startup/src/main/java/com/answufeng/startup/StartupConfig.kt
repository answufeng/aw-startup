package com.answufeng.startup

import android.content.Context

enum class FailStrategy {

    CONTINUE,

    ABORT_DEPENDENTS
}

class StartupConfig {

    internal val initializers = mutableListOf<AppInitializer>()
    internal var resultCallback: ((InitResult) -> Unit)? = null
    internal var logger: Boolean = false
    internal var backgroundThreadCount: Int = Runtime.getRuntime().availableProcessors()
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

    fun logger(enabled: Boolean = true) {
        logger = enabled
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
        deps: List<String> = emptyList(),
        onCompleted: () -> Unit = {},
        onFailed: (Throwable) -> Unit = {},
        init: (Context) -> Unit
    ) {
        add(object : AppInitializer() {
            override val name = name
            override val priority = InitPriority.IMMEDIATELY
            override val dependencies = deps
            override fun onCreate(context: Context) = init(context)
            override fun onCompleted() = onCompleted()
            override fun onFailed(error: Throwable) = onFailed(error)
        })
    }

    fun normal(
        name: String,
        deps: List<String> = emptyList(),
        onCompleted: () -> Unit = {},
        onFailed: (Throwable) -> Unit = {},
        init: (Context) -> Unit
    ) {
        add(object : AppInitializer() {
            override val name = name
            override val priority = InitPriority.NORMAL
            override val dependencies = deps
            override fun onCreate(context: Context) = init(context)
            override fun onCompleted() = onCompleted()
            override fun onFailed(error: Throwable) = onFailed(error)
        })
    }

    fun deferred(
        name: String,
        deps: List<String> = emptyList(),
        onCompleted: () -> Unit = {},
        onFailed: (Throwable) -> Unit = {},
        init: (Context) -> Unit
    ) {
        add(object : AppInitializer() {
            override val name = name
            override val priority = InitPriority.DEFERRED
            override val dependencies = deps
            override fun onCreate(context: Context) = init(context)
            override fun onCompleted() = onCompleted()
            override fun onFailed(error: Throwable) = onFailed(error)
        })
    }

    fun background(
        name: String,
        deps: List<String> = emptyList(),
        onCompleted: () -> Unit = {},
        onFailed: (Throwable) -> Unit = {},
        init: (Context) -> Unit
    ) {
        add(object : AppInitializer() {
            override val name = name
            override val priority = InitPriority.BACKGROUND
            override val dependencies = deps
            override fun onCreate(context: Context) = init(context)
            override fun onCompleted() = onCompleted()
            override fun onFailed(error: Throwable) = onFailed(error)
        })
    }
}
