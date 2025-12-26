package com.example.appbook.models

import androidx.annotation.Keep


@Keep
data class ModelComment(
    var id: String = "",
    var bookId: String = "",
    var uid: String = "",      // ID người bình luận
    var comment: String = "",  // Nội dung bình luận
    var timestamp: Long = 0,    // QUAN TRỌNG: Đổi sang Long để sort đúng thời gian
    var adminReply: String = "",      // Nội dung trả lời
    var replyTimestamp: Long = 0
)