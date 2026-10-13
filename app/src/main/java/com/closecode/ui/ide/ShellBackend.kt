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
    fun setSize(cols: Int, rows: Int = 24)
    fun close()
}

/** The native PTY is selected when packaged; JVM tests and unsupported ABIs retain the pipe. */
internal const val USE_PTY = true

internal class TerminalOutputFramer(private val onLine: (String) -> Unit) {
    private val pending = StringBuilder()

    val hasPending: Boolean get() = pending.isNotEmpty()

    fun append(text: String) {
        pending.append(text)
        var newline = pending.indexOf("\n")
        while (newline >= 0) {
            emit(pending.substring(0, newline))
            pending.delete(0, newline + 1)
            newline = pending.indexOf("\n")
        }
    }

    fun flushPartial() {
        if (pending.isNotEmpty()) {
            emit(pending.toString())
            pending.clear()
        }
    }

    private fun emit(raw: String) {
        onLine(if (raw.endsWith("\r")) raw.dropLast(1) else raw)
    }
}

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
    if (USE_PTY && NativePty.isAvailable) {
        FallbackShellBackend(directory, onLine)
    } else {
        PipeShellBackend(directory, onLine)
    }

private class FallbackShellBackend(
    private val directory: File?,
    private val onLine: (String) -> Unit,
) : ShellBackend {
    private var delegate: ShellBackend = PtyShellBackend(directory, onLine)

    override val isRunning: Boolean get() = delegate.isRunning

    override fun start() {
        try {
            delegate.start()
        } catch (_: LinkageError) {
            fallBack()
        } catch (_: IllegalStateException) {
            fallBack()
        }
        if (!delegate.isRunning) fallBack()
    }

    override fun run(command: String) = delegate.run(command)
    override fun killRunning() = delegate.killRunning()
    override fun setSize(cols: Int, rows: Int) = delegate.setSize(cols, rows)
    override fun close() = delegate.close()

    private fun fallBack() {
        delegate.close()
        delegate = PipeShellBackend(directory, onLine)
        delegate.start()
    }
}

internal class PtyShellBackend(
    private val directory: File?,
    private val onLine: (String) -> Unit,
) : ShellBackend {
    private var handle: Long = 0L
    private var reader: Thread? = null
    @Volatile
    private var running = false
    private var columns = 80
    private var rows = 24

    override val isRunning: Boolean get() = running

    override fun start() {
        if (handle != 0L && running) return
        if (handle != 0L) {
            NativePty.close(handle)
            handle = 0L
        }
        handle = NativePty.open(
            SHELL_PATH,
            directory?.absolutePath.orEmpty(),
            columns,
            rows,
        )
        if (handle == 0L) {
            onLine("PTY could not start the Android shell.")
            return
        }
        running = true
        reader = Thread(::readOutput, "closecode-pty-reader").apply {
            isDaemon = true
            start()
        }
        write("export PS1='' TERM=xterm-256color COLUMNS=$columns LINES=$rows\n")
    }

    override fun run(command: String) {
        if (!running) start()
        if (running) write("${shellLine(command)}\n")
    }

    override fun killRunning() {
        if (handle != 0L) NativePty.interrupt(handle)
    }

    override fun setSize(cols: Int, rows: Int) {
        columns = cols.coerceIn(20, 512)
        this.rows = rows.coerceIn(10, 512)
        if (handle != 0L) NativePty.resize(handle, columns, this.rows)
    }

    override fun close() {
        val oldHandle = handle
        handle = 0L
        running = false
        if (oldHandle != 0L) NativePty.close(oldHandle)
        reader?.interrupt()
        reader = null
    }

    private fun write(text: String) {
        NativePty.write(handle, text.toByteArray(Charsets.UTF_8))
    }

    private fun readOutput() {
        val activeHandle = handle
        val framer = TerminalOutputFramer(onLine)
        val bytes = ByteArray(4096)
        var lastDataAt = System.currentTimeMillis()
        try {
            while (running) {
                val count = NativePty.read(activeHandle, bytes)
                if (count < 0) break
                if (count == 0) {
                    if (framer.hasPending && System.currentTimeMillis() - lastDataAt >= 150) {
                        framer.flushPartial()
                    }
                    continue
                }
                framer.append(String(bytes, 0, count, Charsets.UTF_8))
                lastDataAt = System.currentTimeMillis()
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (_: IllegalStateException) {
            if (running) onLine("PTY connection closed.")
        } finally {
            framer.flushPartial()
            running = false
        }
    }
}

internal object NativePty {
    val isAvailable: Boolean = try {
        System.loadLibrary("closecode_pty")
        true
    } catch (_: UnsatisfiedLinkError) {
        false
    }

    external fun open(shell: String, directory: String, columns: Int, rows: Int): Long
    external fun read(handle: Long, buffer: ByteArray): Int
    external fun write(handle: Long, bytes: ByteArray): Int
    external fun resize(handle: Long, columns: Int, rows: Int): Boolean
    external fun interrupt(handle: Long): Boolean
    external fun close(handle: Long): Boolean
}

private const val SHELL_PATH = "/system/bin/sh"
