package com.closecode.ui.ide

import java.util.ArrayDeque

/**
 * Every programmatic editor-scroll decision, with its reason and origin, in one place.
 *
 * Finger drags and flings move the scroll states directly and never come through here — only
 * code that moves the view on the reader's behalf records. An entry without a matching program
 * call, seen in the observer as `external`, is a scroll nobody asked for (the text field's own
 * bring-into-view is the usual suspect), and its timestamp says which finished job it followed.
 *
 * Always cheap: a bounded ring in memory plus one logcat line per entry. Mirroring into the
 * app's own Logcat panel is opt-in per session ([mirror]), so ordinary scrolling never spams
 * the panel the reader uses for builds.
 */
object ScrollLog {
    /** One recorded move of an editor scroll state. */
    data class ScrollEvent(
        val atMs: Long,
        val axis: String,
        val from: Int,
        val to: Int,
        val reason: String,
        val origin: String,
    )

    private const val MAX_EVENTS = 80
    private const val TAG = "ClosecodeScroll"
    private val events = ArrayDeque<ScrollEvent>(MAX_EVENTS)

    /** Set while Frame stats is on: each entry is also appended to the Logcat panel. */
    var mirror: ((String) -> Unit)? = null

    private fun origin(): String = Thread.currentThread().stackTrace
        .asSequence()
        .map { it.className.substringAfterLast('.') + "." + it.methodName + ":" + it.lineNumber }
        .filterNot { it.startsWith("Thread.") || it.startsWith("ScrollLog.") }
        .filter { it.contains("closecode", ignoreCase = true) || it.startsWith("androidx.compose.foundation") }
        .take(6)
        .joinToString(" <- ")

    @Synchronized
    fun record(axis: String, from: Int, to: Int, reason: String) {
        val event = ScrollEvent(
            atMs = System.currentTimeMillis(),
            axis = axis,
            from = from,
            to = to,
            reason = reason,
            origin = origin(),
        )
        if (events.size >= MAX_EVENTS) events.removeFirst()
        events.addLast(event)
        val line = "scroll $axis $from->$to $reason\n${event.origin}"
        try {
            android.util.Log.d(TAG, line)
        } catch (_: Throwable) {
            // Logging must never break the editor it observes.
        }
        mirror?.invoke(line)
    }

    @Synchronized
    fun snapshot(): List<ScrollEvent> = events.toList()

    @Synchronized
    fun clear() {
        events.clear()
    }
}
