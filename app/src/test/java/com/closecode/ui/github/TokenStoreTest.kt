package com.closecode.ui.github

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenStoreTest {

    @Test
    fun unavailableSecureStoreSignsOutAndNeverWritesPlaintext() {
        val plain = FakeTokenPreferences("legacy-token")
        val store = TokenStore(encrypted = null, plain = plain)

        assertNull(store.read())
        assertFalse(store.write("new-token"))
        assertNull(plain.token)
        assertNotNull(store.storageError)
    }

    @Test
    fun legacyTokenMovesToSecureStorageAndIsRemovedFromPlainPreferences() {
        val secure = FakeTokenPreferences()
        val plain = FakeTokenPreferences("legacy-token")
        val store = TokenStore(encrypted = secure, plain = plain)

        assertEquals("legacy-token", store.read())
        assertEquals("legacy-token", secure.token)
        assertNull(plain.token)
        assertNull(store.storageError)
    }

    @Test
    fun failedSecureMigrationDiscardsLegacyTokenAndSignsOut() {
        val secure = FakeTokenPreferences(allowWrite = false)
        val plain = FakeTokenPreferences("legacy-token")
        val store = TokenStore(encrypted = secure, plain = plain)

        assertNull(store.read())
        assertNull(secure.token)
        assertNull(plain.token)
        assertNotNull(store.storageError)
    }

    @Test
    fun failedSecureWriteDoesNotFallBackToPlainPreferences() {
        val secure = FakeTokenPreferences(allowWrite = false)
        val plain = FakeTokenPreferences()
        val store = TokenStore(encrypted = secure, plain = plain)

        assertFalse(store.write("new-token"))
        assertNull(secure.token)
        assertNull(plain.token)
        assertNotNull(store.storageError)
    }

    @Test
    fun clearRemovesTokenFromBothStores() {
        val secure = FakeTokenPreferences("saved-token")
        val plain = FakeTokenPreferences("legacy-token")
        val store = TokenStore(encrypted = secure, plain = plain)

        assertTrue(store.clear())
        assertNull(secure.token)
        assertNull(plain.token)
    }

    private class FakeTokenPreferences(
        var token: String? = null,
        private val allowWrite: Boolean = true,
    ) : TokenPreferences {
        override fun read(): String? = token

        override fun write(token: String): Boolean {
            if (!allowWrite) return false
            this.token = token
            return true
        }

        override fun clear(): Boolean {
            token = null
            return true
        }
    }
}
