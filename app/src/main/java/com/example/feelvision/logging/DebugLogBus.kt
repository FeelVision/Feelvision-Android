package com.feelvision.logging

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class DebugLogType {
    INFO, OK, WARN, ERROR,
    COMMAND, BUTTON, MODE,
    PROMPT_IN, PROMPT_OUT,
    TTS, HARDWARE, INFERENCE
}

data class DebugLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val type: DebugLogType,
    val tag: String,
    val message: String,
    val requestId: String? = null
) {
    val timeLabel: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(timestamp))
}

@Singleton
class DebugLogBus @Inject constructor() {

    private val _logs = MutableSharedFlow<DebugLogEntry>(
        replay = 200,
        extraBufferCapacity = 300,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val logs: SharedFlow<DebugLogEntry> = _logs.asSharedFlow()

    fun log(
        type: DebugLogType,
        tag: String,
        message: String,
        requestId: String? = null
    ) {
        val entry = DebugLogEntry(
            type = type,
            tag = tag,
            message = message.take(2000),
            requestId = requestId
        )
        
        // Mirror to system Logcat for viewing on laptop
        val msg = if (requestId != null) "[$requestId] $message" else message
        when (type) {
            DebugLogType.ERROR -> android.util.Log.e(tag, msg)
            DebugLogType.WARN  -> android.util.Log.w(tag, msg)
            else               -> android.util.Log.d(tag, msg)
        }

        _logs.tryEmit(entry)
    }
}