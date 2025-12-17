package com.example.appbook.models

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class ModelChapter(
    var id: String = "",
    var bookId: String = "",
    var title: String = "",   // Ví dụ: "Chương 1"
    var content: String = "", // Nội dung text dài
    var timestamp: Long = 0
)