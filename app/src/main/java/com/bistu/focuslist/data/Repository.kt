package com.bistu.focuslist.data

import android.content.Context
import androidx.lifecycle.LiveData

/**
 * 数据仓库：界面层（ViewModel）访问数据的统一入口。
 * 屏蔽底层 Room / DAO 细节，符合“单一数据源”的分层架构思想。
 */
class Repository private constructor(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val taskDao = db.taskDao()
    private val sessionDao = db.focusSessionDao()

    // ---------------- 任务 ----------------
    fun observeAllTasks(): LiveData<List<Task>> = taskDao.observeAll()
    fun observePendingTasks(): LiveData<List<Task>> = taskDao.observePending()
    fun observePendingCount(): LiveData<Int> = taskDao.observePendingCount()
    fun observeFilteredTasks(
        query: String = "",
        category: String = "",
        priority: Int = -1,
        dueOnly: Boolean = false
    ): LiveData<List<Task>> = taskDao.observeFiltered(query, category, priority, dueOnly)

    suspend fun getTask(id: Long): Task? = taskDao.getById(id)
    suspend fun getTasksWithReminder(): List<Task> = taskDao.getTasksWithReminder()
    suspend fun insertTask(task: Task): Long = taskDao.insert(task)
    suspend fun insertTasks(tasks: List<Task>): List<Long> = taskDao.insertAll(tasks)
    suspend fun updateTask(task: Task) = taskDao.update(task)
    suspend fun deleteTask(task: Task) = taskDao.delete(task)
    suspend fun clearCompletedTasks() = taskDao.clearCompleted()

    // ---------------- 专注记录 ----------------
    fun observeRecentSessions(limit: Int = 20): LiveData<List<FocusSession>> =
        sessionDao.observeRecent(limit)

    fun observeRecentSessionsSince(since: Long, limit: Int = 30): LiveData<List<FocusSession>> =
        sessionDao.observeRecentSince(since, limit)

    fun observeTodayMinutes(startOfDay: Long): LiveData<Int> =
        sessionDao.observeTotalMinutesSince(startOfDay)

    fun observeTodayCount(startOfDay: Long): LiveData<Int> =
        sessionDao.observeCountSince(startOfDay)

    fun observeTotalMinutes(): LiveData<Int> =
        sessionDao.observeTotalMinutesSince(0L)

    fun observeTotalCount(): LiveData<Int> =
        sessionDao.observeCountSince(0L)

    /** 按天聚合的专注统计（用于7天图表） */
    fun observeDailyStats(since: Long): LiveData<List<DailyStat>> =
        sessionDao.observeDailyStats(since)

    /** 按分类统计专注时长 */
    fun observeCategoryStats(since: Long): LiveData<List<CategoryStat>> =
        sessionDao.observeCategoryStats(since)

    /** 计算连续专注天数 */
    suspend fun getStreakDays(): Int {
        val days = sessionDao.getDistinctFocusDays()
        if (days.isEmpty()) return 0
        val msPerDay = 86400000L
        var streak = 1
        for (i in 1 until days.size) {
            if (days[i - 1] - days[i] == msPerDay) {
                streak++
            } else {
                break
            }
        }
        // 检查最近一天是否是今天或昨天
        val todayStart = (System.currentTimeMillis() / msPerDay) * msPerDay
        val latestDay = days.firstOrNull() ?: return 0
        if (latestDay < todayStart - msPerDay) return 0
        return streak
    }

    suspend fun insertSession(session: FocusSession): Long = sessionDao.insert(session)

    companion object {
        @Volatile
        private var INSTANCE: Repository? = null

        fun get(context: Context): Repository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Repository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
