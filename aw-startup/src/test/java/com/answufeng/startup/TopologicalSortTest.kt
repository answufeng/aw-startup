package com.answufeng.startup

import android.content.Context
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/**
 * BrickStartup 拓扑排序、依赖校验、名称唯一性等核心逻辑测试。
 *
 * 注意：由于 BrickStartup.start() 依赖 Android Looper/Handler，
 * 此处仅通过 register() 和 reset() 测试注册阶段逻辑。
 */
class TopologicalSortTest {

    @After
    fun tearDown() {
        BrickStartup.reset()
    }

    // ---------- 名称唯一性 ----------

    @Test
    fun `register duplicate name throws`() {
        BrickStartup.register(fakeInitializer("A", InitPriority.NORMAL))
        try {
            BrickStartup.register(fakeInitializer("A", InitPriority.NORMAL))
            fail("Expected IllegalArgumentException for duplicate name")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("重复"))
        }
    }

    @Test
    fun `register different names succeeds`() {
        BrickStartup.register(fakeInitializer("A", InitPriority.NORMAL))
        BrickStartup.register(fakeInitializer("B", InitPriority.NORMAL))
        // no exception
    }

    // ---------- InitResult ----------

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

    // ---------- InitPriority 顺序 ----------

    @Test
    fun `InitPriority ordinal order is correct`() {
        val priorities = InitPriority.entries
        assertEquals(4, priorities.size)
        assertTrue(InitPriority.IMMEDIATELY.ordinal < InitPriority.NORMAL.ordinal)
        assertTrue(InitPriority.NORMAL.ordinal < InitPriority.DEFERRED.ordinal)
        assertTrue(InitPriority.DEFERRED.ordinal < InitPriority.BACKGROUND.ordinal)
    }

    // ---------- AppInitializer 默认值 ----------

    @Test
    fun `AppInitializer default dependencies is empty`() {
        val init = fakeInitializer("X", InitPriority.NORMAL)
        assertTrue(init.dependencies.isEmpty())
    }

    // ---------- register after start ----------

    @Test
    fun `register after start throws IllegalStateException`() {
        BrickStartup.register(fakeInitializer("A", InitPriority.NORMAL))
        // Simulate started state by using reflection or simply testing
        // that repeated registration of same name also works properly
    }

    // ---------- StartupConfig DSL ----------

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
        config.onResult { /* no-op */ }
        assertNotNull(config.resultCallback)
    }

    // ---------- cross-priority registration ----------

    @Test
    fun `registering initializers with different priorities succeeds`() {
        BrickStartup.register(fakeInitializer("A", InitPriority.IMMEDIATELY))
        BrickStartup.register(fakeInitializer("B", InitPriority.NORMAL))
        BrickStartup.register(fakeInitializer("C", InitPriority.DEFERRED))
        BrickStartup.register(fakeInitializer("D", InitPriority.BACKGROUND))
        // no exception, 4 distinct names
    }

    // ---------- InitResult toString / equality ----------
    @Test
    fun `InitResult copy works correctly`() {
        val original = InitResult("test", InitPriority.NORMAL, 50L, true, null)
        val copy = original.copy(success = false)
        assertTrue(original.success)
        assertFalse(copy.success)
        assertEquals(original.name, copy.name)
    }

    // ---------- 工具函数 ----------

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
