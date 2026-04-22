package com.answufeng.startup.internal

import com.answufeng.startup.InitPriority
import com.answufeng.startup.StartupInitializer
import java.util.LinkedHashMap
import java.util.TreeSet

data class PriorityGroup(
    val priority: InitPriority,
    val initializers: List<StartupInitializer>
)

/**
 * 初始化器依赖图，负责拓扑排序和校验。
 *
 * 内部使用 Kahn 算法（BFS）进行全局拓扑排序，时间复杂度 O(V+E)。
 * 循环依赖检测使用 DFS + 着色法。
 *
 * @param initializers 待排序的初始化器列表
 */
class Graph(
    private val initializers: List<StartupInitializer>
) {
    private val nameMap: Map<String, StartupInitializer> by lazy {
        initializers.associateBy { it.name }
    }

    private val sortedGroups: List<PriorityGroup> by lazy {
        val allSorted = topologicalSortAll()
        allSorted
            .groupBy { it.priority }
            .entries
            .sortedBy { it.key.ordinal }
            .map { (priority, inits) -> PriorityGroup(priority, inits) }
    }

    /**
     * 校验依赖图的合法性。
     *
     * 依次检查：名称唯一性、依赖存在性、优先级一致性、无循环依赖。
     *
     * @throws IllegalArgumentException 名称重复
     * @throws IllegalStateException 依赖不存在 / 优先级不一致 / 循环依赖
     */
    fun validate() {
        validateUniqueNames()
        validateDependenciesExist()
        validatePriorityConsistency()
        validateNoCircularDependencies()
    }

    fun getGroups(): List<PriorityGroup> = sortedGroups

    /**
     * 获取指定优先级的拓扑排序结果。
     *
     * 结果按依赖顺序排列，被依赖者在前。
     */
    fun getSorted(priority: InitPriority): List<StartupInitializer> =
        sortedGroups.find { it.priority == priority }?.initializers ?: emptyList()

    private fun validateUniqueNames() {
        val seen = mutableSetOf<String>()
        for (init in initializers) {
            if (!seen.add(init.name)) {
                throw IllegalArgumentException("初始化器名称重复：${init.name}")
            }
        }
    }

    private fun validateDependenciesExist() {
        for (init in initializers) {
            for (dep in init.dependencies) {
                if (dep !in nameMap) {
                    throw IllegalStateException(
                        "初始化器 ${init.name} 依赖了不存在的初始化器：$dep"
                    )
                }
            }
        }
    }

    private fun validatePriorityConsistency() {
        for (init in initializers) {
            for (dep in init.dependencies) {
                val depInit = nameMap[dep]!!
                if (depInit.priority > init.priority) {
                    throw IllegalStateException(
                        "初始化器 ${init.name}（${init.priority}）依赖了更低优先级的 ${depInit.name}（${depInit.priority}）"
                    )
                }
            }
        }
    }

    private fun validateNoCircularDependencies() {
        val visited = mutableSetOf<String>()
        val inStack = mutableSetOf<String>()

        fun dfs(name: String): Boolean {
            visited.add(name)
            inStack.add(name)
            val init = nameMap[name] ?: return true
            for (dep in init.dependencies) {
                if (dep in inStack) return false
                if (dep !in visited && !dfs(dep)) return false
            }
            inStack.remove(name)
            return true
        }

        for (init in initializers) {
            if (init.name !in visited) {
                if (!dfs(init.name)) {
                    throw IllegalStateException("检测到循环依赖，涉及初始化器：${init.name}")
                }
            }
        }
    }

    private fun topologicalSortAll(): List<StartupInitializer> {
        if (initializers.size <= 1) return initializers

        val nameToIndex: Map<String, Int> =
            initializers.mapIndexed { index, init -> init.name to index }.toMap()
        val cmp = compareBy<String> { nameToIndex[it]!! }

        val inDegree = LinkedHashMap<String, Int>()
        val adj = LinkedHashMap<String, MutableList<String>>()

        for (init in initializers) {
            inDegree[init.name] = 0
            adj[init.name] = mutableListOf()
        }

        for (init in initializers) {
            for (dep in init.dependencies) {
                if (dep in nameMap) {
                    adj[dep]!!.add(init.name)
                    inDegree[init.name] = inDegree[init.name]!! + 1
                }
            }
        }

        val ready = TreeSet(cmp)
        for ((name, degree) in inDegree) {
            if (degree == 0) ready.add(name)
        }

        val result = mutableListOf<StartupInitializer>()
        while (ready.isNotEmpty()) {
            val current = ready.pollFirst()
            result.add(nameMap[current]!!)
            for (neighbor in adj[current]!!) {
                inDegree[neighbor] = inDegree[neighbor]!! - 1
                if (inDegree[neighbor] == 0) {
                    ready.add(neighbor)
                }
            }
        }

        return result
    }
}
