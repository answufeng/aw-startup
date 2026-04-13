# Changelog

## 1.0.0

- 4 级优先级：IMMEDIATELY、NORMAL、DEFERRED、BACKGROUND
- 拓扑排序，带循环依赖检测
- 后台线程池，使用 CountDownLatch 保证依赖顺序
- DEFERRED 优先级使用 IdleHandler 延迟到空闲时执行
- 错误隔离：单个初始化器失败不影响后续
- DSL 配置方式，支持结果回调
- 初始化报告，含耗时统计
- 可配置后台线程数
- await 等待后台任务完成
