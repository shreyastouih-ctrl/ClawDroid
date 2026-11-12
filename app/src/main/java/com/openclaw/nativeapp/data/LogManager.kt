package com.openclaw.nativeapp.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

object LogManager {
    enum class Level {
        INFO, WARN, ERROR, AI, ACTION
    }

    data class LogEntry(
        val timestamp: Long = System.currentTimeMillis(),
        val tag: String,
        val message: String,
        val level: Level
    ) {
        fun formatted(): String {
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
            val prefix = when (level) {
                Level.INFO -> "[INFO]"
                Level.WARN -> "[WARN]"
                Level.ERROR -> "[ERROR]"
                Level.AI -> "[AI 🧠]"
                Level.ACTION -> "[ACTION ⚡]"
            }
            return "$time $prefix [$tag] $message"
        }
    }

    private val logHistory = CopyOnWriteArrayList<LogEntry>()
    private const val MAX_LOGS = 300

    private val _logsFlow = MutableSharedFlow<LogEntry>(replay = 50, extraBufferCapacity = 100)
    val logsFlow: SharedFlow<LogEntry> = _logsFlow.asSharedFlow()

    fun log(tag: String, message: String, level: Level = Level.INFO) {
        val entry = LogEntry(tag = tag, message = message, level = level)
        if (logHistory.size >= MAX_LOGS) {
            logHistory.removeAt(0)
        }
        logHistory.add(entry)
        _logsFlow.tryEmit(entry)
        android.util.Log.d("OpenClaw", "${entry.level}: [${entry.tag}] $message")
    }

    fun i(tag: String, message: String) = log(tag, message, Level.INFO)
    fun w(tag: String, message: String) = log(tag, message, Level.WARN)
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val msg = if (throwable != null) "$message\n${throwable.stackTraceToString()}" else message
        log(tag, msg, Level.ERROR)
    }
    fun ai(tag: String, message: String) = log(tag, message, Level.AI)
    fun action(tag: String, message: String) = log(tag, message, Level.ACTION)

    fun getAllLogs(): List<LogEntry> = logHistory.toList()

    fun clear() {
        logHistory.clear()
    }
}
