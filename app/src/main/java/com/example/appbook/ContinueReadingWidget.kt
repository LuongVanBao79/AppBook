package com.example.appbook

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.appbook.activities.PdfViewActivity

/**
 * Implementation of App Widget functionality.
 */
class ContinueReadingWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // There may be multiple widgets active, so update all of them
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        // Enter relevant functionality for when the first widget is created
    }

    override fun onDisabled(context: Context) {
        // Enter relevant functionality for when the last widget is disabled
    }
}

internal fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int
) {
    val prefs = context.getSharedPreferences("ReadingPrefs", Context.MODE_PRIVATE)
    val bookId = prefs.getString("lastBookId", null)
    val title = prefs.getString("lastBookTitle", "Chưa có sách nào") ?: ""
    val page = prefs.getInt("lastBookPage", 0)
    val total = prefs.getInt("lastBookTotal", 0)

    val views = RemoteViews(context.packageName, R.layout.continue_reading_widget)

    if (bookId == null) {
        views.setTextViewText(R.id.widgetStatus, "📚 Bạn chưa đọc cuốn nào")
        views.setTextViewText(R.id.widgetTitle, "")
        views.setTextViewText(R.id.widgetPageInfo, "")
    } else {
        views.setTextViewText(R.id.widgetTitle, title)
        views.setTextViewText(R.id.widgetPageInfo, "Trang $page/$total")

        // 🟡 Intent mở lại PdfViewActivity ở đúng trang
        val intent = Intent(context, PdfViewActivity::class.java).apply {
            putExtra("bookId", bookId)
            putExtra("resumePage", page)
            putExtra("fromWidget", true)   // 🟡 Thêm flag này
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widgetContinueBtn, pendingIntent)
    }

    appWidgetManager.updateAppWidget(appWidgetId, views)
}

