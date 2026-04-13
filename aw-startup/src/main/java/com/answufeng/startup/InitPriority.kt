package com.answufeng.startup

/**
 * 初始化器优先级。
 *
 * 决定初始化器的执行时机和所在线程：
 * - [IMMEDIATELY] — 主线程同步执行，用于最关键的初始化（如日志、崩溃收集）
 * - [NORMAL] — 主线程同步执行，用于常规初始化（如网络、数据库）
 * - [DEFERRED] — 主线程延迟执行，用于非关键初始化（如推送、统计）
 * - [BACKGROUND] — 后台线程并发执行，用于耗时操作（如预加载、缓存预热）
 *
 * 执行顺序：IMMEDIATELY → NORMAL → DEFERRED → BACKGROUND
 */
enum class InitPriority {

    IMMEDIATELY,

    NORMAL,

    DEFERRED,

    BACKGROUND
}
