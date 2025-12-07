package com.example.appbook.utils

import android.util.Base64
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Simple AES-GCM helper for demo purposes.
 * Do not ship hard-coded secrets like this in production code.
 */
object EncryptionHelper {

    init {
        System.loadLibrary("app-security")
    }

    external fun getAesKeyFromNative(): String

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val AES_ALGORITHM = "AES"
    private const val GCM_TAG_LENGTH = 128



    private val charset = Charsets.UTF_8
    private val secureRandom = SecureRandom()

    private fun getSecretKey(): SecretKey {
        // 3. Lấy key từ NDK thay vì hardcode
        val rawSecret = getAesKeyFromNative()

        val keyBytes = rawSecret.toByteArray(charset)
        return SecretKeySpec(keyBytes.copyOf(32), AES_ALGORITHM)
    }

    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return plainText

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = ByteArray(12).also(secureRandom::nextBytes)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(), spec)

        val cipherText = cipher.doFinal(plainText.toByteArray(charset))

        val payload = ByteBuffer.allocate(iv.size + cipherText.size)
            .put(iv)
            .put(cipherText)
            .array()

        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    fun decrypt(cipherText: String): String {
        if (cipherText.isEmpty()) return cipherText

        val payload = Base64.decode(cipherText, Base64.NO_WRAP)
        val buffer = ByteBuffer.wrap(payload)

        val iv = ByteArray(12).apply { buffer.get(this) }
        val encrypted = ByteArray(buffer.remaining()).apply { buffer.get(this) }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)

        val plainBytes = cipher.doFinal(encrypted)
        return String(plainBytes, charset)
    }

    fun decryptOrNull(cipherText: String?): String? {
        return try {
            cipherText?.let { decrypt(it) }
        } catch (_: Exception) {
            null
        }
    }
}
