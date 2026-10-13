package com.closecode

import java.io.File

/**
 * The last thing a crash leaves behind: its stack trace, written to a file before the process
 * dies, so the next launch can show it instead of swallowing it. There is no logcat on a phone,
 * so without this a crash report is "it closed" and nothing else.
 */
internal object CrashReport {
    private const val FILE_NAME = "last_crash.txt"
    private const val MAX_CHARS = 8000

    /**
     * Records the uncaught exception and then lets the app die exactly as it would have: the
     * previous handler still runs, so crash behaviour never changes, only its memory does.
     */
    fun install(filesDir: File) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                File(filesDir, FILE_NAME).writeText(
                    throwable.stackTraceToString().take(MAX_CHARS),
                )
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** The trace left by the previous run, or null when it exited cleanly. */
    fun readLast(filesDir: File): String? {
        val file = File(filesDir, FILE_NAME)
        if (!file.isFile) return null
        return file.readText().takeIf { it.isNotBlank() }
    }

    /** Forgets the recorded crash, so it is shown once and never again. */
    fun clear(filesDir: File) {
        runCatching { File(filesDir, FILE_NAME).delete() }
    }
}
