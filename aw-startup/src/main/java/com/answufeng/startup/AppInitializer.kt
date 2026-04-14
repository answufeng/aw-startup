package com.answufeng.startup

import android.content.Context

/**
 * 应用初始化器抽象类。
 *
 * 每个初始化器需继承此类，声明名称、优先级和依赖关系。
 *
 * 完整声明方式：
 * ```kotlin
 * class NetworkInit : AppInitializer() {
 *     override val name = "Network"
 *     override val priority = InitPriority.NORMAL
 *     override val dependencies = listOf("Logger")
 *     override fun onCreate(context: Context) { AwNet.init(context) }
 *     override fun onCompleted() { Log.d("Startup", "Network ready") }
 * }
 * ```
 *
 * DSL 快捷声明方式（在 [StartupConfig] 中）：
 * ```kotlin
 * AwStartup.init(this) {
 *     immediately("Logger") { AwLogger.init() }
 *     normal("Network", deps = listOf("Logger")) { AwNet.init(it) }
 * }
 * ```
 */
abstract class AppInitializer {

    /**
     * 初始化器名称，用于依赖引用和报告标识。
     *
     * 必须全局唯一。
     */
    abstract val name: String

    /**
     * 初始化优先级，决定执行时机和线程。
     *
     * @see InitPriority.IMMEDIATELY
     * @see InitPriority.NORMAL
     * @see InitPriority.DEFERRED
     * @see InitPriority.BACKGROUND
     */
    abstract val priority: InitPriority

    /**
     * 依赖的初始化器名称列表。
     *
     * 被依赖的初始化器会先于当前初始化器执行。
     * 依赖的初始化器优先级不能低于当前初始化器。
     */
    open val dependencies: List<String> = emptyList()

    /**
     * 执行初始化逻辑。
     *
     * @param context 应用上下文
     */
    abstract fun onCreate(context: Context)

    /**
     * 初始化成功完成回调。
     *
     * 在 [onCreate] 执行成功后调用。
     */
    open fun onCompleted() {}

    /**
     * 初始化失败回调。
     *
     * 在 [onCreate] 抛出异常后调用，可用于降级或重试。
     *
     * @param error 执行失败的异常
     */
    open fun onFailed(error: Throwable) {}
}
