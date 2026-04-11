package com.answufeng.startup

import android.app.Application
import android.content.Context
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * BrickStartup Robolectric 测试 — 验证完整的初始化生命周期。
 *
 * 覆盖场景：
 * - IMMEDIATELY / NORMAL 同步执行顺序
 * - DSL 方式初始化
 * - 结果回调
 * - 错误隔离
 * - 重复调用保护
 * - 依赖关系排序
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BrickStartupRobolectricTest {

    private val context: Application get() = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() {
        BrickStartup.reset()
    }

    // ── IMMEDIATELY + NORMAL 同步执行 ──

    @Test
    fun `start executes IMMEDIATELY then NORMAL in order`() {
        val order = mutableListOf<String>()

        BrickStartup.register(object : AppInitializer {
            override val name = "Normal1"
            override val priority = InitPriority.NORMAL
            override fun onCreate(context: Context) { order.add("Normal1") }
        })
        BrickStartup.register(object : AppInitializer {
            override val name = "Immediate1"
            override val priority = InitPriority.IMMEDIATELY
            override fun onCreate(context: Context) { order.add("Immediate1") }
        })

        BrickStartup.start(context)

        // IMMEDIATELY should come before NORMAL
        assertTrue(order.indexOf("Immediate1") < order.indexOf("Normal1"))
    }

    @Test
    fun `start sets isStarted to true`() {
        assertFalse(BrickStartup.isStarted)
        BrickStartup.register(fakeInitializer("A", InitPriority.NORMAL))
        BrickStartup.start(context)
        assertTrue(BrickStartup.isStarted)
    }

    @Test
    fun `start populates report`() {
        BrickStartup.register(fakeInitializer("A", InitPriority.IMMEDIATELY))
        BrickStartup.register(fakeInitializer("B", InitPriority.NORMAL))
        BrickStartup.start(context)

        val report = BrickStartup.getReport()
        assertTrue(report.any { it.name == "A" && it.success })
        assertTrue(report.any { it.name == "B" && it.success })
    }

    @Test
    fun `getSyncCostMillis returns non-negative value`() {
        BrickStartup.register(fakeInitializer("A", InitPriority.NORMAL))
        BrickStartup.start(context)
        assertTrue(BrickStartup.getSyncCostMillis() >= 0)
    }

    // ── DSL 方式初始化 ──

    @Test
    fun `init DSL registers and starts initializers`() {
        val executed = mutableListOf<String>()

        BrickStartup.init(context) {
            add(object : AppInitializer {
                override val name = "DslInit"
                override val priority = InitPriority.NORMAL
                override fun onCreate(context: Context) { executed.add(name) }
            })
        }

        assertTrue(BrickStartup.isStarted)
        assertEquals(listOf("DslInit"), executed)
    }

    @Test
    fun `init DSL fires result callback`() {
        val results = mutableListOf<InitResult>()

        BrickStartup.init(context) {
            add(fakeInitializer("X", InitPriority.IMMEDIATELY))
            onResult { results.add(it) }
        }

        assertEquals(1, results.size)
        assertEquals("X", results[0].name)
        assertTrue(results[0].success)
    }

    // ── 错误隔离 ──

    @Test
    fun `failing initializer does not block subsequent ones`() {
        val order = mutableListOf<String>()

        BrickStartup.register(object : AppInitializer {
            override val name = "Fail"
            override val priority = InitPriority.NORMAL
            override fun onCreate(context: Context) { throw RuntimeException("boom") }
        })
        BrickStartup.register(object : AppInitializer {
            override val name = "After"
            override val priority = InitPriority.NORMAL
            override fun onCreate(context: Context) { order.add("After") }
        })

        BrickStartup.start(context)

        assertTrue(order.contains("After"))
        val report = BrickStartup.getReport()
        assertFalse(report.first { it.name == "Fail" }.success)
        assertTrue(report.first { it.name == "After" }.success)
    }

    // ── 重复调用保护 ──

    @Test
    fun `start ignores duplicate calls`() {
        var count = 0
        BrickStartup.register(object : AppInitializer {
            override val name = "Counter"
            override val priority = InitPriority.NORMAL
            override fun onCreate(context: Context) { count++ }
        })

        BrickStartup.start(context)
        BrickStartup.start(context)

        assertEquals(1, count)
    }

    @Test
    fun `register after start throws`() {
        BrickStartup.register(fakeInitializer("A", InitPriority.NORMAL))
        BrickStartup.start(context)

        try {
            BrickStartup.register(fakeInitializer("B", InitPriority.NORMAL))
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("已启动"))
        }
    }

    // ── 依赖排序 ──

    @Test
    fun `dependency ordering within same priority`() {
        val order = mutableListOf<String>()

        BrickStartup.register(object : AppInitializer {
            override val name = "B"
            override val priority = InitPriority.NORMAL
            override val dependencies = listOf("A")
            override fun onCreate(context: Context) { order.add("B") }
        })
        BrickStartup.register(object : AppInitializer {
            override val name = "A"
            override val priority = InitPriority.NORMAL
            override fun onCreate(context: Context) { order.add("A") }
        })

        BrickStartup.start(context)

        assertEquals(listOf("A", "B"), order)
    }

    // ── reset ──

    @Test
    fun `reset clears all state`() {
        BrickStartup.register(fakeInitializer("A", InitPriority.NORMAL))
        BrickStartup.start(context)
        assertTrue(BrickStartup.isStarted)

        BrickStartup.reset()

        assertFalse(BrickStartup.isStarted)
        assertTrue(BrickStartup.getReport().isEmpty())
        assertEquals(0L, BrickStartup.getSyncCostMillis())
    }

    @Test
    fun `reset rebuilds background executor for subsequent start`() {
        val firstRun = CountDownLatch(1)
        val secondRun = CountDownLatch(1)

        BrickStartup.register(object : AppInitializer {
            override val name = "Bg1"
            override val priority = InitPriority.BACKGROUND
            override fun onCreate(context: Context) {
                firstRun.countDown()
            }
        })

        BrickStartup.start(context)
        assertTrue(firstRun.await(3, TimeUnit.SECONDS))

        BrickStartup.reset()

        BrickStartup.register(object : AppInitializer {
            override val name = "Bg2"
            override val priority = InitPriority.BACKGROUND
            override fun onCreate(context: Context) {
                secondRun.countDown()
            }
        })

        BrickStartup.start(context)
        assertTrue(secondRun.await(3, TimeUnit.SECONDS))
    }

    // ── helper ──

    private fun fakeInitializer(n: String, p: InitPriority) = object : AppInitializer {
        override val name = n
        override val priority = p
        override fun onCreate(context: Context) {}
    }
}
