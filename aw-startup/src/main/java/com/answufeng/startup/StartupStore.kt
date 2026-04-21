package com.answufeng.startup

import java.util.concurrent.ConcurrentHashMap

/**
 * 初始化器间数据共享存储。
 *
 * 允许初始化器在 [StartupInitializer.onCreate] 中存储产物，
 * 后续初始化器通过 [AwStartup.getStore] 获取。
 *
 * 使用方式：
 * ```kotlin
 * class DatabaseInit : StartupInitializer() {
 *     override val name = "Database"
 *     override val priority = InitPriority.NORMAL
 *     override fun onCreate(context: Context) {
 *         val db = Room.databaseBuilder(...).build()
 *         AwStartup.getStore().put("database", db)
 *     }
 * }
 *
 * class RepositoryInit : StartupInitializer() {
 *     override val name = "Repository"
 *     override val priority = InitPriority.NORMAL
 *     override val dependencies = listOf("Database")
 *     override fun onCreate(context: Context) {
 *         val db = AwStartup.getStore().get<RoomDatabase>("database")
 *         repository = Repository(db)
 *     }
 * }
 * ```
 */
class StartupStore internal constructor() {

    private val store = ConcurrentHashMap<String, Any>()

    /**
     * 存储数据。
     *
     * @param key 数据键
     * @param value 数据值
     */
    fun put(key: String, value: Any) {
        store[key] = value
    }

    /**
     * 获取数据。
     *
     * @param key 数据键
     * @return 数据值，不存在时返回 null
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String): T? {
        return store[key] as? T
    }

    /**
     * 获取数据，不存在时抛出异常。
     *
     * @param key 数据键
     * @return 数据值
     * @throws IllegalStateException 数据不存在时
     */
    fun <T> getOrThrow(key: String): T {
        return get<T>(key) ?: throw IllegalStateException("Store 中不存在 key: $key")
    }

    /**
     * 是否包含指定键。
     */
    fun contains(key: String): Boolean = store.containsKey(key)

    /**
     * 移除指定键的数据。
     */
    fun remove(key: String) {
        store.remove(key)
    }

    internal fun clear() {
        store.clear()
    }
}
