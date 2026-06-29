package com.bistu.focuslist.util

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bistu.focuslist.R
import com.bistu.focuslist.receiver.ReminderReceiver
import com.bistu.focuslist.ui.MainActivity

/**
 * 通知工具：创建通知渠道，发送任务提醒通知。
 * （番茄钟前台服务的常驻通知在 Service 内构建。）
 */
object NotificationHelper {

    const val CHANNEL_FOCUS = "channel_focus"
    const val CHANNEL_REMINDER = "channel_reminder"
    private const val REMINDER_NOTIF_BASE = 10000

    /** 创建通知渠道。Android 8.0+ 必须先建渠道才能发通知。 */
    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return

        val focus = NotificationChannel(
            CHANNEL_FOCUS, "专注计时", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "番茄钟计时进行中的常驻通知"
            setShowBadge(false)
        }

        val reminder = NotificationChannel(
            CHANNEL_REMINDER, "任务提醒", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "任务到达截止时间时的提醒"
            enableVibration(true)
        }

        nm.createNotificationChannel(focus)
        nm.createNotificationChannel(reminder)
    }

    /** 发送一条任务提醒通知（由广播接收器调用）。 */
    @SuppressLint("MissingPermission")
    fun showReminder(context: Context, taskId: Long, title: String) {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context,
            taskId.toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle(context.getString(R.string.notification_task_reminder))
            .setContentText(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(title))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .addAction(
                0,
                context.getString(R.string.notification_start_focus),
                focusPendingIntent(context, taskId, title)
            )
            .addAction(
                0,
                context.getString(R.string.notification_snooze),
                reminderActionIntent(context, ReminderReceiver.ACTION_SNOOZE, taskId, title, 201)
            )
            .addAction(
                0,
                context.getString(R.string.notification_mark_done),
                reminderActionIntent(context, ReminderReceiver.ACTION_MARK_DONE, taskId, title, 202)
            )
            .build()

        // 若用户未授予通知权限，notify 会被系统忽略，不会崩溃
        NotificationManagerCompat.from(context)
            .notify(REMINDER_NOTIF_BASE + taskId.toInt(), notification)
    }

    fun cancelReminder(context: Context, taskId: Long) {
        NotificationManagerCompat.from(context)
            .cancel(REMINDER_NOTIF_BASE + taskId.toInt())
    }

    private fun focusPendingIntent(context: Context, taskId: Long, title: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_START_FOCUS
            putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
            putExtra(ReminderReceiver.EXTRA_TASK_TITLE, title)
        }
        return PendingIntent.getBroadcast(
            context,
            taskId.toInt() + 1000,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun reminderActionIntent(
        context: Context,
        action: String,
        taskId: Long,
        title: String,
        requestOffset: Int
    ): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            this.action = action
            putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
            putExtra(ReminderReceiver.EXTRA_TASK_TITLE, title)
        }
        return PendingIntent.getBroadcast(
            context,
            taskId.toInt() + requestOffset,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
