package com.answufeng.startup

import android.content.Context
import kotlinx.coroutines.runBlocking

/**
 * 协程兼容的初始化器抽象类。
 *
 * 继承此类可实现 [suspend] 初始化逻辑，适用于天然的异步操作（网络、IO）。
 *
 * **线程说明**：由 [AwStartup] / [StartupRunner] 调度时，若在**主线程**上执行（IMMEDIATELY /
 * NORMAL / DEFERRED），挂起逻辑会在临时后台线程中通过 [runBlocking] 运行，以避免占用主线程
 * Looper 时与 [kotlinx.coroutines.Dispatchers.Main] 等调度产生死锁。若必须触碰主线程 API，
 * 请在协程内使用 `withContext(Dispatchers.Main)`（此时由后台线程 post 到主线程，安全）或改用
 * 非挂起的 [StartupInitializer] 并在 [StartupInitializer.onCreate] 内自行 `Handler` 投递。
 *
 * [onCreate] 默认仍使用 [runBlocking]，供你在库外手动调用时保持行为一致。
 *
 * ```kotlin
 * class DbInit : SuspendInitializer() {
 *     override val name = "Database"
 *     override val priority = InitPriority.BACKGROUND
 *     override suspend fun onCreateSuspend(context: Context) {
 *         val db = Room.databaseBuilder(...)
 *             .build()
 *         db.openHelper.writableDatabase
 *     }
 * }
 * ```
 *
 * @see StartupInitializer
 */
abstract class SuspendInitializer : StartupInitializer() {

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

/**
 * 已废弃：使用 [SuspendInitializer] 替代。
 */
@Deprecated(
    message = "Use SuspendInitializer instead",
    replaceWith = ReplaceWith("SuspendInitializer", "com.answufeng.startup.SuspendInitializer"),
    level = DeprecationLevel.WARNING
)
typealias SuspendAppInitializer = SuspendInitializer
