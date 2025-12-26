package com.example.appbook.models

import androidx.annotation.Keep

@Keep
data class ModelCategory(
    var id: String = "",
    var category: String = "", // Tên danh mục
    var uid: String = "",      // Admin tạo
    var timestamp: Long = 0
)