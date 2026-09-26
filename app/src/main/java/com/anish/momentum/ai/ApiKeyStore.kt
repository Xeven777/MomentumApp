package com.anish.momentum.ai

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts the user's own API key with an AES-256-GCM key that lives in the
 * Android Keystore and never leaves it.
 *
 * This is a deliberate replacement for androidx.security.crypto: that library
 * pulls in Google Tink (~280 extra classes, visible in the R8 mapping) and is
 * still on an alpha version. The Keystore surface we actually need is small
 * enough to own outright.
 *
 * Blocking: call from a background thread.
 */
class ApiKeyStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun getApiKey(): String {
        val stored = prefs.getString(KEY_API, null) ?: return ""
        return try {
            decrypt(stored)
        } catch (e: Exception) {
            // Key invalidated (e.g. lock-screen change) or the blob is corrupt.
            Log.w(TAG, "Could not decrypt the stored API key, clearing it", e)
            prefs.edit().remove(KEY_API).apply()
            ""
        }
    }

    fun setApiKey(value: String) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            clear()
            return
        }
        val encrypted = try {
            encrypt(trimmed)
        } catch (e: Exception) {
            // A stale key can make every encrypt attempt fail; drop and retry once.
            Log.w(TAG, "Encryption failed, regenerating the keystore key", e)
            deleteKeystoreKey()
            encrypt(trimmed)
        }
        prefs.edit().putString(KEY_API, encrypted).apply()
    }

    fun hasApiKey(): Boolean = getApiKey().isNotBlank()

    fun clear() {
        prefs.edit().remove(KEY_API).apply()
    }

    /** e.g. "••••3f9a" — safe to show in the UI. */
    fun maskedKey(): String {
        val key = getApiKey()
        if (key.isBlank()) return ""
        return if (key.length <= 4) "••••" else "••••" + key.takeLast(4)
    }

    // ------------------------------------------------------------- internals

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        // IV is not a secret and must be stored alongside the ciphertext.
        val payload = cipher.iv + cipherText
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        require(payload.size > GCM_IV_LENGTH) { "stored key is malformed" }
        val iv = payload.copyOfRange(0, GCM_IV_LENGTH)
        val cipherText = payload.copyOfRange(GCM_IV_LENGTH, payload.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }
        return createKey()
    }

    private fun createKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun deleteKeystoreKey() {
        try {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(KEY_ALIAS)
        } catch (e: KeyPermanentlyInvalidatedException) {
            Log.w(TAG, "Keystore key permanently invalidated", e)
        } catch (e: Exception) {
            Log.w(TAG, "Could not delete keystore key", e)
        }
    }

    companion object {
        private const val TAG = "ApiKeyStore"
        private const val FILE_NAME = "momentum_secure"
        private const val KEY_API = "api_key"
        private const val KEY_ALIAS = "momentum_api_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_BITS = 128
    }
}
