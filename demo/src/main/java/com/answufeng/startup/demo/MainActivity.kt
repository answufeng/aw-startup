package com.answufeng.startup.demo

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.answufeng.startup.AppInitializer
import com.answufeng.startup.BrickStartup
import com.answufeng.startup.InitPriority
import com.answufeng.startup.InitResult
import android.content.Context

class LoggerInit : AppInitializer {
    override val name = "Logger"
    override val priority = InitPriority.IMMEDIATELY
    override fun onCreate(context: Context) {
        Thread.sleep(50)
    }
}

class NetworkInit : AppInitializer {
    override val name = "Network"
    override val priority = InitPriority.NORMAL
    override val dependencies = listOf("Logger")
    override fun onCreate(context: Context) {
        Thread.sleep(80)
    }
}

class AnalyticsInit : AppInitializer {
    override val name = "Analytics"
    override val priority = InitPriority.DEFERRED
    override val dependencies = listOf("Network")
    override fun onCreate(context: Context) {
        Thread.sleep(30)
    }
}

class CacheInit : AppInitializer {
    override val name = "CacheCleaner"
    override val priority = InitPriority.BACKGROUND
    override fun onCreate(context: Context) {
        Thread.sleep(100)
    }
}

class MainActivity : AppCompatActivity() {

    private lateinit var tvLog: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvLog = TextView(this).apply { textSize = 14f }
        val container = findViewById<LinearLayout>(R.id.container)
        container.addView(tvLog)

        container.addView(button("Run Startup") { runStartup() })
        container.addView(button("Show Report") { showReport() })
    }

    private fun runStartup() {
        if (BrickStartup.isStarted) {
            log("Already started!")
            return
        }
        BrickStartup.init(this) {
            add(LoggerInit())
            add(NetworkInit())
            add(AnalyticsInit())
            add(CacheInit())
            onResult { result ->
                runOnUiThread { log("  ${result.name} [${result.priority}] ${result.costMillis}ms ${if (result.success) "OK" else "FAIL"}") }
            }
        }
        log("Sync cost: ${BrickStartup.getSyncCostMillis()}ms")
    }

    private fun showReport() {
        val report = BrickStartup.getReport()
        log("Init Report (${report.size} items):")
        report.forEach { r ->
            log("  ${r.name} [${r.priority}] ${r.costMillis}ms ${if (r.success) "OK" else "FAIL: ${r.error?.message}"}")
        }
    }

    private fun button(text: String, onClick: () -> Unit): Button {
        return Button(this).apply { this.text = text; setOnClickListener { onClick() } }
    }

    private fun log(msg: String) { tvLog.append("$msg\n") }
}
