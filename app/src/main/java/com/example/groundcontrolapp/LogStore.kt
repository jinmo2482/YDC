package com.example.groundcontrolapp

object LogStore {
    private const val MAX_LOGS = 200
    private val logs: ArrayDeque<String> = ArrayDeque()

    @Synchronized
    fun add(line: String) {
        logs.addLast(line)
        while (logs.size > MAX_LOGS) {
            logs.removeFirst()
        }
    }

    @Synchronized
    fun latest(limit: Int): List<String> {
        if (logs.isEmpty()) return emptyList()
        return logs.takeLast(limit)
    }

    @Synchronized
    fun all(): List<String> = logs.toList()

    @Synchronized
    fun clear() {
        logs.clear()
    }
}
