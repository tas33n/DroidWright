/**
 * Copyright (c) 2025 tas33n
 *
 * Licensed under the MIT License
 * See LICENSE file or https://opensource.org/licenses/MIT
 *
 * @author tas33n
 * @see <a href="https://github.com/tas33n/droidwright">GitHub</a>
 */
package com.tas33n.droidwright.domain

import android.os.Build
import android.util.Log
import com.tas33n.droidwright.data.models.LogEntry
import com.tas33n.droidwright.data.models.LogLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.InterruptedIOException
import java.util.regex.Pattern

object LogcatReader {
    private const val TAG = "LogcatReader"
    private val _logcatLogs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logcatLogs: StateFlow<List<LogEntry>> = _logcatLogs.asStateFlow()
    
    private var process: Process? = null
    private var readingJob: Job? = null
    private val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO)
    
    private val logPattern = Pattern.compile(
        "^(\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+(\\d+)\\s+(\\d+)\\s+([VDIWEF])\\s+([^:]+):\\s+(.*)$"
    )
    
    suspend fun startReading(packageName: String = "com.tas33n.droidwright") {
        // Stop any existing reading
        stopReading()
        
        readingJob = scope.launch {
            try {
                coroutineScope {
                    // Clear existing logs first
                    clearLogcat()
                    
                    // Start logcat process filtering by package name
                    process = Runtime.getRuntime().exec(
                        arrayOf(
                            "logcat",
                            "-v", "time",
                            "-s", "${packageName}:*", "AndroidRuntime:*", "System.err:*"
                        )
                    )
                    
                    val inputStream = process?.inputStream ?: return@coroutineScope
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val buffer = mutableListOf<LogEntry>()
                    var lastUpdateTime = System.currentTimeMillis()
                    val UPDATE_INTERVAL_MS = 500L // Update every 500ms max
                    val MAX_BUFFER_SIZE = 50 // Max logs before forced update
                    
                    try {
                        while (isActive && isProcessAlive(process)) {
                            try {
                                // Check if data is available (non-blocking check)
                                val available = inputStream.available()
                                
                                if (available > 0) {
                                    // Data available, read line (this may still block briefly)
                                    val line = reader.readLine()
                                    if (line != null && line.isNotBlank()) {
                                        parseLogLine(line)?.let { logEntry ->
                                            buffer.add(logEntry)
                                            
                                            // Update if buffer is full or enough time has passed
                                            val now = System.currentTimeMillis()
                                            if (buffer.size >= MAX_BUFFER_SIZE || 
                                                (now - lastUpdateTime) >= UPDATE_INTERVAL_MS) {
                                                _logcatLogs.value = _logcatLogs.value + buffer
                                                buffer.clear()
                                                lastUpdateTime = now
                                            }
                                        }
                                    }
                                } else {
                                    // No data available, yield to other coroutines
                                    // This prevents busy-waiting and allows cancellation
                                    delay(200)
                                }
                            } catch (e: InterruptedIOException) {
                                // Thread was interrupted, check if we should continue
                                if (!isActive) break
                                Log.d(TAG, "Read interrupted, continuing...")
                                delay(200)
                            } catch (e: CancellationException) {
                                // Coroutine was cancelled, exit gracefully
                                break
                            } catch (e: Exception) {
                                if (isActive) {
                                    Log.e(TAG, "Error reading line: ${e.message}", e)
                                    delay(500) // Wait before retrying
                                } else {
                                    break
                                }
                            }
                        }
                        
                        // Add remaining logs
                        if (buffer.isNotEmpty() && isActive) {
                            _logcatLogs.value = _logcatLogs.value + buffer
                        }
                    } catch (e: CancellationException) {
                        Log.d(TAG, "Logcat reading cancelled")
                        throw e
                    } catch (e: Exception) {
                        if (isActive) {
                            Log.e(TAG, "Error in logcat reading loop", e)
                        }
                    } finally {
                        // Clean up
                        try {
                            reader.close()
                        } catch (e: Exception) {
                            // Ignore
                        }
                    }
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Logcat reading job cancelled")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error starting logcat reading", e)
            }
        }
    }
    
    fun stopReading() {
        readingJob?.cancel()
        readingJob = null
        
        process?.let { proc ->
            try {
                proc.destroyForcibly()
            } catch (e: Exception) {
                Log.e(TAG, "Error destroying process", e)
            }
        }
        process = null
    }
    
    fun clearLogs() {
        _logcatLogs.value = emptyList()
    }
    
    private suspend fun clearLogcat() {
        withContext(Dispatchers.IO) {
            try {
                val clearProcess = Runtime.getRuntime().exec("logcat -c")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val finished = clearProcess.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)
                    if (!finished && isProcessAlive(clearProcess)) {
                        clearProcess.destroyForcibly()
                    }
                } else {
                    clearProcess.waitFor()
                }
                Unit
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing logcat", e)
            }
        }
    }
    
    private fun parseLogLine(line: String): LogEntry? {
        try {
            val matcher = logPattern.matcher(line)
            if (matcher.matches()) {
                val timestamp = System.currentTimeMillis() // Use current time as fallback
                val level = when (matcher.group(4)) {
                    "V" -> LogLevel.DEBUG
                    "D" -> LogLevel.DEBUG
                    "I" -> LogLevel.INFO
                    "W" -> LogLevel.WARNING
                    "E", "F" -> LogLevel.ERROR
                    else -> LogLevel.INFO
                }
                val tag = matcher.group(5) ?: "Unknown"
                val message = matcher.group(6) ?: line
                
                return LogEntry(timestamp, "[$tag] $message", level)
            } else {
                // Fallback for lines that don't match pattern
                return LogEntry(
                    System.currentTimeMillis(),
                    line,
                    LogLevel.INFO
                )
            }
        } catch (e: Exception) {
            return LogEntry(
                System.currentTimeMillis(),
                line,
                LogLevel.INFO
            )
        }
    }
    
    fun getAllLogs(): List<LogEntry> {
        return _logcatLogs.value
    }

    private fun isProcessAlive(p: Process?): Boolean {
        if (p == null) return false
        return try {
            p.exitValue()
            false
        } catch (e: IllegalThreadStateException) {
            true
        }
    }
}

