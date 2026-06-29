package com.bistu.focuslist.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.bistu.focuslist.R
import com.bistu.focuslist.provider.TaskProvider
import com.bistu.focuslist.ui.MainActivity
import com.bistu.focuslist.util.TimeUtils

/**
 * 桌面小组件。
 *
 * 关键点：它运行时通过 ContentResolver 查询 TaskProvider（内容提供器）
 * 来获取“待办 / 已完成”数量——这是四大组件协作的真实场景，
 * 也体现了 ContentProvider 跨组件共享数据的价值。
 */
class TaskWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, TaskWidgetProvider::class.java)
            )
            ids.forEach { updateWidget(context, manager, it) }
        }
    }

    private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val data = queryWidgetData(context)

        val views = RemoteViews(context.packageName, R.layout.widget_task).apply {
            setTextViewText(R.id.widgetPendingCount, data.pending.toString())
            setTextViewText(R.id.widgetDoneCount, data.done.toString())
            setTextViewText(R.id.widgetTodayMinutes, data.todayMinutes.toString())
            setTextViewText(R.id.widgetNextTask, data.nextTaskText)
        }

        // 点击主体打开 App
        val openIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_TASKS)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPi = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widgetRoot, openPi)

        // 点击刷新按钮重新读取数据
        val refreshIntent = Intent(context, TaskWidgetProvider::class.java).apply {
            action = ACTION_REFRESH
        }
        val refreshPi = PendingIntent.getBroadcast(
            context, 1, refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widgetRefresh, refreshPi)

        manager.updateAppWidget(widgetId, views)
    }

    /** 通过内容提供器统计任务、小组件下一项与今日专注分钟 */
    private fun queryWidgetData(context: Context): WidgetData {
        var pending = 0
        var done = 0
        var nextTaskText = context.getString(R.string.widget_no_next_task)
        try {
            context.contentResolver.query(
                TaskProvider.CONTENT_URI,
                arrayOf("title", "isDone", "dueTime"),
                null,
                null,
                "isDone ASC, priority DESC, CASE WHEN dueTime IS NULL THEN 1 ELSE 0 END ASC, dueTime ASC, createdAt DESC"
            )?.use { cursor ->
                val titleIdx = cursor.getColumnIndex("title")
                val idx = cursor.getColumnIndex("isDone")
                val dueIdx = cursor.getColumnIndex("dueTime")
                var nextCaptured = false
                if (idx >= 0) {
                    while (cursor.moveToNext()) {
                        val isDone = cursor.getInt(idx) != 0
                        if (isDone) {
                            done++
                        } else {
                            pending++
                            if (!nextCaptured && titleIdx >= 0) {
                                val title = cursor.getString(titleIdx).orEmpty()
                                val dueText = if (dueIdx >= 0 && !cursor.isNull(dueIdx)) {
                                    " · ${TimeUtils.formatTime(cursor.getLong(dueIdx))}"
                                } else {
                                    ""
                                }
                                nextTaskText = context.getString(
                                    R.string.widget_next_task_fmt,
                                    title,
                                    dueText
                                )
                                nextCaptured = true
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // 查询失败时显示 0，避免小组件崩溃
        }
        return WidgetData(
            pending = pending,
            done = done,
            todayMinutes = queryTodayMinutes(context),
            nextTaskText = nextTaskText
        )
    }

    private fun queryTodayMinutes(context: Context): Int {
        return try {
            context.contentResolver.query(
                TaskProvider.TODAY_STATS_URI,
                arrayOf(TaskProvider.COL_TODAY_MINUTES),
                null,
                null,
                null
            )?.use { cursor ->
                val idx = cursor.getColumnIndex(TaskProvider.COL_TODAY_MINUTES)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getInt(idx) else 0
            } ?: 0
        } catch (_: Exception) {
            0
        }
    }

    private data class WidgetData(
        val pending: Int,
        val done: Int,
        val todayMinutes: Int,
        val nextTaskText: String
    )

    companion object {
        const val ACTION_REFRESH = "com.bistu.focuslist.ACTION_WIDGET_REFRESH"

        /** 供 App 内部在数据变化后通知小组件刷新 */
        fun notifyRefresh(context: Context) {
            val intent = Intent(context, TaskWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            context.sendBroadcast(intent)
        }
    }
}
