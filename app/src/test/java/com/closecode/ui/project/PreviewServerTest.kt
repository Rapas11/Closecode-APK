package com.closecode.ui.project

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewServerTest {

    private fun projectRoot(): File {
        val root = Files.createTempDirectory("closecode-preview").toFile()
        File(root, "index.html").writeText("<!doctype html><title>Demo</title><link rel=stylesheet href=styles.css>")
        File(root, "styles.css").writeText("body { color: black; }")
        File(root, ".hidden.txt").writeText("nope")
        File(root, ProjectStore.METADATA).writeText("{}")
        return root
    }

    private fun get(port: Int, path: String): Pair<Int, String> {
        val connection = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            code to (stream?.bufferedReader()?.readText().orEmpty())
        } finally {
            connection.disconnect()
        }
    }

    @Test
    fun servesTheProjectEntryAndItsAssets() {
        val server = PreviewServer(projectRoot())
        val port = server.start()
        try {
            val (indexCode, indexBody) = get(port, "/index.html")
            assertEquals(200, indexCode)
            assertTrue(indexBody.contains("<title>Demo</title>"))

            val (rootCode, _) = get(port, "/")
            assertEquals(200, rootCode)

            val (cssCode, cssBody) = get(port, "/styles.css")
            assertEquals(200, cssCode)
            assertTrue(cssBody.contains("color: black"))
        } finally {
            server.stop()
        }
    }

    @Test
    fun refusesHiddenMetadataTraversalAndMissingFiles() {
        val server = PreviewServer(projectRoot())
        val port = server.start()
        try {
            assertEquals(404, get(port, "/missing.html").first)
            assertEquals(404, get(port, "/.hidden.txt").first)
            assertEquals(404, get(port, "/.closecode.json").first)
            assertEquals(404, get(port, "/../outside.txt").first)
            assertEquals(404, get(port, "/%2e%2e/outside.txt").first)
        } finally {
            server.stop()
        }
    }

    @Test
    fun labelsPreviewContentTypes() {
        assertEquals("text/html; charset=utf-8", PreviewServer.contentTypeFor("index.HTML"))
        assertEquals("text/css; charset=utf-8", PreviewServer.contentTypeFor("styles.css"))
        assertEquals("text/javascript; charset=utf-8", PreviewServer.contentTypeFor("app.js"))
        assertEquals("image/png", PreviewServer.contentTypeFor("shot.png"))
        assertEquals("application/octet-stream", PreviewServer.contentTypeFor("archive.bin"))
    }
}
