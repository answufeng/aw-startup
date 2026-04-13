package com.answufeng.startup

import android.content.Context

interface AppInitializer {

    val name: String

    val priority: InitPriority

    val dependencies: List<String> get() = emptyList()

    fun onCreate(context: Context)

    fun onCompleted() {}
}
