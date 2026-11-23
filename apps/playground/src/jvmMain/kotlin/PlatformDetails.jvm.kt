package playground

actual fun platformDetails(): List<Pair<String, String>> = listOf(
    "JVM" to "${System.getProperty("java.vm.name")} ${System.getProperty("java.version")}",
    "OS" to "${System.getProperty("os.name")} ${System.getProperty("os.arch")}",
    "Heap max" to "${Runtime.getRuntime().maxMemory() / (1024 * 1024)} MB",
    "CPUs" to Runtime.getRuntime().availableProcessors().toString(),
)
