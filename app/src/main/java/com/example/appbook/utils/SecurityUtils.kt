package com.example.appbook.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecurityUtils {
    // Tên file lưu trữ (cần thống nhất cả chỗ ghi và chỗ đọc)
    private const val PREFS_FILE_NAME = "WidgetPrefs_Encrypted"
    private const val BIO_PREFS_FILE_NAME = "BiometricPrefs_Encrypted"

    fun getEncryptedPrefs(context: Context): SharedPreferences {
        // 1. Tạo hoặc lấy Master Key từ Android Keystore
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        // 2. Khởi tạo EncryptedSharedPreferences
        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, // Mã hóa Key
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM // Mã hóa Value
        )
    }

    fun getBiometricPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            BIO_PREFS_FILE_NAME, // Dùng tên file mới
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}