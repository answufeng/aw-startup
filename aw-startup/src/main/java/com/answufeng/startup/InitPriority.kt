package com.answufeng.startup

/**
 * 初始化器优先级。
 *
 * 决定初始化器的执行时机和所在线程：
 * - [IMMEDIATELY] — 主线程同步执行，用于最关键的初始化（如日志、崩溃收集）
 * - [NORMAL] — 主线程同步执行，用于常规初始化（如网络、数据库）
 * - [DEFERRED] — 主线程 IdleHandler 延迟执行，用于非关键初始化（如推送、统计）
 * - [BACKGROUND] — 后台线程池并发执行，用于耗时操作（如预加载、缓存预热）
 *
 * 执行顺序：IMMEDIATELY → NORMAL → DEFERRED → BACKGROUND
 *
 * 支持通过 [Custom] 创建自定义优先级，可携带自定义 [java.util.concurrent.Executor]：
 * ```kotlin
 * val customPriority = InitPriority.Custom(5, myExecutor)
 * ```
 */
sealed class InitPriority(val ordinal: Int) : Comparable<InitPriority> {

    override fun compareTo(other: InitPriority): Int =
        this.ordinal.compareTo(other.ordinal)

    /** 主线程同步执行，用于最关键的初始化（如日志、崩溃收集）。 */
    data object IMMEDIATELY : InitPriority(0)

    /** 主线程同步执行，用于常规初始化（如网络、数据库）。 */
    data object NORMAL : InitPriority(1)

    /** 主线程 IdleHandler 延迟执行，用于非关键初始化（如推送、统计）。 */
    data object DEFERRED : InitPriority(2)

    /** 后台线程池并发执行，用于耗时操作（如预加载、缓存预热）。 */
    data object BACKGROUND : InitPriority(3)

    /**
     * 自定义优先级，可携带自定义 [Executor][java.util.concurrent.Executor]。
     *
     * @param ordinal 优先级序号，用于排序比较
     * @param executor 自定义执行器，为 null 时使用默认线程池
     */
    data class Custom(
        override val ordinal: Int,
        val executor: java.util.concurrent.Executor? = null
    ) : InitPriority(ordinal)

    companion object {
        /** 内置优先级列表。 */
        val entries: List<InitPriority>
            get() = listOf(IMMEDIATELY, NORMAL, DEFERRED, BACKGROUND)
    }
}
