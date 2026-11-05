package com.ps2gamepad.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Thread-safe ring buffer of timestamped log lines, newest last. Also mirrored to logcat by the caller. */
class DiagnosticLog(private val capacity: Int = 300, private val now: () -> Long = System::currentTimeMillis) {
    private val format = ThreadLocal.withInitial { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    fun add(message: String) {
        val line = "${format.get()!!.format(Date(now()))}  $message"
        _lines.update { (it + line).takeLast(capacity) }
    }

    fun clear() { _lines.value = emptyList() }
}
