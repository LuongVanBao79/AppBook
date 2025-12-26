package com.example.appbook.models

import androidx.annotation.Keep
import com.google.firebase.database.IgnoreExtraProperties

@Keep
@IgnoreExtraProperties
data class ModelChapter(
    var id: String = "",
    var bookId: String = "",
    var title: String = "",   // Ví dụ: "Chương 1"
    var content: String = "", // Nội dung text dài
    var timestamp: Long = 0
)