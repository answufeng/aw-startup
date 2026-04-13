package com.answufeng.startup

import android.content.Context

/**
 * 应用初始化器接口。
 *
 * 每个初始化器需实现此接口，声明名称、优先级和依赖关系。
 *
 * ```kotlin
 * class NetworkInitializer : AppInitializer {
 *     override val name = "network"
 *     override val priority = InitPriority.IMMEDIATELY
 *     override val dependencies = listOf("log")
 *     override fun onCreate(context: Context) { ... }
 * }
 * ```
 */
interface AppInitializer {

    /**
     * 初始化器名称，用于依赖引用和报告标识。
     *
     * 必须全局唯一。
     */
    val name: String

    /**
     * 初始化优先级，决定执行时机和线程。
     *
     * @see InitPriority
     */
    val priority: InitPriority

    /**
     * 依赖的初始化器名称列表。
     *
     * 被依赖的初始化器会先于当前初始化器执行。
     * 依赖的初始化器优先级不能低于当前初始化器。
     */
    val dependencies: List<String> get() = emptyList()

    /**
     * 执行初始化逻辑。
     *
     * @param context 应用上下文
     */
    fun onCreate(context: Context)

    /**
     * 初始化完成回调。
     *
     * 在 [onCreate] 执行成功后调用。
     */
    fun onCompleted() {}
}
