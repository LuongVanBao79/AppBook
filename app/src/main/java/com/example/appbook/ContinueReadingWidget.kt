package com.example.appbook

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.example.appbook.activities.ReadingActivity

class ContinueReadingWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }
}

internal fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int
) {
    // 1. Lấy SharedPreferences (Đã mã hóa hoặc thường tùy bạn chọn ở bước trước)
    val prefs = try {
        com.example.appbook.utils.SecurityUtils.getEncryptedPrefs(context)
    } catch (e: Exception) {
        null
    }

    // Nếu lỗi đọc file, coi như chưa có dữ liệu
    val bookId = prefs?.getString("lastBookId", null)
    val chapterId = prefs?.getString("lastChapterId", "")
    val chapterTitle = prefs?.getString("lastChapterTitle", "Đọc tiếp...") ?: "Đọc tiếp..."

    val views = RemoteViews(context.packageName, R.layout.continue_reading_widget)

    // --- XỬ LÝ GIAO DIỆN ---
    if (bookId == null) {
        // TRƯỜNG HỢP 1: TRỐNG / ĐÃ ĐĂNG XUẤT
        // Hiện layout Empty, Ẩn layout Content
        views.setViewVisibility(R.id.layoutContent, View.GONE)
        views.setViewVisibility(R.id.layoutEmpty, View.VISIBLE)

        // **QUAN TRỌNG:** Tạo Intent mở MainActivity (Màn hình đăng nhập/Trang chủ)
        val intentOpenApp = Intent(context, com.example.appbook.activities.SplashActivity::class.java)
        val pendingIntentOpen = PendingIntent.getActivity(
            context, 0, intentOpenApp, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Gán sự kiện bấm vào toàn bộ widget để mở App
        views.setOnClickPendingIntent(R.id.widgetContainer, pendingIntentOpen)

    } else {
        // TRƯỜNG HỢP 2: CÓ DỮ LIỆU
        // Hiện layout Content, Ẩn layout Empty
        views.setViewVisibility(R.id.layoutContent, View.VISIBLE)
        views.setViewVisibility(R.id.layoutEmpty, View.GONE)

        // Set text
        views.setTextViewText(R.id.widgetTitle, "TIẾP TỤC ĐỌC") // Sửa lại text cho hợp style mới
        views.setTextViewText(R.id.widgetChapterInfo, chapterTitle)

        // Tạo Intent mở thẳng vào trang đọc sách (ReadingActivity)
        val intentReading = Intent(context, ReadingActivity::class.java).apply {
            putExtra("BOOK_ID", bookId)
            putExtra("CHAPTER_ID", chapterId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntentReading = PendingIntent.getActivity(
            context, 0, intentReading, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Gán sự kiện click cho nút Play và toàn bộ khung
        views.setOnClickPendingIntent(R.id.widgetContainer, pendingIntentReading)
        views.setOnClickPendingIntent(R.id.widgetContinueBtn, pendingIntentReading)
    }

    appWidgetManager.updateAppWidget(appWidgetId, views)
}