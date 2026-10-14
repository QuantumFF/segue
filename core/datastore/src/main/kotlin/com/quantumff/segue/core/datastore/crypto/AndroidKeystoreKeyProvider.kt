package com.quantumff.segue.core.datastore.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * A non-exportable AES-256 key in the Android Keystore, created on first use.
 *
 * The key never leaves this device, so passwords encrypted with it cannot be restored from a
 * backup. Exclude the accounts file from backups; a user restoring to a new device signs in again.
 */
internal class AndroidKeystoreKeyProvider(private val alias: String) : SecretKeyProvider {
    @Volatile
    private var cached: SecretKey? = null

    override fun key(): SecretKey = cached ?: synchronized(this) {
        cached ?: loadOrCreate().also { cached = it }
    }

    private fun loadOrCreate(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_BITS)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_BITS = 256
    }
}
