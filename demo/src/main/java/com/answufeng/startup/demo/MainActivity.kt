package com.answufeng.startup.demo

import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.answufeng.startup.AwStartup

class MainActivity : AppCompatActivity() {

    private lateinit var tvLog: TextView
    private lateinit var logScrollView: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        title = "aw-startup 演示"

        tvLog = findViewById(R.id.tvLog)
        logScrollView = findViewById(R.id.logScrollView)

        findViewById<Button>(R.id.btnReport).setOnClickListener { showReport() }
        findViewById<Button>(R.id.btnAwait).setOnClickListener { awaitBackground() }
        findViewById<Button>(R.id.btnIsInitialized).setOnClickListener { checkInitialized() }
        findViewById<Button>(R.id.btnStore).setOnClickListener { showStore() }
        findViewById<Button>(R.id.btnClearLog).setOnClickListener { clearLog() }

        log("✅ 应用启动完成")
        log("📊 同步耗时: ${AwStartup.getSyncCostMillis()}ms")
        log("")
    }

    private fun log(msg: String) {
        tvLog.append("$msg\n")
        logScrollView.post { logScrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun clearLog() {
        tvLog.text = ""
        log("📝 日志已清除")
    }

    private fun showReport() {
        val report = AwStartup.getReport()
        log("📊 启动报告 (共${report.size}个任务):")
        log("  同步耗时: ${AwStartup.getSyncCostMillis()}ms")
        report.forEach { r ->
            val status = when {
                r.skipped -> "⏭️ 跳过: ${r.error?.message}"
                r.success -> "✅ 成功"
                else -> "❌ 失败: ${r.error?.message}"
            }
            log("  ${r.name} [${r.priority}] ${r.costMillis}ms $status")
        }
        log("")
    }

    private fun awaitBackground() {
        log("⏳ 开始等待后台任务...")
        Thread {
            val completed = AwStartup.await(3000)
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

    private fun checkInitialized() {
        log("🔍 初始化器状态查询:")
        val names = listOf("Logger", "Network", "Config", "Analytics", "CacheCleaner", "DbPreload", "Firebase", "Database")
        names.forEach { name ->
            val initialized = AwStartup.isInitialized(name)
            val status = if (initialized) "✅ 已完成" else "⏳ 未完成"
            log("  $name: $status")
        }
        log("")
    }

    private fun showStore() {
        val store = AwStartup.getStore()
        log("📦 StartupStore 数据:")
        log("  networkReady: ${store.get<Boolean>("networkReady")}")
        log("  dbPreloaded: ${store.get<Boolean>("dbPreloaded")}")
        log("  database: ${store.get<String>("database")}")
        log("")
    }
}
