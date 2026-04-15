package com.answufeng.startup.demo

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.answufeng.startup.AwStartup
import com.answufeng.startup.FailStrategy
import com.answufeng.startup.InitPriority
import com.answufeng.startup.SuspendAppInitializer

class MainActivity : AppCompatActivity() {

    private lateinit var tvLog: TextView
    private lateinit var logScrollView: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "aw-startup 演示"

        // 主布局
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 30, 20, 20)
        }

        // 标题
        mainLayout.addView(TextView(this).apply {
            text = "🚀 aw-startup 功能演示"
            textSize = 20f
            setPadding(0, 0, 0, 20)
        })

        // 功能按钮布局
        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 20)
        }

        // 基本功能按钮
        buttonLayout.addView(createButton("📊 查看启动报告") { showReport() })
        buttonLayout.addView(createButton("⏳ 等待后台任务") { awaitBackground() })

        // 高级功能按钮
        buttonLayout.addView(createButton("🔗 演示依赖关系") { registerDependencyDemo() })
        buttonLayout.addView(createButton("🔍 演示优先级") { registerPriorityDemo() })
        buttonLayout.addView(createButton("⚠️ 演示错误处理") { registerErrorDemo() })
        buttonLayout.addView(createButton("🔄 重新启动任务") { restartTasks() })

        // 管理按钮
        buttonLayout.addView(createButton("🗑️ 清除日志") { clearLog() })

        mainLayout.addView(buttonLayout)

        // 日志区域
        mainLayout.addView(TextView(this).apply {
            text = "操作日志："
            textSize = 16f
            setPadding(0, 10, 0, 10)
        })

        logScrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                400
            )
        }

        tvLog = TextView(this).apply {
            text = "日志输出将显示在这里..."
            setPadding(10, 10, 10, 10)
        }

        logScrollView.addView(tvLog)
        mainLayout.addView(logScrollView)

        setContentView(mainLayout)

        log("✅ 应用启动完成")
        log("📊 点击按钮查看启动报告")
    }

    private fun createButton(text: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 8, 0, 8)
            }
            setOnClickListener { onClick() }
        }
    }

    private fun log(msg: String) {
        tvLog.append("$msg\n")
        logScrollView.post { logScrollView.fullScroll(ScrollView.FOCUS_DOWN) }
        android.util.Log.d("AwStartupDemo", msg)
    }

    private fun clearLog() {
        tvLog.text = "日志已清除\n"
    }

    private fun showReport() {
        val report = AwStartup.getReport()
        log("📊 启动报告 (共${report.size}个任务):")
        log("  同步耗时: ${AwStartup.getSyncCostMillis()}ms")
        log("  总耗时: ${AwStartup.getTotalCostMillis()}ms")
        report.forEachIndexed { index, r ->
            val status = if (r.success) "✅ 成功" else "❌ 失败: ${r.error?.message ?: "未知错误"}"
            log("  ${index + 1}. ${r.name} [优先级:${r.priority}] ${r.costMillis}ms $status")
        }
    }

    private fun awaitBackground() {
        log("⏳ 开始等待后台任务...")
        Thread {
            val completed = AwStartup.await(5000)
            runOnUiThread {
                if (completed) {
                    log("✅ 所有后台任务已完成！")
                } else {
                    log("❌ 后台任务超时！")
                }
                showReport()
            }
        }.start()
    }

    private fun registerDependencyDemo() {
        log("🔗 注册依赖关系演示任务...")

        // 任务 A：基础配置
        class TaskA : SuspendAppInitializer {
            override val name = "TaskA"
            override val priority = InitPriority.NORMAL
            override val dependencies = emptyList<String>()

            override suspend fun initialize() {
                Thread.sleep(500)
                log("✅ TaskA 完成 (基础配置)")
            }
        }

        // 任务 B：依赖 TaskA
        class TaskB : SuspendAppInitializer {
            override val name = "TaskB"
            override val priority = InitPriority.NORMAL
            override val dependencies = listOf("TaskA")

            override suspend fun initialize() {
                Thread.sleep(300)
                log("✅ TaskB 完成 (依赖 TaskA)")
            }
        }

        // 任务 C：依赖 TaskB
        class TaskC : SuspendAppInitializer {
            override val name = "TaskC"
            override val priority = InitPriority.NORMAL
            override val dependencies = listOf("TaskB")

            override suspend fun initialize() {
                Thread.sleep(400)
                log("✅ TaskC 完成 (依赖 TaskB)")
            }
        }

        AwStartup.register(TaskA())
        AwStartup.register(TaskB())
        AwStartup.register(TaskC())

        log("🔗 依赖关系：TaskA → TaskB → TaskC")
        log("🔗 启动任务链...")

        Thread {
            AwStartup.start()
            runOnUiThread {
                log("✅ 依赖关系演示完成")
                showReport()
            }
        }.start()
    }

    private fun registerPriorityDemo() {
        log("🔍 注册优先级演示任务...")

        // 低优先级任务
        class LowPriorityTask : SuspendAppInitializer {
            override val name = "LowPriority"
            override val priority = InitPriority.LOW
            override val dependencies = emptyList<String>()

            override suspend fun initialize() {
                Thread.sleep(800)
                log("✅ LowPriority 完成")
            }
        }

        // 普通优先级任务
        class NormalPriorityTask : SuspendAppInitializer {
            override val name = "NormalPriority"
            override val priority = InitPriority.NORMAL
            override val dependencies = emptyList<String>()

            override suspend fun initialize() {
                Thread.sleep(500)
                log("✅ NormalPriority 完成")
            }
        }

        // 高优先级任务
        class HighPriorityTask : SuspendAppInitializer {
            override val name = "HighPriority"
            override val priority = InitPriority.HIGH
            override val dependencies = emptyList<String>()

            override suspend fun initialize() {
                Thread.sleep(300)
                log("✅ HighPriority 完成")
            }
        }

        // 最高优先级任务
        class CriticalPriorityTask : SuspendAppInitializer {
            override val name = "CriticalPriority"
            override val priority = InitPriority.CRITICAL
            override val dependencies = emptyList<String>()

            override suspend fun initialize() {
                Thread.sleep(200)
                log("✅ CriticalPriority 完成")
            }
        }

        AwStartup.register(LowPriorityTask())
        AwStartup.register(NormalPriorityTask())
        AwStartup.register(HighPriorityTask())
        AwStartup.register(CriticalPriorityTask())

        log("🔍 优先级顺序：CRITICAL > HIGH > NORMAL > LOW")
        log("🔍 启动优先级演示...")

        Thread {
            AwStartup.start()
            runOnUiThread {
                log("✅ 优先级演示完成")
                showReport()
            }
        }.start()
    }

    private fun registerErrorDemo() {
        log("⚠️ 注册错误处理演示任务...")

        // 成功任务
        class SuccessTask : SuspendAppInitializer {
            override val name = "SuccessTask"
            override val priority = InitPriority.NORMAL
            override val dependencies = emptyList<String>()

            override suspend fun initialize() {
                Thread.sleep(200)
                log("✅ SuccessTask 完成")
            }
        }

        // 失败任务
        class ErrorTask : SuspendAppInitializer {
            override val name = "ErrorTask"
            override val priority = InitPriority.NORMAL
            override val dependencies = emptyList<String>()

            override suspend fun initialize() {
                Thread.sleep(300)
                throw RuntimeException("模拟初始化失败")
            }
        }

        // 依赖失败任务的任务
        class DependentTask : SuspendAppInitializer {
            override val name = "DependentTask"
            override val priority = InitPriority.NORMAL
            override val dependencies = listOf("ErrorTask")

            override suspend fun initialize() {
                Thread.sleep(200)
                log("✅ DependentTask 完成")
            }
        }

        AwStartup.register(SuccessTask())
        AwStartup.register(ErrorTask())
        AwStartup.register(DependentTask())

        log("⚠️ 错误处理策略：SKIP (跳过失败任务，继续执行其他任务)")
        log("⚠️ 启动错误处理演示...")

        Thread {
            AwStartup.start(failStrategy = FailStrategy.SKIP)
            runOnUiThread {
                log("✅ 错误处理演示完成")
                showReport()
            }
        }.start()
    }

    private fun restartTasks() {
        log("🔄 重新启动所有任务...")
        Thread {
            AwStartup.start()
            runOnUiThread {
                log("✅ 任务重启完成")
                showReport()
            }
        }.start()
    }
}
