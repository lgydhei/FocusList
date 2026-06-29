package com.bistu.focuslist.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bistu.focuslist.data.Repository
import com.bistu.focuslist.service.FocusTimerService
import com.bistu.focuslist.util.AlarmScheduler
import com.bistu.focuslist.util.NotificationHelper
import com.bistu.focuslist.util.Prefs
import com.bistu.focuslist.widget.TaskWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 任务提醒广播接收器（四大组件之一）。
 * 由 AlarmManager 在任务到点时唤起，弹出提醒通知。
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        val title = intent.getStringExtra(EXTRA_TASK_TITLE) ?: "你有一项任务到期了"
        if (taskId < 0) return

        when (action) {
            ACTION_REMIND -> NotificationHelper.showReminder(context, taskId, title)
            ACTION_START_FOCUS -> {
                FocusTimerService.startFocus(
                    context,
                    Prefs.getFocusMinutes(context),
                    taskId,
                    title
                )
                NotificationHelper.cancelReminder(context, taskId)
            }
            ACTION_SNOOZE -> {
                AlarmScheduler.snooze(context, taskId, title)
                NotificationHelper.cancelReminder(context, taskId)
            }
            ACTION_MARK_DONE -> markTaskDone(context, taskId)
        }
    }

    private fun markTaskDone(context: Context, taskId: Long) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = Repository.get(appContext)
                repo.getTask(taskId)?.let { task ->
                    repo.updateTask(task.copy(isDone = true))
                    AlarmScheduler.cancel(appContext, taskId)
                    NotificationHelper.cancelReminder(appContext, taskId)
                    TaskWidgetProvider.notifyRefresh(appContext)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_REMIND = "com.bistu.focuslist.action.REMIND"
        const val ACTION_START_FOCUS = "com.bistu.focuslist.action.START_FOCUS_FROM_REMINDER"
        const val ACTION_SNOOZE = "com.bistu.focuslist.action.SNOOZE_REMINDER"
        const val ACTION_MARK_DONE = "com.bistu.focuslist.action.MARK_TASK_DONE"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_TITLE = "extra_task_title"
    }
}
