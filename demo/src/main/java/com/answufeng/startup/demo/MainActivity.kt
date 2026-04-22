package com.answufeng.startup.demo

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.answufeng.startup.AwStartup
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

class MainActivity : AppCompatActivity() {

    private val logs = ArrayList<LogItem>()
    private lateinit var logAdapter: LogAdapter

    private lateinit var tvSubhead: android.widget.TextView
    private lateinit var tvProgress: android.widget.TextView
    private lateinit var progress: LinearProgressIndicator
    private lateinit var etAwaitTimeout: TextInputEditText

    private var awaitTimeoutMs: Long = 3000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvSubhead = findViewById(R.id.tvSubhead)
        tvProgress = findViewById(R.id.tvProgress)
        progress = findViewById(R.id.progress)
        etAwaitTimeout = findViewById(R.id.etAwaitTimeout)

        setupLogs()
        setupActions()
        refreshHeader()

        pushLog("App ready")
        pushLog("Sync cost: ${AwStartup.getSyncCostMillis()} ms")
    }

    private fun setupLogs() {
        logAdapter = LogAdapter(logs)
        val rv = findViewById<RecyclerView>(R.id.rvLog)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = logAdapter
        rv.itemAnimator = null
    }

    private fun setupActions() {
        findViewById<android.view.View>(R.id.btnReport).setOnClickListener { showReport() }
        findViewById<android.view.View>(R.id.btnAwait).setOnClickListener { awaitBackground() }
        findViewById<android.view.View>(R.id.btnIsInitialized).setOnClickListener { checkInitialized() }
        findViewById<android.view.View>(R.id.btnStore).setOnClickListener { showStore() }
        findViewById<android.view.View>(R.id.btnClearLog).setOnClickListener { clearLog() }
        findViewById<android.view.View>(R.id.btnCopyLog).setOnClickListener { copyLogs() }
        findViewById<android.view.View>(R.id.btnShareLog).setOnClickListener { shareLogs() }

        etAwaitTimeout.doAfterTextChanged {
            awaitTimeoutMs = it?.toString()?.trim()?.toLongOrNull() ?: 3000L
        }
    }

    private fun refreshHeader() {
        tvSubhead.text = "Sync cost: ${AwStartup.getSyncCostMillis()} ms"
        val report = AwStartup.getReport()
        val total = report.size
        val completed = report.count { it.success || it.skipped || it.error != null }
        val ratio = if (total == 0) 0 else (completed * 100 / total)
        progress.progress = ratio
        tvProgress.text = "Progress: $completed/$total"
    }

    private fun pushLog(msg: String, level: LogLevel = LogLevel.INFO) {
        logs.add(LogItem(now(), level, msg))
        logAdapter.notifyItemInserted(max(0, logs.size - 1))
        findViewById<RecyclerView>(R.id.rvLog).scrollToPosition(max(0, logs.size - 1))
    }

    private fun showReport() {
        val report = AwStartup.getReport()
        refreshHeader()
        pushLog("Startup report (${report.size} tasks):", LogLevel.SECTION)
        pushLog("  Sync cost: ${AwStartup.getSyncCostMillis()} ms")
        report.forEach { r ->
            val status = when {
                r.skipped -> "SKIPPED: ${r.error?.message}"
                r.success -> "OK"
                else -> "FAIL: ${r.error?.message}"
            }
            val lvl = when {
                r.success -> LogLevel.SUCCESS
                r.skipped -> LogLevel.WARNING
                else -> LogLevel.ERROR
            }
            pushLog("  ${r.name} [${r.priority}] ${r.costMillis} ms $status", lvl)
        }
    }

    private fun awaitBackground() {
        val timeout = max(100L, awaitTimeoutMs)
        pushLog("Await background tasks (timeout=${timeout}ms)...", LogLevel.ACTION)
        Thread {
            val completed = AwStartup.await(timeout)
            runOnUiThread {
                if (completed) {
                    pushLog("All background tasks completed.", LogLevel.SUCCESS)
                } else {
                    pushLog("Background tasks timeout.", LogLevel.ERROR)
                }
                showReport()
            }
        }.start()
    }

    private fun checkInitialized() {
        refreshHeader()
        pushLog("Initializer states:", LogLevel.SECTION)
        val names = listOf("Logger", "Network", "Config", "Analytics", "CacheCleaner", "DbPreload", "Firebase", "Database")
        names.forEach { name ->
            val initialized = AwStartup.isInitialized(name)
            val status = if (initialized) "DONE" else "PENDING"
            pushLog("  $name: $status", if (initialized) LogLevel.SUCCESS else LogLevel.INFO)
        }
    }

    private fun showStore() {
        val store = AwStartup.getStore()
        refreshHeader()
        pushLog("StartupStore:", LogLevel.SECTION)
        pushLog("  networkReady: ${store.get<Boolean>("networkReady")}")
        pushLog("  dbPreloaded: ${store.get<Boolean>("dbPreloaded")}")
        pushLog("  database: ${store.get<String>("database")}")
    }

    private fun clearLog() {
        logs.clear()
        logAdapter.notifyDataSetChanged()
        pushLog("Logs cleared.", LogLevel.ACTION)
    }

    private fun copyLogs() {
        val text = buildLogsText()
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("AwStartup logs", text))
        pushLog("Copied logs to clipboard.", LogLevel.ACTION)
    }

    private fun shareLogs() {
        val text = buildLogsText()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "AwStartup Demo logs")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, "Share logs"))
    }

    private fun buildLogsText(): String {
        return logs.joinToString(separator = "\n") { item ->
            "${item.time} ${item.level.name}: ${item.msg}"
        }
    }

    private fun now(): String {
        return SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
    }
}

private enum class LogLevel { SECTION, ACTION, INFO, SUCCESS, WARNING, ERROR }

private data class LogItem(
    val time: String,
    val level: LogLevel,
    val msg: String,
)

private class LogAdapter(
    private val items: List<LogItem>,
) : RecyclerView.Adapter<LogAdapter.VH>() {

    class VH(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        val tvMeta: android.widget.TextView = itemView.findViewById(R.id.tvMeta)
        val tvMsg: android.widget.TextView = itemView.findViewById(R.id.tvMsg)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
        val view = android.view.LayoutInflater.from(parent.context).inflate(R.layout.item_log, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvMeta.text = "${item.time} • ${item.level.name}"
        holder.tvMsg.text = item.msg

        val color = when (item.level) {
            LogLevel.SECTION -> R.color.ds_text
            LogLevel.ACTION -> R.color.ds_primary
            LogLevel.INFO -> R.color.ds_text
            LogLevel.SUCCESS -> R.color.ds_success
            LogLevel.WARNING -> R.color.ds_warning
            LogLevel.ERROR -> R.color.ds_error
        }
        holder.tvMsg.setTextColor(androidx.core.content.ContextCompat.getColor(holder.itemView.context, color))
    }
}
