package com.answufeng.startup

data class InitResult(
    val name: String,
    val priority: InitPriority,
    val costMillis: Long,
    val success: Boolean,
    val error: Throwable? = null,
    val skipped: Boolean = false
)
