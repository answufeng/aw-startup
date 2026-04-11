package com.answufeng.startup

/**
 * 初始化优先级，决定组件初始化的时机。
 *
 * | 优先级 | 时机 | 线程 | 适用场景 |
 * |--------|------|------|----------|
 * | [IMMEDIATELY] | `Application.onCreate()` 最先 | 主线程同步 | 崩溃收集、日志 |
 * | [NORMAL] | `Application.onCreate()` 正常顺序 | 主线程同步 | 网络、图片、存储 |
 * | [DEFERRED] | 主线程空闲后 | 主线程异步 | 非关键预加载、统计上报 |
 * | [BACKGROUND] | 子线程异步 | IO 线程 | 数据预热、缓存清理 |
 */
enum class InitPriority {
    /**
     * 最高优先级 — 主线程同步，最先执行。
     *
     * 适合必须在所有其他组件之前完成的初始化（崩溃收集、基础日志）。
     */
    IMMEDIATELY,

    /**
     * 正常优先级 — 主线程同步，在 IMMEDIATELY 之后执行。
     *
     * 适合大多数组件初始化（网络、图片加载、键值存储）。
     */
    NORMAL,

    /**
     * 延迟优先级 — 主线程空闲时异步执行（通过 `Looper.myQueue().addIdleHandler`）。
     *
     * 适合非首屏必需的初始化（统计上报、推送注册、预加载）。
     */
    DEFERRED,

    /**
     * 后台优先级 — 在子线程（IO 调度器）异步执行。
     *
     * 适合耗时但不依赖主线程的初始化（磁盘缓存清理、数据预热）。
     */
    BACKGROUND
}
