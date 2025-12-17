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
    // Đọc dữ liệu từ file "WidgetPrefs" mà ReadingActivity đã lưu
    val prefs = context.getSharedPreferences("WidgetPrefs", Context.MODE_PRIVATE)
    val bookId = prefs.getString("lastBookId", null)
    val chapterId = prefs.getString("lastChapterId", "")
    val chapterTitle = prefs.getString("lastChapterTitle", "Đọc tiếp...") ?: "Đọc tiếp..."

    val views = RemoteViews(context.packageName, R.layout.continue_reading_widget)

    if (bookId == null) {
        // Trường hợp chưa đọc sách nào
        views.setViewVisibility(R.id.layoutContent, View.GONE)
        views.setViewVisibility(R.id.layoutEmpty, View.VISIBLE)
    } else {
        // Trường hợp đã có lịch sử
        views.setViewVisibility(R.id.layoutContent, View.VISIBLE)
        views.setViewVisibility(R.id.layoutEmpty, View.GONE)

        views.setTextViewText(R.id.widgetTitle, "Đang đọc dở:")
        views.setTextViewText(R.id.widgetChapterInfo, chapterTitle)

        // Tạo Intent mở ReadingActivity
        val intent = Intent(context, ReadingActivity::class.java).apply {
            putExtra("BOOK_ID", bookId)         // Key khớp với ReadingActivity
            putExtra("CHAPTER_ID", chapterId)   // Key khớp với ReadingActivity
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Gán sự kiện click cho toàn bộ widget hoặc nút bấm
        views.setOnClickPendingIntent(R.id.widgetContainer, pendingIntent)
        views.setOnClickPendingIntent(R.id.widgetContinueBtn, pendingIntent)
    }

    appWidgetManager.updateAppWidget(appWidgetId, views)
}