package com.answufeng.startup.demo

import android.app.Application
import android.content.Context
import android.util.Log
import com.answufeng.startup.AwStartup
import com.answufeng.startup.AppInitializer
import com.answufeng.startup.FailStrategy
import com.answufeng.startup.InitPriority
import com.answufeng.startup.SuspendAppInitializer
import kotlinx.coroutines.delay

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AwStartup.init(this, mainProcessOnly = true) {
            immediately("Logger",
                onCompleted = { Log.d("Startup", "Logger ready") }
            ) {
                Log.d("Startup", "Logger initialized")
            }
            normal("Network", deps = listOf("Logger"),
                onFailed = { Log.e("Startup", "Network init failed", it) }
            ) {
                Log.d("Startup", "Network initialized")
                Thread.sleep(50)
            }
            deferred("Analytics", deps = listOf("Network"),
                onCompleted = { Log.d("Startup", "Analytics ready") }
            ) {
                Log.d("Startup", "Analytics initialized (idle)")
            }
            background("CacheCleaner") {
                Log.d("Startup", "CacheCleaner initialized (background)")
                Thread.sleep(100)
            }
            background("DbPreload",
                onCompleted = { Log.d("Startup", "DbPreload ready") }
            ) {
                Log.d("Startup", "DbPreload initialized (background)")
                Thread.sleep(80)
            }
            add(FirebaseInit())
            add(DbInit())
            failStrategy(FailStrategy.ABORT_DEPENDENTS)
            deferredTimeout(5000)
            onResult { result ->
                val status = when {
                    result.skipped -> "SKIPPED"
                    result.success -> "OK"
                    else -> "FAIL"
                }
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

class DbInit : SuspendAppInitializer() {
    override val name = "Database"
    override val priority = InitPriority.BACKGROUND
    override suspend fun onCreateSuspend(context: Context) {
        delay(50)
        Log.d("Startup", "Database initialized (coroutine)")
    }
}
