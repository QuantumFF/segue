package com.quantumff.segue.core.network.auth

import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject

/** Subsonic token auth (API 1.13.0+): `t = md5(password + salt)`, sent with `s = salt`. */
object SubsonicToken {
    fun create(password: String, salt: String): String {
        val digest = MessageDigest.getInstance("MD5").digest((password + salt).toByteArray(Charsets.UTF_8))
        return digest.toHex()
    }
}

/** Produces the per-request salt. Every request must use a new one. */
fun interface SaltGenerator {
    fun next(): String
}

/** 8 random bytes as 16 hex characters. */
class SecureRandomSaltGenerator @Inject constructor() : SaltGenerator {
    private val random = SecureRandom()

    override fun next(): String = ByteArray(SALT_BYTES).also(random::nextBytes).toHex()

    private companion object {
        const val SALT_BYTES = 8
    }
}

private fun ByteArray.toHex(): String = joinToString(separator = "") { "%02x".format(it) }
