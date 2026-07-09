package com.spartan.diagnostics

import com.spartan.BuildConfig
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Debug-only, in-memory ring buffer of operational events (sync outcomes, worker runs). This is
 * Spartan's "observability without analytics": it never records PHI (no metric values, no plan
 * text, no tokens), never persists to disk, and is a no-op in release builds. Viewable from the
 * debug-only Diagnostics screen in Settings.
 */
object DebugLog {
    private const val CAPACITY = 200
    private val buffer = ArrayDeque<String>(CAPACITY)
    private val formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")

    @Synchronized
    fun log(tag: String, message: String) {
        if (!BuildConfig.DEBUG) return
        if (buffer.size >= CAPACITY) buffer.removeFirst()
        val ts = Instant.now().atZone(ZoneId.systemDefault()).format(formatter)
        buffer.addLast("$ts [$tag] $message")
    }

    @Synchronized
    fun entries(): List<String> = buffer.toList().asReversed()
}
