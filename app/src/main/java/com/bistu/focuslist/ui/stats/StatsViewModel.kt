package com.bistu.focuslist.ui.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.bistu.focuslist.data.CategoryStat
import com.bistu.focuslist.data.DailyStat
import com.bistu.focuslist.data.FocusSession
import com.bistu.focuslist.data.Repository
import com.bistu.focuslist.util.TimeUtils
import kotlinx.coroutines.launch

/**
 * 统计页 ViewModel。
 * 汇总今日 / 累计的专注时长与番茄个数，最近记录，以及7天图表、连续天数、分类统计。
 */
class StatsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repository.get(app)
    private val startOfToday = TimeUtils.startOfToday()
    private val startOfRecentSessions = TimeUtils.startOfRecentDays(RECENT_SESSION_DAYS)

    val todayMinutes: LiveData<Int> = repo.observeTodayMinutes(startOfToday)
    val todayCount: LiveData<Int> = repo.observeTodayCount(startOfToday)
    val totalMinutes: LiveData<Int> = repo.observeTotalMinutes()
    val totalCount: LiveData<Int> = repo.observeTotalCount()
    val pendingCount: LiveData<Int> = repo.observePendingCount()
    val recentSessions: LiveData<List<FocusSession>> =
        repo.observeRecentSessionsSince(startOfRecentSessions, RECENT_SESSION_LIMIT)

    /** 最近7天按天聚合的统计 */
    val dailyStats: LiveData<List<DailyStat>> = repo.observeDailyStats(startOfRecentSessions)

    /** 按分类统计的专注时长 */
    val categoryStats: LiveData<List<CategoryStat>> = repo.observeCategoryStats(0L)

    /** 连续专注天数 */
    private val _streakDays = MutableLiveData(0)
    val streakDays: LiveData<Int> = _streakDays

    init {
        refreshStreak()
    }

    fun refreshStreak() {
        viewModelScope.launch {
            _streakDays.value = repo.getStreakDays()
        }
    }

    companion object {
        private const val RECENT_SESSION_DAYS = 7
        private const val RECENT_SESSION_LIMIT = 30
    }
}
