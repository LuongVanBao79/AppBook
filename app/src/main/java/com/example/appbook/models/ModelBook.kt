package com.example.appbook.models

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class ModelBook(
    // Định danh
    var id: String = "",
    var uid: String = "", // ID của Admin đăng sách

    // Thông tin sách
    var title: String = "",
    var author: String = "", // MỚI: Thêm tên tác giả
    var description: String = "",
    var categoryId: String = "",
    var imageUrl: String = "", // Link ảnh bìa từ Cloudinary

    // Thống kê
    var viewsCount: Long = 0,
    var downloadsCount: Long = 0, // Giữ lại để hiển thị nếu cần (hoặc đổi thành likeCount tùy ý)
    var timestamp: Long = 0,

    // Trạng thái local (dùng để xử lý giao diện Yêu thích)
    var isFavorite: Boolean = false
)