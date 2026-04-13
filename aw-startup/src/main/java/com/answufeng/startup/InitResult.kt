package com.answufeng.startup

/**
 * 单个初始化器的执行结果。
 *
 * @property name 初始化器名称
 * @property priority 执行优先级
 * @property costMillis 执行耗时（毫秒）
 * @property success 是否执行成功
 * @property error 执行失败的异常，成功时为 null
 */
data class InitResult(
    val name: String,
    val priority: InitPriority,
    val costMillis: Long,
    val success: Boolean,
    val error: Throwable? = null
)
