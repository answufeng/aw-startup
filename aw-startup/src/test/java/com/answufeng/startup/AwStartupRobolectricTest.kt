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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AwStartupRobolectricTest {

    private val context: Application get() = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() {
        AwStartup.reset()
    }

    @Test
    fun `start executes IMMEDIATELY then NORMAL in order`() {
        val order = mutableListOf<String>()

        AwStartup.register(object : AppInitializer() {
            override val name = "Normal1"
            override val priority = InitPriority.NORMAL
            override fun onCreate(context: Context) { order.add("Normal1") }
        })
        AwStartup.register(object : AppInitializer() {
            override val name = "Immediate1"
            override val priority = InitPriority.IMMEDIATELY
            override fun onCreate(context: Context) { order.add("Immediate1") }
        })

        AwStartup.start(context)

        assertTrue(order.indexOf("Immediate1") < order.indexOf("Normal1"))
    }

    @Test
    fun `start sets isStarted to true`() {
        assertFalse(AwStartup.isStarted)
        AwStartup.register(fakeInitializer("A", InitPriority.NORMAL))
        AwStartup.start(context)
        assertTrue(AwStartup.isStarted)
    }

    @Test
    fun `start populates report`() {
        AwStartup.register(fakeInitializer("A", InitPriority.IMMEDIATELY))
        AwStartup.register(fakeInitializer("B", InitPriority.NORMAL))
        AwStartup.start(context)

        val report = AwStartup.getReport()
        assertTrue(report.any { it.name == "A" && it.success })
        assertTrue(report.any { it.name == "B" && it.success })
    }

    @Test
    fun `getSyncCostMillis returns non-negative value`() {
        AwStartup.register(fakeInitializer("A", InitPriority.NORMAL))
        AwStartup.start(context)
        assertTrue(AwStartup.getSyncCostMillis() >= 0)
    }

    @Test
    fun `init DSL registers and starts initializers`() {
        val executed = mutableListOf<String>()

        AwStartup.init(context) {
            add(object : AppInitializer() {
                override val name = "DslInit"
                override val priority = InitPriority.NORMAL
                override fun onCreate(context: Context) { executed.add(name) }
            })
        }

        assertTrue(AwStartup.isStarted)
        assertEquals(listOf("DslInit"), executed)
    }

    @Test
    fun `init DSL fires result callback`() {
        val results = mutableListOf<InitResult>()

        AwStartup.init(context) {
            add(fakeInitializer("X", InitPriority.IMMEDIATELY))
            onResult { results.add(it) }
        }

        assertEquals(1, results.size)
        assertEquals("X", results[0].name)
        assertTrue(results[0].success)
    }

    @Test
    fun `failing initializer does not block subsequent ones`() {
        val order = mutableListOf<String>()

        AwStartup.register(object : AppInitializer() {
            override val name = "Fail"
            override val priority = InitPriority.NORMAL
            override fun onCreate(context: Context) { throw RuntimeException("boom") }
        })
        AwStartup.register(object : AppInitializer() {
            override val name = "After"
            override val priority = InitPriority.NORMAL
            override fun onCreate(context: Context) { order.add("After") }
        })

        AwStartup.start(context)

        assertTrue(order.contains("After"))
        val report = AwStartup.getReport()
        assertFalse(report.first { it.name == "Fail" }.success)
        assertTrue(report.first { it.name == "After" }.success)
    }

    @Test
    fun `start ignores duplicate calls`() {
        var count = 0
        AwStartup.register(object : AppInitializer() {
            override val name = "Counter"
            override val priority = InitPriority.NORMAL
            override fun onCreate(context: Context) { count++ }
        })

        AwStartup.start(context)
        try {
            AwStartup.start(context)
        } catch (_: IllegalStateException) {}

        assertEquals(1, count)
    }

    @Test
    fun `register after start throws`() {
        AwStartup.register(fakeInitializer("A", InitPriority.NORMAL))
        AwStartup.start(context)

        try {
            AwStartup.register(fakeInitializer("B", InitPriority.NORMAL))
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("已启动"))
        }
    }

    @Test
    fun `dependency ordering within same priority`() {
        val order = mutableListOf<String>()

        AwStartup.register(object : AppInitializer() {
            override val name = "B"
            override val priority = InitPriority.NORMAL
            override val dependencies = listOf("A")
            override fun onCreate(context: Context) { order.add("B") }
        })
        AwStartup.register(object : AppInitializer() {
            override val name = "A"
            override val priority = InitPriority.NORMAL
            override fun onCreate(context: Context) { order.add("A") }
        })

        AwStartup.start(context)

        assertEquals(listOf("A", "B"), order)
    }

    @Test
    fun `circular dependency throws IllegalStateException`() {
        AwStartup.register(object : AppInitializer() {
            override val name = "X"
            override val priority = InitPriority.NORMAL
            override val dependencies = listOf("Y")
            override fun onCreate(context: Context) {}
        })
        AwStartup.register(object : AppInitializer() {
            override val name = "Y"
            override val priority = InitPriority.NORMAL
            override val dependencies = listOf("X")
            override fun onCreate(context: Context) {}
        })

        try {
            AwStartup.start(context)
            fail("Expected IllegalStateException for circular dependency")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("循环依赖"))
        }
    }

    @Test
    fun `reset clears all state`() {
        AwStartup.register(fakeInitializer("A", InitPriority.NORMAL))
        AwStartup.start(context)
        assertTrue(AwStartup.isStarted)

        AwStartup.reset()

        assertFalse(AwStartup.isStarted)
        assertTrue(AwStartup.getReport().isEmpty())
        assertEquals(0L, AwStartup.getSyncCostMillis())
    }

    @Test
    fun `reset rebuilds background executor for subsequent start`() {
        val firstRun = CountDownLatch(1)
        val secondRun = CountDownLatch(1)

        AwStartup.register(object : AppInitializer() {
            override val name = "Bg1"
            override val priority = InitPriority.BACKGROUND
            override fun onCreate(context: Context) {
                firstRun.countDown()
            }
        })

        AwStartup.start(context)
        assertTrue(firstRun.await(3, TimeUnit.SECONDS))

        AwStartup.reset()

        AwStartup.register(object : AppInitializer() {
            override val name = "Bg2"
            override val priority = InitPriority.BACKGROUND
            override fun onCreate(context: Context) {
                secondRun.countDown()
            }
        })

        AwStartup.start(context)
        assertTrue(secondRun.await(3, TimeUnit.SECONDS))
    }

    @Test
    fun `await returns true after background tasks complete`() {
        val latch = CountDownLatch(1)
        AwStartup.register(object : AppInitializer() {
            override val name = "BgAwait"
            override val priority = InitPriority.BACKGROUND
            override fun onCreate(context: Context) {
                Thread.sleep(100)
                latch.countDown()
            }
        })

        AwStartup.start(context)
        assertTrue(AwStartup.await(3000))
        assertTrue(latch.await(3, TimeUnit.SECONDS))
    }

    @Test
    fun `background initializer with dependency waits for dependency`() {
        val order = mutableListOf<String>()
        val latch = CountDownLatch(2)

        AwStartup.register(object : AppInitializer() {
            override val name = "BgDep"
            override val priority = InitPriority.BACKGROUND
            override fun onCreate(context: Context) {
                Thread.sleep(100)
                synchronized(order) { order.add("BgDep") }
                latch.countDown()
            }
        })
        AwStartup.register(object : AppInitializer() {
            override val name = "BgAfter"
            override val priority = InitPriority.BACKGROUND
            override val dependencies = listOf("BgDep")
            override fun onCreate(context: Context) {
                synchronized(order) { order.add("BgAfter") }
                latch.countDown()
            }
        })

        AwStartup.start(context)
        assertTrue(latch.await(3, TimeUnit.SECONDS))

        synchronized(order) {
            assertTrue(order.indexOf("BgDep") < order.indexOf("BgAfter"))
        }
    }

    @Test
    fun `init DSL with custom threadCount`() {
        val latch = CountDownLatch(4)
        AwStartup.init(context) {
            backgroundThreads(4)
            add(object : AppInitializer() {
                override val name = "T1"
                override val priority = InitPriority.BACKGROUND
                override fun onCreate(context: Context) { latch.countDown() }
            })
            add(object : AppInitializer() {
                override val name = "T2"
                override val priority = InitPriority.BACKGROUND
                override fun onCreate(context: Context) { latch.countDown() }
            })
            add(object : AppInitializer() {
                override val name = "T3"
                override val priority = InitPriority.BACKGROUND
                override fun onCreate(context: Context) { latch.countDown() }
            })
            add(object : AppInitializer() {
                override val name = "T4"
                override val priority = InitPriority.BACKGROUND
                override fun onCreate(context: Context) { latch.countDown() }
            })
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS))
    }

    @Test
    fun `onFailed callback is called on failure`() {
        val failedNames = mutableListOf<String>()
        val error = RuntimeException("test error")

        AwStartup.register(object : AppInitializer() {
            override val name = "Failing"
            override val priority = InitPriority.NORMAL
            override fun onCreate(context: Context) { throw error }
            override fun onFailed(err: Throwable) { failedNames.add(name) }
        })

        AwStartup.start(context)

        assertEquals(listOf("Failing"), failedNames)
    }

    @Test
    fun `DSL shortcut methods register correct priorities`() {
        AwStartup.init(context) {
            immediately("Imm") {}
            normal("Norm") {}
            deferred("Def") {}
            background("Bg") {}
        }

        assertTrue(AwStartup.isStarted)
        val report = AwStartup.getReport()
        val immResult = report.first { it.name == "Imm" }
        val normResult = report.first { it.name == "Norm" }
        assertEquals(InitPriority.IMMEDIATELY, immResult.priority)
        assertEquals(InitPriority.NORMAL, normResult.priority)
    }

    private fun fakeInitializer(n: String, p: InitPriority) = object : AppInitializer() {
        override val name = n
        override val priority = p
        override fun onCreate(context: Context) {}
    }
}
