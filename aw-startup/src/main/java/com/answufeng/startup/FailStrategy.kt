package com.answufeng.startup

/**
 * 失败策略。
 *
 * 决定当初始化器失败时，如何处理依赖该失败初始化器的后续任务。
 */
enum class FailStrategy {

    /** 单个失败后继续执行后续初始化器（默认）。 */
    CONTINUE,

    /** 依赖失败者的任务跳过执行，标记为 skipped。 */
    ABORT_DEPENDENTS
}
