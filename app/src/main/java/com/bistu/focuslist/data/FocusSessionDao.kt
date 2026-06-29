package com.bistu.focuslist.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

/** 专注记录 DAO。 */
@Dao
interface FocusSessionDao {

    @Insert
    suspend fun insert(session: FocusSession): Long

    @Update
    suspend fun update(session: FocusSession)

    @Query("SELECT * FROM focus_sessions WHERE id = :id")
    suspend fun getById(id: Long): FocusSession?

    @Query("SELECT * FROM focus_sessions ORDER BY endTime DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): LiveData<List<FocusSession>>

    @Query("SELECT * FROM focus_sessions ORDER BY endTime DESC, id DESC")
    fun observeAllSessions(): LiveData<List<FocusSession>>

    @Query("SELECT * FROM focus_sessions WHERE endTime >= :since ORDER BY endTime DESC, id DESC LIMIT :limit")
    fun observeRecentSince(since: Long, limit: Int): LiveData<List<FocusSession>>

    @Query("SELECT * FROM focus_sessions WHERE startTime >= :since ORDER BY startTime ASC, id ASC")
    fun observeSessionsSinceAsc(since: Long): LiveData<List<FocusSession>>

    @Query(
        """
        SELECT COALESCE(NULLIF(tasks.category, ''), '自由专注') AS category,
               COALESCE(SUM(focus_sessions.durationMinutes), 0) AS minutes
        FROM focus_sessions
        LEFT JOIN tasks ON focus_sessions.taskId = tasks.id
        WHERE focus_sessions.startTime >= :since
        GROUP BY COALESCE(NULLIF(tasks.category, ''), '自由专注')
        ORDER BY minutes DESC
        LIMIT :limit
        """
    )
    fun observeCategoryStatsSince(since: Long, limit: Int): LiveData<List<CategoryFocusStat>>

    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN completed = 1 THEN 1 ELSE 0 END), 0) AS completedCount,
               COALESCE(SUM(CASE WHEN completed = 0 THEN 1 ELSE 0 END), 0) AS incompleteCount,
               COALESCE(SUM(CASE WHEN reviewMood != '' OR reviewNotes != '' OR interruptionReason != '' THEN 1 ELSE 0 END), 0) AS reviewedCount
        FROM focus_sessions
        WHERE startTime >= :since
        """
    )
    fun observeCompletionStatsSince(since: Long): LiveData<CompletionStat>

    /** 自某时间点以来的累计专注分钟数 */
    @Query("SELECT COALESCE(SUM(durationMinutes), 0) FROM focus_sessions WHERE startTime >= :since")
    fun observeTotalMinutesSince(since: Long): LiveData<Int>

    /** 自某时间点以来专注过的番茄个数（包含未完成但有记录的） */
    @Query("SELECT COUNT(*) FROM focus_sessions WHERE startTime >= :since")
    fun observeCountSince(since: Long): LiveData<Int>
}
