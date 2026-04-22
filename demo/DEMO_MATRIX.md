# aw-startup Demo 功能矩阵

| 操作 | 说明 |
|------|------|
| Report | 打印初始化报告、同步耗时 |
| Await | 等待后台任务（可改超时 ms） |
| Store | `StartupStore` 共享数据 |
| 日志列表 | 各 `Initializer` 输出 |

与 `App.kt` 中注册的 **IMMEDIATELY / NORMAL / DEFERRED / BACKGROUND** 任务对照；DEFERRED 超时行为见 README。工具栏 **「演示清单」** 可查看本摘要。

## 推荐手测（边界与极端场景）

| 场景 | 建议操作 |
|------|----------|
| DEFERRED | 主线程繁忙时观察 Idle 任务是否在超时后仍被执行 |
| 失败策略 | 人为让某一初始化失败，看 `ABORT_DEPENDENTS` 与 `CONTINUE` 差异 |
| 低内存 | 后台冷启动 + 低内存杀进程后重启，对照报告耗时 |
| 多进程 | 若宿主多进程，验证 `mainProcessOnly` 与子进程行为 |
