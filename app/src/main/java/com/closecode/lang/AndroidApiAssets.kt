package com.closecode.lang

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

object AndroidApiAssets {
    private const val PATH = "android-api.txt"

    @Volatile
    private var loaded: AndroidApiIndex? = null

    private val loading = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun cached(): AndroidApiIndex = loaded ?: AndroidApiIndex.EMPTY

    fun load(context: Context, onReady: (AndroidApiIndex) -> Unit) {
        val existing = loaded
        if (existing != null) {
            mainHandler.post { onReady(existing) }
            return
        }
        if (!loading.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        Thread {
            val index = runCatching {
                appContext.assets.open(PATH).use { stream ->
                    AndroidApiIndex.parse(stream.bufferedReader().readText())
                }
            }.getOrElse { emptyFallbackIndex() }
            loaded = index
            loading.set(false)
            mainHandler.post { onReady(index) }
        }.apply {
            isDaemon = true
            name = "closecode-android-api-index"
            start()
        }
    }

    private fun emptyFallbackIndex(): AndroidApiIndex {
        val path = System.getProperty("closecode.androidApiIndex") ?: return AndroidApiIndex.EMPTY
        val file = File(path)
        return if (file.isFile) AndroidApiIndex.parse(file.readText()) else AndroidApiIndex.EMPTY
    }
}