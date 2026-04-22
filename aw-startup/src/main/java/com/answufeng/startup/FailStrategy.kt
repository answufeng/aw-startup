package com.answufeng.startup

/**
 * 失败策略。
 *
 * 决定当初始化器失败时，如何处理依赖该失败初始化器的后续任务。
 *
 * **注意**：[ABORT_DEPENDENTS] 仅针对**执行失败**的依赖（会进入失败集合）。
 * 若依赖方因 [StartupInitializer.enabled] 为 false 被跳过，不计入失败，下游仍会执行。
 */
enum class FailStrategy {

    /** 单个失败后继续执行后续初始化器（默认）。 */
    CONTINUE,

    /** 依赖失败者的任务跳过执行，标记为 skipped。 */
    ABORT_DEPENDENTS
}
