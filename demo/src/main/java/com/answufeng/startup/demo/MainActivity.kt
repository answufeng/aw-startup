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
        findViewById<Button>(R.id.btnClearLog).setOnClickListener { clearLog() }

        log("✅ 应用启动完成")
        log("📊 点击按钮查看启动报告")
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
            val status = if (r.success) "✅ 成功" else "❌ 失败: ${r.error?.message}"
            log("  ${r.name} [优先级:${r.priority}] ${r.costMillis}ms $status")
        }
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
}
