package com.quantumff.segue.core.datastore

import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.datastore.crypto.AesGcmPasswordCipher
import java.util.Base64
import javax.crypto.KeyGenerator
import org.junit.Test

class AesGcmPasswordCipherTest {
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val cipher = AesGcmPasswordCipher { key }

    @Test
    fun `encrypted passwords decrypt to the original`() {
        val encrypted = cipher.encrypt("correct horse battery staple")

        assertThat(encrypted).doesNotContain("horse")
        assertThat(cipher.decrypt(encrypted)).isEqualTo("correct horse battery staple")
    }

    @Test
    fun `each encryption uses a fresh IV`() {
        assertThat(cipher.encrypt("same")).isNotEqualTo(cipher.encrypt("same"))
    }

    @Test
    fun `tampered ciphertext does not decrypt`() {
        val bytes = Base64.getDecoder().decode(cipher.encrypt("secret"))
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()

        assertThat(cipher.decrypt(Base64.getEncoder().encodeToString(bytes))).isNull()
    }

    @Test
    fun `ciphertext from another key does not decrypt`() {
        val otherKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val encrypted = AesGcmPasswordCipher { otherKey }.encrypt("secret")

        assertThat(cipher.decrypt(encrypted)).isNull()
    }

    @Test
    fun `garbage does not decrypt`() {
        assertThat(cipher.decrypt("not base64!")).isNull()
        assertThat(cipher.decrypt("")).isNull()
    }
}
