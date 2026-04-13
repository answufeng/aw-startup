package com.answufeng.startup.demo

import android.app.Application
import android.content.Context
import android.util.Log
import com.answufeng.startup.AwStartup
import com.answufeng.startup.AppInitializer
import com.answufeng.startup.InitPriority

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AwStartup.init(this) {
            add(LoggerInit())
            add(NetworkInit())
            add(AnalyticsInit())
            add(CacheInit())
            onResult { result ->
                Log.d("aw-startup", "${result.name} [${result.priority}] ${result.costMillis}ms ${if (result.success) "OK" else "FAIL"}")
            }
        }
    }
}

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
