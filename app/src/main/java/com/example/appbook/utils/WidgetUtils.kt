package com.example.appbook.utils

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.example.appbook.ContinueReadingWidget

object WidgetUtils {
    fun updateContinueReadingWidget(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, ContinueReadingWidget::class.java)
        val ids = appWidgetManager.getAppWidgetIds(componentName)

        if (ids.isNotEmpty()) {
            for (id in ids) {
                com.example.appbook.updateAppWidget(context, appWidgetManager, id)
            }
        }
    }
}
