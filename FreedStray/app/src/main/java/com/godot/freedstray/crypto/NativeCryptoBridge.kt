package com.godot.freedstray.crypto

import android.util.Log
import com.godot.freedstray.BuildConfig
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object NativeCryptoBridge {

    private const val TAG = "NativeCryptoBridge"
    val DEFAULT_AES_KEY_HEX: String = BuildConfig.AES_KEY

    init {
        try {
            System.loadLibrary("freedstray_native")
            Log.i(TAG, "libfreedstray_native.so loaded successfully.")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load libfreedstray_native.so: ${e.message}")
        }
    }

    fun hexToBytes(hex: String): ByteArray {
        val cleanHex = hex.trim()
        val len = cleanHex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(cleanHex[i], 16) shl 4) + Character.digit(cleanHex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    fun encryptPayload(keyHex: String = DEFAULT_AES_KEY_HEX, plainText: String): ByteArray? {
        return try {
            val keyBytes = hexToBytes(keyHex)
            val plainBytes = plainText.toByteArray(Charsets.UTF_8)
            val iv = ByteArray(12)
            SecureRandom().nextBytes(iv)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = SecretKeySpec(keyBytes, "AES")
            val gcmSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)

            val encryptedWithTag = cipher.doFinal(plainBytes)

            // Format: IV (12 bytes) + Ciphertext + Auth Tag (16 bytes)
            val result = ByteArray(12 + encryptedWithTag.size)
            System.arraycopy(iv, 0, result, 0, 12)
            System.arraycopy(encryptedWithTag, 0, result, 12, encryptedWithTag.size)
            result
        } catch (e: Exception) {
            Log.e(TAG, "Encryption failed: ${e.message}")
            null
        }
    }

    fun decryptPayload(keyHex: String = DEFAULT_AES_KEY_HEX, encryptedBytes: ByteArray): String? {
        return try {
            if (encryptedBytes.size < 12 + 16) {
                Log.e(TAG, "Encrypted payload too short: ${encryptedBytes.size} bytes")
                return null
            }
            val keyBytes = hexToBytes(keyHex)

            val iv = ByteArray(12)
            System.arraycopy(encryptedBytes, 0, iv, 0, 12)

            val encryptedWithTagSize = encryptedBytes.size - 12
            val encryptedWithTag = ByteArray(encryptedWithTagSize)
            System.arraycopy(encryptedBytes, 12, encryptedWithTag, 0, encryptedWithTagSize)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = SecretKeySpec(keyBytes, "AES")
            val gcmSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)

            val decryptedBytes = cipher.doFinal(encryptedWithTag)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed: ${e.message}")
            null
        }
    }
}
