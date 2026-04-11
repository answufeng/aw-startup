package com.answufeng.startup

/**
 * 初始化结果，记录每个初始化器的执行信息。
 *
 * @param name 初始化器名称
 * @param priority 初始化优先级
 * @param costMillis 耗时（毫秒）
 * @param success 是否成功
 * @param error 失败异常（成功时为 null）
 */
data class InitResult(
    val name: String,
    val priority: InitPriority,
    val costMillis: Long,
    val success: Boolean,
    val error: Throwable? = null
)
