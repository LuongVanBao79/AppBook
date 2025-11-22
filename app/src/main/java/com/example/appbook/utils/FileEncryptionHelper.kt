package com.example.appbook.utils

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Hỗ trợ mã hóa/giải mã file PDF bằng AES-GCM với khóa dẫn xuất từ mật khẩu.
 * Chỉ dùng cho mục đích demo; không nên giữ mật khẩu/khóa cố định trong ứng dụng thật.
 */
object FileEncryptionHelper {

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEY_ALGORITHM = "AES"
    private const val PBKDF_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 65_536
    private const val KEY_LENGTH = 256
    private const val GCM_TAG_LENGTH = 128
    private const val VERSION_MARKER: Byte = 1

    private val random = SecureRandom()

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKey {
        val factory = SecretKeyFactory.getInstance(PBKDF_ALGORITHM)
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH)
        val secret = factory.generateSecret(spec).encoded
        return SecretKeySpec(secret, KEY_ALGORITHM)
    }

    fun encrypt(data: ByteArray, password: CharArray): ByteArray {
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val key = deriveKey(password, salt)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)
        val cipherText = cipher.doFinal(data)

        return ByteBuffer.allocate(1 + salt.size + iv.size + cipherText.size)
            .put(VERSION_MARKER)
            .put(salt)
            .put(iv)
            .put(cipherText)
            .array()
    }

    fun decrypt(data: ByteArray, password: CharArray): ByteArray {
        if (data.isEmpty()) throw IllegalArgumentException("Dữ liệu mã hóa rỗng")

        val buffer = ByteBuffer.wrap(data)
        val version = buffer.get()
        if (version != VERSION_MARKER) {
            throw IllegalArgumentException("Định dạng file mã hóa không hợp lệ")
        }

        val salt = ByteArray(16).also { buffer.get(it) }
        val iv = ByteArray(12).also { buffer.get(it) }
        val cipherText = ByteArray(buffer.remaining()).also { buffer.get(it) }

        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        return cipher.doFinal(cipherText)
    }

    fun encryptToFile(plainData: ByteArray, password: CharArray, target: File) {
        val encrypted = encrypt(plainData, password)
        FileOutputStream(target).use { it.write(encrypted) }
    }

    fun decryptToFile(encryptedData: ByteArray, password: CharArray, target: File) {
        val plain = decrypt(encryptedData, password)
        FileOutputStream(target).use { it.write(plain) }
    }
}
