package com.answufeng.startup

import android.content.Context
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class TopologicalSortTest {

    @After
    fun tearDown() {
        AwStartup.reset()
    }

    @Test
    fun `register duplicate name throws`() {
        AwStartup.register(fakeInitializer("A", InitPriority.NORMAL))
        try {
            AwStartup.register(fakeInitializer("A", InitPriority.NORMAL))
            fail("Expected IllegalArgumentException for duplicate name")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("重复"))
        }
    }

    @Test
    fun `register different names succeeds`() {
        AwStartup.register(fakeInitializer("A", InitPriority.NORMAL))
        AwStartup.register(fakeInitializer("B", InitPriority.NORMAL))
    }

    @Test
    fun `InitResult data class works correctly`() {
        val result = InitResult(
            name = "test",
            priority = InitPriority.IMMEDIATELY,
            costMillis = 42L,
            success = true,
            error = null
        )
        assertEquals("test", result.name)
        assertEquals(InitPriority.IMMEDIATELY, result.priority)
        assertEquals(42L, result.costMillis)
        assertTrue(result.success)
        assertNull(result.error)
    }

    @Test
    fun `InitResult failure captures error`() {
        val ex = RuntimeException("boom")
        val result = InitResult("fail", InitPriority.NORMAL, 10L, false, ex)
        assertFalse(result.success)
        assertSame(ex, result.error)
    }

    @Test
    fun `InitPriority ordinal order is correct`() {
        val priorities = InitPriority.entries
        assertEquals(4, priorities.size)
        assertTrue(InitPriority.IMMEDIATELY.ordinal < InitPriority.NORMAL.ordinal)
        assertTrue(InitPriority.NORMAL.ordinal < InitPriority.DEFERRED.ordinal)
        assertTrue(InitPriority.DEFERRED.ordinal < InitPriority.BACKGROUND.ordinal)
    }

    @Test
    fun `AppInitializer default dependencies is empty`() {
        val init = fakeInitializer("X", InitPriority.NORMAL)
        assertTrue(init.dependencies.isEmpty())
    }

    @Test
    fun `StartupConfig add collects initializers`() {
        val config = StartupConfig()
        config.add(fakeInitializer("A", InitPriority.NORMAL))
        config.add(fakeInitializer("B", InitPriority.DEFERRED))
        assertEquals(2, config.initializers.size)
        assertEquals("A", config.initializers[0].name)
        assertEquals("B", config.initializers[1].name)
    }

    @Test
    fun `StartupConfig onResult stores callback`() {
        val config = StartupConfig()
        assertNull(config.resultCallback)
        config.onResult { }
        assertNotNull(config.resultCallback)
    }

    @Test
    fun `StartupConfig backgroundThreads validates positive`() {
        val config = StartupConfig()
        try {
            config.backgroundThreads(0)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("大于 0"))
        }
    }

    @Test
    fun `registering initializers with different priorities succeeds`() {
        AwStartup.register(fakeInitializer("A", InitPriority.IMMEDIATELY))
        AwStartup.register(fakeInitializer("B", InitPriority.NORMAL))
        AwStartup.register(fakeInitializer("C", InitPriority.DEFERRED))
        AwStartup.register(fakeInitializer("D", InitPriority.BACKGROUND))
    }

    @Test
    fun `InitResult copy works correctly`() {
        val original = InitResult("test", InitPriority.NORMAL, 50L, true, null)
        val copy = original.copy(success = false)
        assertTrue(original.success)
        assertFalse(copy.success)
        assertEquals(original.name, copy.name)
    }

    private fun fakeInitializer(
        initName: String,
        initPriority: InitPriority,
        deps: List<String> = emptyList()
    ): AppInitializer = object : AppInitializer {
        override val name = initName
        override val priority = initPriority
        override val dependencies = deps
        override fun onCreate(context: Context) {}
    }
}
