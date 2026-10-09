package com.example.wajuscanner.core.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Ujian Keystore AES-GCM (perlu Android — jalankan sebagai androidTest). */
@RunWith(AndroidJUnit4::class)
class SecretCipherTest {

    @Test
    fun roundTripRecoversPlaintext() {
        val cipher = SecretCipher()
        val secret = "AIzaSyTEST-key-1234567890"

        val encrypted = cipher.encrypt(secret)

        assertTrue("ciphertext mesti berformat iv:ct", encrypted.contains(":"))
        assertFalse("plaintext tidak boleh hadir dalam ciphertext", encrypted.contains(secret))
        assertEquals(secret, cipher.decrypt(encrypted))
    }

    @Test
    fun malformedInputReturnsNull() {
        assertNull(SecretCipher().decrypt("bukan-format-sah"))
    }
}
