package com.bistu.focuslist.ui.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import com.bistu.focuslist.data.FocusSession
import com.bistu.focuslist.data.Repository
import com.bistu.focuslist.util.TimeUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 统计页 ViewModel。
 * 汇总今日 / 累计、7 天趋势、连续专注、分类分布与复盘概览。
 */
class StatsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repository.get(app)
    private val startOfToday = TimeUtils.startOfToday()
    private val startOfRecentSessions = TimeUtils.startOfRecentDays(RECENT_SESSION_DAYS)
    private val startOfTrend = TimeUtils.startOfRecentDays(TREND_DAYS)
    private val startOfStreakLookback = TimeUtils.startOfRecentDays(STREAK_LOOKBACK_DAYS)

    val todayMinutes: LiveData<Int> = repo.observeTodayMinutes(startOfToday)
    val todayCount: LiveData<Int> = repo.observeTodayCount(startOfToday)
    val totalMinutes: LiveData<Int> = repo.observeTotalMinutes()
    val totalCount: LiveData<Int> = repo.observeTotalCount()
    val pendingCount: LiveData<Int> = repo.observePendingCount()
    val recentSessions: LiveData<List<FocusSession>> =
        repo.observeRecentSessionsSince(startOfRecentSessions, RECENT_SESSION_LIMIT)
    val weeklyTrend: LiveData<List<DailyFocusUi>> =
        repo.observeSessionsSinceAsc(startOfTrend).map { buildWeeklyTrend(it) }
    val streakDays: LiveData<Int> =
        repo.observeSessionsSinceAsc(startOfStreakLookback).map { calculateStreakDays(it) }
    val categoryStats = repo.observeCategoryStatsSince(startOfRecentSessions, CATEGORY_LIMIT)
    val totalCategoryStats = repo.observeCategoryStatsSince(0L, CATEGORY_LIMIT)
    val completionStats = repo.observeCompletionStatsSince(startOfRecentSessions)

    private fun buildWeeklyTrend(sessions: List<FocusSession>): List<DailyFocusUi> {
        val totalsByDay = sessions.groupBy { startOfDay(it.startTime) }
            .mapValues { entry -> entry.value.sumOf { it.durationMinutes } }
        val labelFormat = SimpleDateFormat("M/d", Locale.getDefault())

        return (0 until TREND_DAYS).map { offset ->
            val day = Calendar.getInstance().apply {
                timeInMillis = startOfTrend
                add(Calendar.DAY_OF_YEAR, offset)
            }
            DailyFocusUi(
                label = labelFormat.format(day.time),
                minutes = totalsByDay[day.timeInMillis] ?: 0
            )
        }
    }

    private fun calculateStreakDays(sessions: List<FocusSession>): Int {
        val activeDays = sessions.map { startOfDay(it.startTime) }.toSet()
        var cursor = startOfToday
        var count = 0
        while (activeDays.contains(cursor)) {
            count++
            cursor = Calendar.getInstance().apply {
                timeInMillis = cursor
                add(Calendar.DAY_OF_YEAR, -1)
            }.timeInMillis
        }
        return count
    }

    private fun startOfDay(millis: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    companion object {
        private const val RECENT_SESSION_DAYS = 7
        private const val RECENT_SESSION_LIMIT = 30
        private const val TREND_DAYS = 7
        private const val STREAK_LOOKBACK_DAYS = 30
        private const val CATEGORY_LIMIT = 4
    }
}
