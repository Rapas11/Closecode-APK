package com.closecode.ui.project

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * A tiny dependency-free HTTP server for live web previews.
 *
 * It serves only files beneath [root], denies dotfiles and the project metadata file, and sends
 * `Cache-Control: no-store` so the on-device WebView always shows the latest save. Auto-refresh
 * itself is a WebView reload driven by the IDE's save counter; the server only has to serve fresh
 * bytes when asked.
 */
class PreviewServer(private val root: File) {
    private var serverSocket: ServerSocket? = null
    private var pool: ExecutorService? = null
    private var acceptThread: Thread? = null

    val port: Int get() = serverSocket?.localPort ?: -1
    val isRunning: Boolean get() = serverSocket?.isBound == true && serverSocket?.isClosed == false

    @Synchronized
    fun start(): Int {
        serverSocket?.let { if (!it.isClosed) return it.localPort }
        val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val workers = Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "closecode-preview").apply { isDaemon = true }
        }
        serverSocket = socket
        pool = workers
        acceptThread = Thread({
            while (!socket.isClosed) {
                try {
                    val client = socket.accept()
                    workers.execute { handle(client) }
                } catch (_: Exception) {
                    if (socket.isClosed) break
                }
            }
        }, "closecode-preview-accept").apply {
            isDaemon = true
            start()
        }
        return socket.localPort
    }

    @Synchronized
    fun stop() {
        runCatching { serverSocket?.close() }
        runCatching { pool?.shutdownNow() }
        runCatching { acceptThread?.interrupt() }
        serverSocket = null
        pool = null
        acceptThread = null
    }

    fun baseUrl(): String = "http://127.0.0.1:$port"

    private fun handle(socket: Socket) {
        socket.soTimeout = 5_000
        socket.use { client ->
            val reader = BufferedReader(InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))
            val requestLine = reader.readLine() ?: return
            // Consume headers; the preview server does not need them.
            var headerLines = 0
            while (headerLines < 100) {
                val line = reader.readLine() ?: return
                headerLines++
                if (line.isEmpty()) break
            }
            val parts = requestLine.split(' ')
            if (parts.size < 2 || parts[0] != "GET") {
                writeStatus(client, 405, "Method Not Allowed", "text/plain", "Method Not Allowed".toByteArray())
                return
            }
            val file = resolveVisibleFile(parts[1])
            if (file == null) {
                writeStatus(client, 404, "Not Found", "text/plain", "Not Found".toByteArray())
                return
            }
            val bytes = runCatching { file.readBytes() }.getOrNull()
            if (bytes == null) {
                writeStatus(client, 500, "Read Failed", "text/plain", "Read Failed".toByteArray())
                return
            }
            writeStatus(client, 200, "OK", contentTypeFor(file.name), bytes)
        }
    }

    /**
     * Maps a raw HTTP target to a visible file, or null when the request must not be served.
     * Dot segments, the IDE metadata file, escapes above [root], and missing files are all null.
     */
    internal fun resolveVisibleFile(rawTarget: String): File? {
        val pathOnly = rawTarget.substringBefore('?').substringBefore('#')
        if (!pathOnly.startsWith("/")) return null
        val decoded = runCatching {
            URLDecoder.decode(pathOnly.substring(1), StandardCharsets.UTF_8.name())
        }.getOrNull() ?: return null
        if (decoded.isEmpty()) return indexFile(root)
        val segments = decoded.split('/')
        if (segments.any { it.isEmpty() || it == "." || it == ".." || it.startsWith(".") }) return null
        if (segments.any { it == ProjectStore.METADATA }) return null
        val rootPath = root.canonicalPath
        val candidate = File(root, decoded).canonicalFile
        val candidatePath = candidate.canonicalPath
        if (candidatePath != rootPath && !candidatePath.startsWith(rootPath + File.separator)) return null
        if (candidate.isDirectory) return indexFile(candidate)
        return candidate.takeIf { it.isFile }
    }

    private fun indexFile(directory: File): File? {
        val index = File(directory, "index.html")
        return index.takeIf { it.isFile }
    }

    private fun writeStatus(
        socket: Socket,
        code: Int,
        reason: String,
        contentType: String,
        body: ByteArray,
    ) {
        val header = buildString {
            append("HTTP/1.1 ").append(code).append(' ').append(reason).append("\r\n")
            append("Content-Type: ").append(contentType).append("\r\n")
            append("Content-Length: ").append(body.size).append("\r\n")
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }.toByteArray(StandardCharsets.UTF_8)
        val out = socket.getOutputStream()
        out.write(header)
        out.write(body)
        out.flush()
    }

    companion object {
        fun contentTypeFor(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
            "html", "htm" -> "text/html; charset=utf-8"
            "css" -> "text/css; charset=utf-8"
            "js", "mjs" -> "text/javascript; charset=utf-8"
            "json" -> "application/json; charset=utf-8"
            "xml" -> "application/xml; charset=utf-8"
            "svg" -> "image/svg+xml"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "ico" -> "image/x-icon"
            "txt", "md", "csv" -> "text/plain; charset=utf-8"
            else -> "application/octet-stream"
        }
    }
}
