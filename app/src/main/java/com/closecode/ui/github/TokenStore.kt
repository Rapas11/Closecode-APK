package com.closecode.ui.github

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

private const val TOKEN_KEY = "access_token"

/**
 * Where the GitHub access token lives between launches.
 *
 * Tokens are only read or written through Keystore-backed encrypted preferences. A token from an
 * older plain-preferences store is migrated only after encrypted storage succeeds; if it cannot
 * be migrated, the old value is removed and the user remains signed out.
 */
class TokenStore internal constructor(
    private val encrypted: TokenPreferences?,
    private val plain: TokenPreferences,
) {

    constructor(context: Context) : this(
        encryptedPreferences(context.applicationContext),
        SharedPreferencesTokenPreferences(
            context.applicationContext.getSharedPreferences(FILENAME, Context.MODE_PRIVATE),
        ),
    )

    @Volatile
    var storageError: String? = if (encrypted == null) SECURE_STORAGE_ERROR else null
        private set

    @Synchronized
    fun read(): String? {
        val secure = encrypted
        if (secure == null) {
            discardPlainToken()
            storageError = SECURE_STORAGE_ERROR
            return null
        }

        return try {
            secure.read()?.takeIf(String::isNotEmpty)?.let { return it }
            val legacy = plain.read()?.takeIf(String::isNotEmpty) ?: return null
            if (!secure.write(legacy)) {
                clearSecure(secure)
                discardPlainToken()
                storageError = SECURE_STORAGE_ERROR
                return null
            }
            if (!plain.clear()) {
                clearSecure(secure)
                storageError = SECURE_STORAGE_ERROR
                return null
            }
            legacy
        } catch (_: Exception) {
            clearSecure(secure)
            discardPlainToken()
            storageError = SECURE_STORAGE_ERROR
            null
        }
    }

    /** Returns false and sets [storageError] if secure persistence failed. */
    @Synchronized
    fun write(token: String): Boolean {
        require(token.isNotBlank()) { "GitHub access token must not be blank." }
        val secure = encrypted
        if (secure == null) {
            discardPlainToken()
            storageError = SECURE_STORAGE_ERROR
            return false
        }

        return try {
            if (!secure.write(token)) {
                clearSecure(secure)
                discardPlainToken()
                storageError = SECURE_STORAGE_ERROR
                false
            } else if (!plain.clear()) {
                clearSecure(secure)
                storageError = SECURE_STORAGE_ERROR
                false
            } else {
                storageError = null
                true
            }
        } catch (_: Exception) {
            clearSecure(secure)
            discardPlainToken()
            storageError = SECURE_STORAGE_ERROR
            false
        }
    }

    @Synchronized
    fun clear(): Boolean {
        val secureCleared = try {
            encrypted?.clear() ?: true
        } catch (_: Exception) {
            false
        }
        val plainCleared = discardPlainToken()
        if (!secureCleared || !plainCleared) {
            storageError = "CloseCode could not clear the saved GitHub sign-in. Please try again."
            return false
        }
        storageError = if (encrypted == null) SECURE_STORAGE_ERROR else null
        return true
    }

    private fun discardPlainToken(): Boolean = try {
        plain.clear()
    } catch (_: Exception) {
        false
    }

    private fun clearSecure(secure: TokenPreferences) {
        try {
            secure.clear()
        } catch (_: Exception) {
            storageError = SECURE_STORAGE_ERROR
        }
    }

    private companion object {
        const val FILENAME = "closecode_github"
        const val SECURE_STORAGE_ERROR =
            "Secure GitHub token storage is unavailable. You are signed out; please sign in again when secure storage is available."

        fun encryptedPreferences(context: Context): TokenPreferences? = try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            SharedPreferencesTokenPreferences(
                EncryptedSharedPreferences.create(
                    context,
                    FILENAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                ),
            )
        } catch (_: Exception) {
            null
        }
    }
}

internal interface TokenPreferences {
    fun read(): String?
    fun write(token: String): Boolean
    fun clear(): Boolean
}

private class SharedPreferencesTokenPreferences(
    private val preferences: SharedPreferences,
) : TokenPreferences {
    override fun read(): String? = preferences.getString(TOKEN_KEY, null)

    override fun write(token: String): Boolean =
        preferences.edit().putString(TOKEN_KEY, token).commit()

    override fun clear(): Boolean = preferences.edit().remove(TOKEN_KEY).commit()
}
