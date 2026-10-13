package com.closecode.ui.ide

import java.io.File

/**
 * Part B terminal seam. The panel talks to this interface; today only the pipe backend
 * exists. The real PTY (jackpal Apache-2.0 core + own forkpty JNI) plugs in behind
 * [USE_PTY] with zero panel changes — and the pipe stays as the fallback behind the flag.
 */
interface ShellBackend {
    val isRunning: Boolean
    fun start()
    fun run(command: String)
    fun killRunning()
    fun setSize(cols: Int, rows: Int)
    fun close()
}

/** Set true once the vendored PTY + JNI bridge lands. Pipe stays as fallback. */
internal const val USE_PTY = false

/** The current pipe implementation, unchanged in behavior, behind the interface. */
internal class PipeShellBackend(
    directory: File?,
    onLine: (String) -> Unit,
) : ShellBackend {
    private val shell = ShellProcess(directory = directory, onLine = onLine)

    override val isRunning: Boolean get() = shell.isRunning
    override fun start() = shell.start()
    override fun run(command: String) = shell.run(command)
    override fun killRunning() = shell.killRunning()
    override fun setSize(cols: Int, rows: Int) = shell.setSize(cols, rows)
    override fun close() = shell.close()
}

internal fun openShell(directory: File?, onLine: (String) -> Unit): ShellBackend =
    // PTY branch lands here; the pipe is the fallback either way.
    PipeShellBackend(directory, onLine)
