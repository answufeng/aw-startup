package com.answufeng.startup

import android.content.Context

/**
 * 初始化器抽象基类。
 *
 * 所有需要在应用启动时执行的初始化逻辑都应继承此类。每个初始化器通过 [name] 唯一标识，
 * 通过 [priority] 决定执行时机，通过 [dependencies] 声明依赖关系。
 *
 * 使用方式：
 * ```kotlin
 * class NetworkInit : StartupInitializer() {
 *     override val name = "Network"
 *     override val priority = InitPriority.NORMAL
 *     override val dependencies = listOf("Logger")
 *     override fun onCreate(context: Context) {
 *         OkHttpClient.Builder().build()
 *     }
 * }
 * ```
 *
 * @see InitPriority
 * @see SuspendInitializer
 */
abstract class StartupInitializer {

    /** 初始化器唯一标识，用于依赖引用和日志输出。必须全局唯一。 */
    abstract val name: String

    /** 初始化优先级，决定执行时机和所在线程。 */
    abstract val priority: InitPriority

    /** 依赖的初始化器名称列表。被依赖的初始化器会先于当前初始化器执行。 */
    open val dependencies: List<String> = emptyList()

    /**
     * 初始化器级别失败策略，优先于全局 [StartupConfig.failStrategy]。
     * 为 null 时使用全局策略。
     */
    open val failStrategy: FailStrategy? = null

    /**
     * 单个初始化器超时时间（毫秒）。0 表示不超时。
     *
     * 注意：超时强制取消仅对 [InitPriority.BACKGROUND] 和 [SuspendInitializer] 生效；
     * 对于 [InitPriority.IMMEDIATELY]、[InitPriority.NORMAL]、[InitPriority.DEFERRED]，
     * 超时后仅输出警告日志，无法强制取消主线程上的执行。
     *
     * [SuspendInitializer] 在主线程优先级下由库内放到临时线程执行 [suspend] 块，超时对协程体仍可通过
     * [kotlinx.coroutines.withTimeout] 生效；普通 [onCreate] 仍运行在主调用线程上。
     */
    open val timeoutMillis: Long = 0

    /** 失败后最大重试次数。0 表示不重试。 */
    open val retryCount: Int = 0

    /**
     * 是否启用此初始化器。为 false 时跳过执行，但视为已完成（依赖此初始化器的任务不会阻塞）。
     * 可用于根据运行时条件（如 BuildConfig、远程配置）动态控制初始化。
     */
    open val enabled: Boolean = true

    /**
     * 执行初始化逻辑。
     *
     * @param context 应用上下文
     */
    abstract fun onCreate(context: Context)

    /** 初始化成功回调。 */
    open fun onCompleted() {}

    /** 初始化失败回调。 */
    open fun onFailed(error: Throwable) {}
}

/**
 * 已废弃：使用 [StartupInitializer] 替代。
 */
@Deprecated(
    message = "Use StartupInitializer instead",
    replaceWith = ReplaceWith("StartupInitializer", "com.answufeng.startup.StartupInitializer"),
    level = DeprecationLevel.WARNING
)
typealias AppInitializer = StartupInitializer
