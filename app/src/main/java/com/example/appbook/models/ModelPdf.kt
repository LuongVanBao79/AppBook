package com.example.appbook.models

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class ModelPdf(
    // Thông tin cơ bản và quản lý
    var uid: String = "",
    var id: String = "",
    var title: String = "",
    var description: String = "",
    var categoryId: String = "",

    // Thông tin về file và URL
    var url: String = "",
    var imageUrl: String = "", // URL ảnh bìa
    var fileSize: Long = 0,    // Dung lượng file (bytes)
    var pagesCount: Int = 0,   // Tổng số trang

    // Thống kê và thời gian
    var timestamp: Long = 0,
    var viewsCount: Long = 0,
    var downloadsCount: Long = 0,

    // Trạng thái tạm thời
    var isFavorite: Boolean = false
) {
    // KHÔNG CẦN ĐỊNH NGHĨA constructor() VÀ constructor(...) CŨ.
    // Data class đã tự động tạo constructor rỗng và constructor đầy đủ.
}