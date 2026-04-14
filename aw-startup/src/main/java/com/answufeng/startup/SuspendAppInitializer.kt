package com.answufeng.startup

import android.content.Context
import kotlinx.coroutines.runBlocking

/**
 * 协程兼容的初始化器抽象类。
 *
 * 继承此类可实现 [suspend] 初始化逻辑，适用于天然的异步操作（网络、IO）。
 * 默认使用 [runBlocking] 阻塞等待协程完成，保证执行顺序不变。
 *
 * ```kotlin
 * class DbInit : SuspendAppInitializer() {
 *     override val name = "Database"
 *     override val priority = InitPriority.BACKGROUND
 *     override suspend fun onCreateSuspend(context: Context) {
 *         val db = Room.databaseBuilder(...)
 *             .build()
 *         db.openHelper.writableDatabase
 *     }
 * }
 * ```
 */
abstract class SuspendAppInitializer : AppInitializer() {

    /**
     * 执行协程初始化逻辑。
     *
     * @param context 应用上下文
     */
    abstract suspend fun onCreateSuspend(context: Context)

    override fun onCreate(context: Context) {
        runBlocking { onCreateSuspend(context) }
    }
}
