package com.closecode.ui.ide

/**
 * Forensics for one question: when Back is pressed, who consumes it.
 *
 * Only the winning handler's `onBack` runs, so each of this app's handlers logs its own name
 * when it fires, together with the state that armed it. The screen stack is recorded from the
 * navigation host beside it. If a press produces no line here at all, no handler ran — which is
 * itself the answer. Always cheap (one logcat line per press); mirrored into the app's own
 * Logcat panel while Frame stats is on, so the phone produces the log with no adb.
 */
object BackLog {
    private const val TAG = "ClosecodeBack"
    private const val MAX_EVENTS = 80
    private val stack = ArrayDeque<String>(8)
    private val events = ArrayDeque<String>(MAX_EVENTS)

    /** Set while Frame stats is on: each entry is also appended to the Logcat panel. */
    var mirror: ((String) -> Unit)? = null

    @Synchronized
    fun routeChanged(route: String?) {
        if (route == null) return
        if (stack.lastOrNull() == route) return
        // Back out of a route returns to the one below it; forward goes on top.
        if (stack.size >= 2 && stack[stack.size - 2] == route) {
            stack.removeLast()
        } else {
            stack.addLast(route)
            while (stack.size > 8) stack.removeFirst()
        }
    }

    @Synchronized
    fun press(consumedBy: String, detail: String) {
        val line = "back stack=[${stack.joinToString(" > ")}] consumed=$consumedBy $detail"
        if (events.size >= MAX_EVENTS) events.removeFirst()
        events.addLast(line)
        try {
            android.util.Log.d(TAG, line)
        } catch (_: Throwable) {
            // Logging must never break the press it observes.
        }
        mirror?.invoke(line)
    }

    @Synchronized
    fun snapshot(): List<String> = events.toList()
}
