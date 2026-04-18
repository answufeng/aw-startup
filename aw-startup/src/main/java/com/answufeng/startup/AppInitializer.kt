package com.answufeng.startup

import android.content.Context

abstract class AppInitializer {

    abstract val name: String

    abstract val priority: InitPriority

    open val dependencies: List<String> = emptyList()

    open val failStrategy: FailStrategy? = null

    open val timeoutMillis: Long = 0

    open val retryCount: Int = 0

    abstract fun onCreate(context: Context)

    open fun onCompleted() {}

    open fun onFailed(error: Throwable) {}
}
