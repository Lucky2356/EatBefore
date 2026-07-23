package com.eatbefore.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypts small secrets with a key held in the Android Keystore, so the key material
 * never enters the app process and cannot be read out of a backup or a rooted dump of
 * DataStore.
 *
 * Used for the optional Open Food Facts password. That password belongs to the user's
 * account on a third-party service; storing it in plain text next to the preferences
 * would leak it to anything that can read app storage.
 *
 * Not a general-purpose crypto layer: AES/GCM with a random IV per value, IV prepended to
 * the ciphertext, Base64 for storage.
 */
@Singleton
class SecretCipher @Inject constructor() {

    fun encrypt(plainText: String): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
    }.getOrNull()

    fun decrypt(stored: String): String? = runCatching {
        val raw = Base64.decode(stored, Base64.NO_WRAP)
        if (raw.size <= IV_LENGTH) return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(TAG_LENGTH_BITS, raw, 0, IV_LENGTH),
        )
        String(cipher.doFinal(raw, IV_LENGTH, raw.size - IV_LENGTH), Charsets.UTF_8)
    }.getOrNull()

    /** Returns the existing key or creates it on first use. */
    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "eatbefore_secrets"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_LENGTH_BITS = 128
    }
}
