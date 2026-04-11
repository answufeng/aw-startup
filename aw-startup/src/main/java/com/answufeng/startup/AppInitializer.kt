package com.answufeng.startup

import android.content.Context

/**
 * 应用初始化器接口，所有需要在启动时执行的组件均需实现此接口。
 *
 * ### 实现示例
 * ```kotlin
 * class LogInitializer : AppInitializer {
 *     override val name: String = "BrickLogger"
 *     override val priority: InitPriority = InitPriority.IMMEDIATELY
 *
 *     override fun onCreate(context: Context) {
 *         BrickLogger.init {
 *             debug = BuildConfig.DEBUG
 *             fileLog = true
 *             fileDir = "${context.cacheDir}/logs"
 *         }
 *     }
 * }
 * ```
 *
 * ### 带依赖的初始化器
 * ```kotlin
 * class ImageInitializer : AppInitializer {
 *     override val name: String = "BrickImage"
 *     override val priority: InitPriority = InitPriority.NORMAL
 *     override val dependencies: List<String> = listOf("BrickLogger") // 需要日志先初始化
 *
 *     override fun onCreate(context: Context) {
 *         BrickImage.init(context) { diskCacheSize(128L * 1024 * 1024) }
 *     }
 * }
 * ```
 */
interface AppInitializer {

    /**
     * 初始化器名称，用于日志输出和依赖引用，必须唯一。
     */
    val name: String

    /**
     * 初始化优先级，决定执行时机。
     *
     * @see InitPriority
     */
    val priority: InitPriority

    /**
     * 依赖的其他初始化器名称列表。
     *
     * 在同一优先级内，依赖的初始化器会先执行。
     * 跨优先级时由优先级决定顺序（高优先级始终先于低优先级）。
     *
     * 默认为空（无依赖）。
     */
    val dependencies: List<String> get() = emptyList()

    /**
     * 执行初始化逻辑。
     *
     * @param context Application Context
     */
    fun onCreate(context: Context)

    /**
     * 初始化完成后的回调（可选）。
     *
     * 当前初始化器执行完毕后立即调用，适合做“初始化完成”日志记录。
     * 默认空实现。
     */
    fun onCompleted() {}
}
