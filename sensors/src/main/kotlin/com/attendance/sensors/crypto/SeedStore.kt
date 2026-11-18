package com.attendance.sensors.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.attendance.sensors.SensorException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class SeedStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = Any()

    fun protect(seed: ByteArray) {
        if (seed.size != SEED_BYTES) throw SensorException("seed must be 32 bytes")
        synchronized(lock) {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val iv = cipher.iv
            check(iv.size == IV_BYTES) { "AES-GCM IV must be 12 bytes" }
            val encrypted = cipher.doFinal(seed)
            val blob = ByteArray(iv.size + encrypted.size)
            iv.copyInto(blob, destinationOffset = 0)
            encrypted.copyInto(blob, destinationOffset = iv.size)
            val stored = preferences.edit()
                .putString(BLOB_KEY, Base64.encodeToString(blob, Base64.NO_WRAP))
                .commit()
            if (!stored) throw SensorException("Protected seed could not be stored")
        }
    }

    fun reveal(): ByteArray {
        synchronized(lock) {
            val encoded = preferences.getString(BLOB_KEY, null)
                ?: throw SensorException("No protected seed; call protectSeed before revealSeed")
            val blob = try {
                Base64.decode(encoded, Base64.NO_WRAP)
            } catch (_: IllegalArgumentException) {
                throw SensorException("Protected seed is corrupt")
            }
            if (blob.size <= IV_BYTES) throw SensorException("Protected seed is corrupt")
            val iv = blob.copyOfRange(0, IV_BYTES)
            val encrypted = blob.copyOfRange(IV_BYTES, blob.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            val seed = try {
                cipher.doFinal(encrypted)
            } catch (_: Exception) {
                throw SensorException("Protected seed could not be unwrapped")
            }
            if (seed.size != SEED_BYTES) throw SensorException("Protected seed is corrupt")
            return seed
        }
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "com.attendance.sensors.seed"
        const val PREFS_NAME = "com.attendance.sensors.seed"
        const val BLOB_KEY = "blob"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val GCM_TAG_BITS = 128
        const val SEED_BYTES = 32
    }
}
