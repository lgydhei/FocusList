package com.bistu.focuslist.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.bistu.focuslist.R
import com.bistu.focuslist.data.FocusSession
import com.bistu.focuslist.data.Repository
import com.bistu.focuslist.ui.MainActivity
import com.bistu.focuslist.util.NotificationHelper
import com.bistu.focuslist.util.Prefs
import com.bistu.focuslist.util.SoundManager
import com.bistu.focuslist.util.TimeUtils
import com.bistu.focuslist.widget.TaskWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 番茄钟前台服务（四大组件之一：服务）。
 *
 * 为什么用前台服务：番茄钟需要在用户切到其他 App 或锁屏后继续计时，
 * 普通后台执行会被系统限制甚至杀死，因此使用带常驻通知的前台服务。
 *
 * 同时演示多媒体 API：可在专注期间循环播放白噪音（MediaPlayer）。
 *
 * 服务与界面同进程，使用 companion 中的 LiveData 向界面发布倒计时状态。
 */
class FocusTimerService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var totalSeconds = 0
    private var remainingSeconds = 0
    private var running = false
    private var paused = false
    private var taskId: Long? = null
    private var taskTitle: String = ""
    private var startTimeMillis = 0L

    private var ambientPlayer: MediaPlayer? = null

    /** 每秒触发一次的计时器 */
    private val ticker = object : Runnable {
        override fun run() {
            if (running && !paused) {
                remainingSeconds -= 1
                if (remainingSeconds <= 0) {
                    remainingSeconds = 0
                    onFinished()
                    return
                }
                publish()
                updateNotification()
            }
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_PAUSE -> handlePause()
            ACTION_RESUME -> handleResume()
            ACTION_STOP -> handleStop(userCancelled = true)
        }
        // 计时被杀死后不自动重启（避免无意义的空状态重启）
        return START_NOT_STICKY
    }

    private fun handleStart(intent: Intent) {
        val minutes = intent.getIntExtra(EXTRA_MINUTES, Prefs.DEFAULT_FOCUS_MINUTES)
        val id = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        taskId = if (id < 0) null else id
        taskTitle = intent.getStringExtra(EXTRA_TASK_TITLE) ?: ""
        totalSeconds = minutes * 60
        remainingSeconds = totalSeconds
        running = true
        paused = false
        startTimeMillis = System.currentTimeMillis()

        startForegroundCompat(buildNotification())
        startAmbientIfNeeded()
        publish()

        handler.removeCallbacks(ticker)
        handler.postDelayed(ticker, 1000L)
    }

    private fun handlePause() {
        if (!running || paused) return
        paused = true
        stopAmbient()
        publish()
        updateNotification()
    }

    private fun handleResume() {
        if (!running || !paused) return
        paused = false
        startAmbientIfNeeded()
        publish()
        updateNotification()
    }

    private fun handleStop(userCancelled: Boolean) {
        if (running) {
            recordSession(completed = !userCancelled)
        }
        finishAndStop()
    }

    /** 倒计时自然归零 */
    private fun onFinished() {
        recordSession(completed = true)
        incrementTaskPomodoro()
        SoundManager.playChime(this)
        SoundManager.vibrate(this)
        showCompletionNotification()
        finishAndStop()
    }

    private fun finishAndStop() {
        handler.removeCallbacks(ticker)
        stopAmbient()
        running = false
        paused = false
        remainingSeconds = 0
        totalSeconds = 0
        publish()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ---------------- 数据记录 ----------------

    private fun recordSession(completed: Boolean) {
        val elapsed = (totalSeconds - remainingSeconds).coerceAtLeast(0)
        val minutes = elapsed / 60
        if (minutes <= 0 && !completed) return // 放弃且不足一分钟则不记录
        val session = FocusSession(
            taskId = taskId,
            taskTitle = taskTitle,
            durationMinutes = if (completed) totalSeconds / 60 else minutes,
            startTime = startTimeMillis,
            endTime = System.currentTimeMillis(),
            completed = completed
        )
        ioScope.launch {
            Repository.get(applicationContext).insertSession(session)
            TaskWidgetProvider.notifyRefresh(applicationContext)
        }
    }

    private fun incrementTaskPomodoro() {
        val id = taskId ?: return
        ioScope.launch {
            val repo = Repository.get(applicationContext)
            repo.getTask(id)?.let { repo.updateTask(it.copy(pomodoroCount = it.pomodoroCount + 1)) }
        }
    }

    // ---------------- 多媒体：白噪音 ----------------

    private fun startAmbientIfNeeded() {
        if (!Prefs.isAmbientEnabled(this)) return
        val player = ambientPlayer
        if (player != null) {
            runCatching { player.start() }
            return
        }
        try {
            ambientPlayer = MediaPlayer.create(this, R.raw.ambient)?.apply {
                isLooping = true
                setVolume(0.5f, 0.5f)
                start()
            }
        } catch (_: Exception) {
            ambientPlayer = null
        }
    }

    private fun stopAmbient() {
        runCatching { ambientPlayer?.let { if (it.isPlaying) it.pause() } }
    }

    private fun releaseAmbient() {
        runCatching { ambientPlayer?.release() }
        ambientPlayer = null
    }

    // ---------------- 通知 ----------------

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_FOCUS)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPi = PendingIntent.getActivity(
            this, 100, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (paused) "专注已暂停" else "专注进行中"
        val text = buildString {
            append(TimeUtils.formatMmSs(remainingSeconds))
            if (taskTitle.isNotBlank()) append("  ·  ").append(taskTitle)
        }

        val builder = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_FOCUS)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentPi)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (paused) {
            builder.addAction(0, "继续", servicePendingIntent(ACTION_RESUME, 101))
        } else {
            builder.addAction(0, "暂停", servicePendingIntent(ACTION_PAUSE, 102))
        }
        builder.addAction(0, "结束", servicePendingIntent(ACTION_STOP, 103))

        return builder.build()
    }

    private fun updateNotification() {
        ContextCompat.getSystemService(this, NotificationManager::class.java)
            ?.notify(NOTIF_ID, buildNotification())
    }

    private fun showCompletionNotification() {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_FOCUS)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            this, 104, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val msg = if (taskTitle.isNotBlank()) {
            "「$taskTitle」完成了一个番茄钟，休息一下吧！"
        } else {
            "完成了一个番茄钟，休息一下吧！"
        }
        val notification = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle("专注完成")
            .setContentText(msg)
            .setStyle(NotificationCompat.BigTextStyle().bigText(msg))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        ContextCompat.getSystemService(this, NotificationManager::class.java)
            ?.notify(NOTIF_ID + 1, notification)
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, FocusTimerService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            this, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun startForegroundCompat(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIF_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIF_ID, notification)
            }
        } catch (_: Exception) {
            // 极端情况下兜底，避免崩溃
            runCatching { startForeground(NOTIF_ID, notification) }
        }
    }

    private fun publish() {
        _state.value = FocusUiState(
            running = running,
            paused = paused,
            totalSeconds = totalSeconds,
            remainingSeconds = remainingSeconds,
            taskTitle = taskTitle,
            taskId = taskId
        )
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        releaseAmbient()
        super.onDestroy()
    }

    companion object {
        private const val NOTIF_ID = 2001

        const val ACTION_START = "com.bistu.focuslist.action.START_FOCUS"
        const val ACTION_PAUSE = "com.bistu.focuslist.action.PAUSE_FOCUS"
        const val ACTION_RESUME = "com.bistu.focuslist.action.RESUME_FOCUS"
        const val ACTION_STOP = "com.bistu.focuslist.action.STOP_FOCUS"

        const val EXTRA_MINUTES = "extra_minutes"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_TITLE = "extra_task_title"

        private val _state = MutableLiveData(FocusUiState())

        /** 界面观察此 LiveData 获取实时倒计时状态 */
        val state: LiveData<FocusUiState> = _state

        /** 启动一次专注（前台服务） */
        fun startFocus(context: Context, minutes: Int, taskId: Long?, taskTitle: String) {
            val intent = Intent(context, FocusTimerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_MINUTES, minutes)
                putExtra(EXTRA_TASK_ID, taskId ?: -1L)
                putExtra(EXTRA_TASK_TITLE, taskTitle)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        /** 向服务发送暂停 / 继续 / 结束指令 */
        fun sendAction(context: Context, action: String) {
            val intent = Intent(context, FocusTimerService::class.java).apply {
                this.action = action
            }
            context.startService(intent)
        }
    }
}
