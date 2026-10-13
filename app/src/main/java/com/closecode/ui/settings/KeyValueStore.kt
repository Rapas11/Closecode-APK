package com.closecode.ui.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * The bare key/value layer every setting is read from and written to.
 *
 * An interface rather than a concrete class so the reading and writing rules — defaults, clamping
 * and "a corrupt value must not become a setting" — are unit tested against an in-memory store,
 * because SharedPreferences is not on the unit-test classpath.
 */
interface KeyValueStore {
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)

    fun getString(key: String, default: String?): String?
    fun putString(key: String, value: String)

    fun getInt(key: String, default: Int): Int
    fun putInt(key: String, value: Int)

    fun remove(key: String)
    fun clear()
}

/** Private preferences, the same backing store [com.closecode.ui.github.TokenStore] already uses. */
class SharedPreferencesKeyValueStore(context: Context) : KeyValueStore {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILENAME, Context.MODE_PRIVATE)

    override fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)

    override fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    override fun getString(key: String, default: String?): String? = prefs.getString(key, default)

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun getInt(key: String, default: Int): Int = prefs.getInt(key, default)

    override fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val FILENAME = "closecode_settings"
    }
}

/** The same store without a device behind it, for tests and for the CompositionLocal default. */
class InMemoryKeyValueStore(
    initial: Map<String, Any> = emptyMap(),
) : KeyValueStore {

    private val values = initial.toMutableMap()

    override fun getBoolean(key: String, default: Boolean): Boolean =
        values[key] as? Boolean ?: default

    override fun putBoolean(key: String, value: Boolean) {
        values[key] = value
    }

    override fun getString(key: String, default: String?): String? =
        values[key] as? String ?: default

    override fun putString(key: String, value: String) {
        values[key] = value
    }

    override fun getInt(key: String, default: Int): Int = when (val found = values[key]) {
        is Int -> found
        // A number written through some other route still counts as that number; a string that
        // is not one does not, and falls back to the default rather than throwing.
        is String -> found.toIntOrNull() ?: default
        else -> default
    }

    override fun putInt(key: String, value: Int) {
        values[key] = value
    }

    override fun remove(key: String) {
        values.remove(key)
    }

    override fun clear() {
        values.clear()
    }
}
