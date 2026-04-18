package com.answufeng.startup

/**
 * 启动库日志接口。
 *
 * 提供自定义日志实现的能力，例如写入文件、上报到 APM 平台等。
 * 通过 [StartupConfig.logger] 注入自定义实现。
 *
 * 使用方式：
 * ```kotlin
 * AwStartup.init(this) {
 *     logger(object : StartupLogger {
 *         override fun d(tag: String, msg: String) { myLogger.d(tag, msg) }
 *         override fun w(tag: String, msg: String, t: Throwable?) { myLogger.w(tag, msg, t) }
 *         override fun e(tag: String, msg: String, t: Throwable?) { myLogger.e(tag, msg, t) }
 *     })
 * }
 * ```
 *
 * @see StartupConfig.logger
 */
interface StartupLogger {

    /** 输出调试日志。 */
    fun d(tag: String, msg: String)

    /** 输出警告日志。 */
    fun w(tag: String, msg: String, t: Throwable? = null)

    /** 输出错误日志。 */
    fun e(tag: String, msg: String, t: Throwable? = null)

    companion object {
        /** 默认实现，使用 [android.util.Log]。 */
        val DEFAULT: StartupLogger = object : StartupLogger {
            override fun d(tag: String, msg: String) {
                android.util.Log.d(tag, msg)
            }

            override fun w(tag: String, msg: String, t: Throwable?) {
                android.util.Log.w(tag, msg, t)
            }

            override fun e(tag: String, msg: String, t: Throwable?) {
                android.util.Log.e(tag, msg, t)
            }
        }

        /** 空实现，不输出任何日志。 */
        val NOOP: StartupLogger = object : StartupLogger {
            override fun d(tag: String, msg: String) {}
            override fun w(tag: String, msg: String, t: Throwable?) {}
            override fun e(tag: String, msg: String, t: Throwable?) {}
        }
    }
}
