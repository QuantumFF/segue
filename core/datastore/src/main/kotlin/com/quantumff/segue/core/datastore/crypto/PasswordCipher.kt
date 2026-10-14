package com.quantumff.segue.core.datastore.crypto

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts account passwords at rest. */
interface PasswordCipher {
    /** Returns Base64 text that is safe to store in a file. */
    fun encrypt(plaintext: String): String

    /** Null when the text cannot be decrypted, for example after a restore where the key is gone. */
    fun decrypt(encoded: String): String?
}

fun interface SecretKeyProvider {
    fun key(): SecretKey
}

/**
 * AES-256-GCM. The stored form is Base64 of `IV (12 bytes) || ciphertext || tag`. The cipher picks
 * a random IV for every encryption (Android Keystore keys require that).
 */
class AesGcmPasswordCipher(private val keyProvider: SecretKeyProvider) : PasswordCipher {
    override fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider.key())
        val iv = cipher.iv
        check(iv.size == IV_BYTES) { "Unexpected IV size ${iv.size}" }
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val out = ByteBuffer.allocate(iv.size + ciphertext.size).put(iv).put(ciphertext).array()
        return Base64.getEncoder().encodeToString(out)
    }

    override fun decrypt(encoded: String): String? = try {
        val bytes = Base64.getDecoder().decode(encoded)
        if (bytes.size <= IV_BYTES) {
            null
        } else {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, keyProvider.key(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
            cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES).toString(Charsets.UTF_8)
        }
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: GeneralSecurityException) {
        null
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
