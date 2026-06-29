package com.bistu.focuslist.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/** 专注记录 DAO。 */
@Dao
interface FocusSessionDao {

    @Insert
    suspend fun insert(session: FocusSession): Long

    @Query("SELECT * FROM focus_sessions ORDER BY endTime DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): LiveData<List<FocusSession>>

    @Query("SELECT * FROM focus_sessions WHERE endTime >= :since ORDER BY endTime DESC, id DESC LIMIT :limit")
    fun observeRecentSince(since: Long, limit: Int): LiveData<List<FocusSession>>

    /** 自某时间点以来的累计专注分钟数 */
    @Query("SELECT COALESCE(SUM(durationMinutes), 0) FROM focus_sessions WHERE startTime >= :since")
    fun observeTotalMinutesSince(since: Long): LiveData<Int>

    /** 自某时间点以来专注过的番茄个数（包含未完成但有记录的） */
    @Query("SELECT COUNT(*) FROM focus_sessions WHERE startTime >= :since")
    fun observeCountSince(since: Long): LiveData<Int>

    /** 按天聚合的专注分钟数（用于7天柱状图），返回 (日期字符串, 分钟数) */
    @Query(
        """
        SELECT (startTime / 86400000) * 86400000 AS dayStart,
               COALESCE(SUM(durationMinutes), 0) AS minutes,
               COUNT(*) AS sessions
        FROM focus_sessions
        WHERE startTime >= :since
        GROUP BY dayStart
        ORDER BY dayStart ASC
        """
    )
    fun observeDailyStats(since: Long): LiveData<List<DailyStat>>

    /** 连续专注天数（最近一次专注日期开始倒推） */
    @Query(
        """
        SELECT (startTime / 86400000) * 86400000 AS dayStart
        FROM focus_sessions
        GROUP BY dayStart
        ORDER BY dayStart DESC
        """
    )
    suspend fun getDistinctFocusDays(): List<Long>

    /** 按分类统计专注时长（通过关联任务表的 category 字段） */
    @Query(
        """
        SELECT COALESCE(t.category, '未分类') AS category,
               COALESCE(SUM(fs.durationMinutes), 0) AS totalMinutes,
               COUNT(*) AS sessionCount
        FROM focus_sessions fs
        LEFT JOIN tasks t ON fs.taskId = t.id
        WHERE fs.startTime >= :since
        GROUP BY t.category
        ORDER BY totalMinutes DESC
        """
    )
    fun observeCategoryStats(since: Long): LiveData<List<CategoryStat>>
}

/** 按天聚合的统计结果 */
data class DailyStat(
    val dayStart: Long,
    val minutes: Int,
    val sessions: Int
)

/** 按分类统计的专注结果 */
data class CategoryStat(
    val category: String,
    val totalMinutes: Int,
    val sessionCount: Int
)
