package com.answufeng.startup.demo

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.answufeng.startup.AwStartup

class MainActivity : AppCompatActivity() {

    private lateinit var tvLog: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvLog = findViewById(R.id.tvLog)
        val container = findViewById<LinearLayout>(R.id.container)

        container.addView(button("Show Report") { showReport() })
        container.addView(button("Await Background") { awaitBackground() })
    }

    private fun showReport() {
        val report = AwStartup.getReport()
        log("Init Report (${report.size} items):")
        log("  Sync cost: ${AwStartup.getSyncCostMillis()}ms")
        report.forEach { r ->
            log("  ${r.name} [${r.priority}] ${r.costMillis}ms ${if (r.success) "OK" else "FAIL: ${r.error?.message}"}")
        }
    }

    private fun awaitBackground() {
        Thread {
            val completed = AwStartup.await(3000)
            runOnUiThread {
                if (completed) {
                    log("All background tasks completed!")
                } else {
                    log("Background tasks timed out!")
                }
                showReport()
            }
        }.start()
    }

    private fun button(text: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            setOnClickListener { onClick() }
        }
    }

    private fun log(msg: String) {
        tvLog.append("$msg\n")
    }
}
