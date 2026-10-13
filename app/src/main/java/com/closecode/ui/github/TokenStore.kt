package com.closecode.ui.github

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Where the GitHub access token lives between launches.
 *
 * Keystore-backed encrypted preferences: the token is encrypted at rest, and
 * `android:allowBackup="false"` stops the directory being copied off the device by a backup.
 * The token is never written to a log, an exception message or a repository.
 *
 * A token stored by an older version in plain preferences is moved into the encrypted store on
 * first read and wiped from the old file. If the encrypted store cannot start (broken Keystore
 * on some devices), the plain file stays the fallback rather than signing the user out — a
 * working sign-in beats a perfect one that throws on launch.
 */
class TokenStore(context: Context) {

    private val app: Context = context.applicationContext

    private val encrypted: SharedPreferences? = runCatching {
        val masterKey = MasterKey.Builder(app)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            app,
            FILENAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrNull()

    private val plain: SharedPreferences by lazy {
        app.getSharedPreferences(FILENAME, Context.MODE_PRIVATE)
    }

    fun read(): String? {
        encrypted?.getString(KEY, null)?.takeIf { it.isNotEmpty() }?.let { return it }
        // An older version's token: adopt it, then forget where it came from.
        val legacy = plain.getString(KEY, null)?.takeIf { it.isNotEmpty() }
        if (legacy != null && encrypted != null) {
            encrypted.edit().putString(KEY, legacy).apply()
            plain.edit().remove(KEY).apply()
        }
        return legacy
    }

    fun write(token: String) {
        (encrypted ?: plain).edit().putString(KEY, token).apply()
    }

    fun clear() {
        encrypted?.edit()?.remove(KEY)?.apply()
        plain.edit().remove(KEY).apply()
    }

    private companion object {
        const val FILENAME = "closecode_github"
        const val KEY = "access_token"
    }
}
