package com.bistu.focuslist.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.bistu.focuslist.data.Repository
import com.bistu.focuslist.data.Task
import com.bistu.focuslist.receiver.ReminderReceiver

/**
 * 闹钟调度器：使用 AlarmManager 在任务到点时触发广播。
 * 采用 setAndAllowWhileIdle（非精确闹钟），无需额外权限，且能在低电耗模式下触发。
 */
object AlarmScheduler {

    private const val SNOOZE_MINUTES = 10

    fun schedule(context: Context, task: Task) {
        val due = task.dueTime ?: return
        if (task.isDone) return
        if (due <= System.currentTimeMillis()) return // 已过期不再排程

        scheduleAt(context, task.id, task.title, due)
    }

    /** 不修改任务截止时间，仅临时追加一次稍后提醒。 */
    fun snooze(context: Context, taskId: Long, title: String) {
        val triggerAt = System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L
        scheduleAt(context, taskId, title, triggerAt)
    }

    private fun scheduleAt(context: Context, taskId: Long, title: String, triggerAt: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            buildPendingIntent(context, taskId, title)
        )
    }

    fun cancel(context: Context, taskId: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(buildPendingIntent(context, taskId, ""))
    }

    /** 开机后重排所有未完成且设置了提醒的任务（由 BootReceiver 调用）。 */
    suspend fun rescheduleAll(context: Context) {
        val tasks = Repository.get(context).getTasksWithReminder()
        tasks.forEach { schedule(context, it) }
    }

    private fun buildPendingIntent(context: Context, taskId: Long, title: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_REMIND
            putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
            putExtra(ReminderReceiver.EXTRA_TASK_TITLE, title)
        }
        return PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
