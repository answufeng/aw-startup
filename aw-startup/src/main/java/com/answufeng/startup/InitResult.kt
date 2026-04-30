package com.answufeng.startup

enum class SkipReason {
    DISABLED,
    DEPENDENCY_FAILED
}

data class InitResult(
    val name: String,
    val priority: InitPriority,
    val costMillis: Long,
    val success: Boolean,
    val error: Throwable? = null,
    val skipped: Boolean = false,
    val skipReason: SkipReason? = null
)
