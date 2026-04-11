# aw-startup

App startup initialization library. Provides priority-based, dependency-aware component initialization with topology sort.

## Installation

Add the dependency in your module-level `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.answufeng:aw-startup:1.0.0")
}
```

Make sure you have the JitPack repository in your root `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

## Features

- 4 priority levels: IMMEDIATELY, NORMAL, DEFERRED, BACKGROUND
- Topological sort with circular dependency detection
- Background thread pool with CountDownLatch for dependency ordering
- IdleHandler for DEFERRED priority
- Error isolation: single initializer failure doesn't block others
- DSL configuration with result callback
- Initialization report with timing

## Usage

```kotlin
// Define initializers
class LoggerInit : AppInitializer {
    override val name = "Logger"
    override val priority = InitPriority.IMMEDIATELY
    override fun onCreate(context: Context) { /* init logger */ }
}

class NetworkInit : AppInitializer {
    override val name = "Network"
    override val priority = InitPriority.NORMAL
    override val dependencies = listOf("Logger")
    override fun onCreate(context: Context) { /* init network */ }
}

// Initialize
BrickStartup.init(this) {
    add(LoggerInit())
    add(NetworkInit())
    add(AnalyticsInit())   // DEFERRED
    add(CacheInit())       // BACKGROUND
    onResult { result ->
        Log.d("Startup", "${result.name} ${result.costMillis}ms")
    }
}

// Get report
val report = BrickStartup.getReport()
val syncCost = BrickStartup.getSyncCostMillis()
```

## License

Apache License 2.0. See [LICENSE](LICENSE) for details.
