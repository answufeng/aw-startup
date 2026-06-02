package com.answufeng.startup.demo

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.answufeng.startup.AwStartup
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var tvSyncCost: TextView
    private lateinit var tvTotalCount: TextView
    private lateinit var tvCompleted: TextView
    private lateinit var progress: LinearProgressIndicator
    private lateinit var etAwaitTimeout: TextInputEditText
    private lateinit var tvLog: TextView

    private var awaitTimeoutMs: Long = 3000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupViews()
        refreshAll()
        appendLog("应用已就绪，同步耗时: ${AwStartup.getSyncCostMillis()}ms")
    }

    private fun setupViews() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        tvSyncCost = findViewById(R.id.tvSyncCost)
        tvTotalCount = findViewById(R.id.tvTotalCount)
        tvCompleted = findViewById(R.id.tvCompleted)
        progress = findViewById(R.id.progress)
        etAwaitTimeout = findViewById(R.id.etAwaitTimeout)
        tvLog = findViewById(R.id.tvLog)
        tvLog.movementMethod = ScrollingMovementMethod()

        findViewById<MaterialButton>(R.id.btnRefresh).setOnClickListener {
            refreshAll()
            appendLog("状态已刷新")
        }

        findViewById<MaterialButton>(R.id.btnReport).setOnClickListener { showReport() }
        findViewById<MaterialButton>(R.id.btnAwait).setOnClickListener { awaitBackground() }
        findViewById<MaterialButton>(R.id.btnCheckInit).setOnClickListener { checkAllInitializers() }
        findViewById<MaterialButton>(R.id.btnStore).setOnClickListener { showStore() }
        findViewById<MaterialButton>(R.id.btnClearLog).setOnClickListener { clearLog() }

        etAwaitTimeout.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                awaitTimeoutMs = etAwaitTimeout.text?.toString()?.trim()?.toLongOrNull() ?: 3000L
            }
        }
    }

    private fun refreshAll() {
        val report = AwStartup.getReport()
        val total = report.size
        val completed = report.count { it.success || it.skipped }

        tvSyncCost.text = "${AwStartup.getSyncCostMillis()}ms"
        tvTotalCount.text = total.toString()
        tvCompleted.text = completed.toString()

        val ratio = if (total == 0) 0 else (completed * 100 / total)
        progress.progress = ratio
    }

    private fun showReport() {
        val report = AwStartup.getReport()
        val sb = StringBuilder()
        sb.appendLine("同步耗时: ${AwStartup.getSyncCostMillis()}ms")
        sb.appendLine("共 ${report.size} 个初始化器")
        sb.appendLine("---")
        report.forEach { r ->
            val status = when {
                r.skipped -> "跳过 (${r.skipReason})"
                r.success -> "OK"
                else -> "失败: ${r.error?.message}"
            }
            sb.appendLine("${r.name} [${r.priority}] ${r.costMillis}ms $status")
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("启动报告")
            .setMessage(sb.toString())
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun awaitBackground() {
        val timeout = max(100L, awaitTimeoutMs)
        appendLog("等待后台任务完成 (超时: ${timeout}ms)...")
        findViewById<MaterialButton>(R.id.btnAwait).isEnabled = false

        thread {
            val completed = AwStartup.await(timeout)
            runOnUiThread {
                findViewById<MaterialButton>(R.id.btnAwait).isEnabled = true
                if (completed) {
                    appendLog("后台任务全部完成")
                } else {
                    appendLog("后台任务超时 ($timeout ms)")
                }
                refreshAll()
            }
        }
    }

    private fun checkAllInitializers() {
        val names = listOf(
            "Logger", "Network", "Config", "Analytics",
            "CacheCleaner", "DbPreload", "Firebase", "Database"
        )
        val sb = StringBuilder()
        names.forEach { name ->
            val done = AwStartup.isInitialized(name)
            sb.appendLine("$name: ${if (done) "✓ 已完成" else "… 等待中"}")
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("初始器状态")
            .setMessage(sb.toString())
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showStore() {
        val store = AwStartup.getStore()
        val sb = StringBuilder()
        val keys = listOf("networkReady", "dbPreloaded", "database")
        keys.forEach { key ->
            val value = store.get<Any>(key)
            sb.appendLine("$key = $value")
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("共享存储 (StartupStore)")
            .setMessage(sb.toString())
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun appendLog(msg: String) {
        tvLog.append("[${simpleTime()}] $msg\n")
        tvLog.post {
            (tvLog.parent.parent as? ScrollView)?.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun clearLog() {
        tvLog.text = ""
    }

    private fun simpleTime(): String =
        SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
}