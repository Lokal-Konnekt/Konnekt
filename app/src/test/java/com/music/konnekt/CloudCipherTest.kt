package com.music.konnekt

import com.music.konnekt.auth.CloudCipher
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CloudCipherTest {
    @Test
    fun roundTripAndWrongPassword() {
        val salt = CloudCipher.randomBytes(16)
        val key = CloudCipher.deriveKey("a long private password", salt)
        val iv = CloudCipher.randomBytes(12)
        val plain = "playlist and account secrets".toByteArray()
        val encrypted = CloudCipher.encrypt(plain, key, iv)

        assertFalse(plain.contentEquals(encrypted))
        assertArrayEquals(plain, CloudCipher.decrypt(encrypted, key, iv))
        val wrongKey = CloudCipher.deriveKey("another private password", salt)
        org.junit.Assert.assertThrows(Exception::class.java) {
            CloudCipher.decrypt(encrypted, wrongKey, iv)
        }
    }
}
