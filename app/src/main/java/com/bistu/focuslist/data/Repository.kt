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

    fun observeAllSessions(): LiveData<List<FocusSession>> =
        sessionDao.observeAllSessions()

    fun observeRecentSessionsSince(since: Long, limit: Int = 30): LiveData<List<FocusSession>> =
        sessionDao.observeRecentSince(since, limit)

    fun observeSessionsSinceAsc(since: Long): LiveData<List<FocusSession>> =
        sessionDao.observeSessionsSinceAsc(since)

    fun observeCategoryStatsSince(since: Long, limit: Int = 4): LiveData<List<CategoryFocusStat>> =
        sessionDao.observeCategoryStatsSince(since, limit)

    fun observeCompletionStatsSince(since: Long): LiveData<CompletionStat> =
        sessionDao.observeCompletionStatsSince(since)

    fun observeTodayMinutes(startOfDay: Long): LiveData<Int> =
        sessionDao.observeTotalMinutesSince(startOfDay)

    fun observeTodayCount(startOfDay: Long): LiveData<Int> =
        sessionDao.observeCountSince(startOfDay)

    fun observeTotalMinutes(): LiveData<Int> =
        sessionDao.observeTotalMinutesSince(0L)

    fun observeTotalCount(): LiveData<Int> =
        sessionDao.observeCountSince(0L)

    suspend fun getSession(id: Long): FocusSession? = sessionDao.getById(id)
    suspend fun insertSession(session: FocusSession): Long = sessionDao.insert(session)
    suspend fun updateSession(session: FocusSession) = sessionDao.update(session)

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
