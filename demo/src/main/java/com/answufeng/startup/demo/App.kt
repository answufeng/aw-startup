package com.answufeng.startup.demo

import android.app.Application
import android.content.Context
import android.util.Log
import com.answufeng.startup.AwStartup
import com.answufeng.startup.AppInitializer
import com.answufeng.startup.FailStrategy
import com.answufeng.startup.InitPriority

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AwStartup.init(this) {
            immediately("Logger") {
                Log.d("Startup", "Logger initialized")
            }
            normal("Network", deps = listOf("Logger")) {
                Log.d("Startup", "Network initialized")
            }
            deferred("Analytics", deps = listOf("Network")) {
                Log.d("Startup", "Analytics initialized (idle)")
            }
            background("CacheCleaner") {
                Log.d("Startup", "CacheCleaner initialized (background)")
            }
            add(FirebaseInit())
            failStrategy(FailStrategy.ABORT_DEPENDENTS)
            onResult { result ->
                val status = if (result.success) "OK" else "FAIL"
                Log.d("aw-startup", "${result.name} [${result.priority}] ${result.costMillis}ms $status")
            }
        }
    }
}

class FirebaseInit : AppInitializer() {
    override val name = "Firebase"
    override val priority = InitPriority.IMMEDIATELY
    override fun onCreate(context: Context) {
        Log.d("Startup", "Firebase initialized")
    }
    override fun onCompleted() {
        Log.d("Startup", "Firebase ready")
    }
}
