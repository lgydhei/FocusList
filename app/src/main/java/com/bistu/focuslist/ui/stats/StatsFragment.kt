package com.bistu.focuslist.ui.stats

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.bistu.focuslist.R
import com.bistu.focuslist.data.CategoryFocusStat
import com.bistu.focuslist.data.CompletionStat
import com.bistu.focuslist.databinding.FragmentStatsBinding
import com.bistu.focuslist.provider.TaskProvider
import com.bistu.focuslist.ui.review.ReviewHistoryActivity
import com.google.android.material.snackbar.Snackbar

/**
 * 统计页面。
 * 展示今日 / 累计专注数据与最近记录；
 * 并提供一个“内容提供器查询”演示按钮，直观体现 ContentProvider 的使用。
 */
class StatsFragment : Fragment() {

    private var _binding: FragmentStatsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: StatsViewModel by viewModels()
    private lateinit var adapter: SessionAdapter
    private var recentCategoryStats: List<CategoryFocusStat> = emptyList()
    private var totalCategoryStats: List<CategoryFocusStat> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = SessionAdapter()
        binding.recyclerSessions.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerSessions.adapter = adapter
        binding.recyclerSessions.isNestedScrollingEnabled = false

        viewModel.todayMinutes.observe(viewLifecycleOwner) {
            binding.textTodayMinutes.text = it.toString()
        }
        viewModel.todayCount.observe(viewLifecycleOwner) {
            binding.textTodayCount.text = it.toString()
        }
        viewModel.totalMinutes.observe(viewLifecycleOwner) {
            binding.textTotalMinutes.text = it.toString()
        }
        viewModel.totalCount.observe(viewLifecycleOwner) {
            binding.textTotalCount.text = it.toString()
        }
        viewModel.pendingCount.observe(viewLifecycleOwner) {
            binding.textPendingCount.text = it.toString()
        }
        viewModel.recentSessions.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            binding.textNoSession.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
        viewModel.weeklyTrend.observe(viewLifecycleOwner) {
            binding.weeklyChart.setData(it)
        }
        viewModel.streakDays.observe(viewLifecycleOwner) {
            binding.textStreakDays.text = getString(R.string.stat_streak_days_fmt, it)
        }
        viewModel.categoryStats.observe(viewLifecycleOwner) {
            recentCategoryStats = it
            renderCategoryStats()
        }
        viewModel.totalCategoryStats.observe(viewLifecycleOwner) {
            totalCategoryStats = it
            renderCategoryStats()
        }
        viewModel.completionStats.observe(viewLifecycleOwner) {
            binding.textCompletionSummary.text = formatCompletionStat(it)
        }

        binding.cardCompletionSummary.setOnClickListener {
            startActivity(Intent(requireContext(), ReviewHistoryActivity::class.java))
        }
        binding.btnProviderDemo.setOnClickListener { queryViaProvider() }
    }

    private fun renderCategoryStats() {
        binding.textCategorySummary.text = formatCategoryStats(
            recentCategoryStats,
            totalCategoryStats
        )
    }

    private fun formatCategoryStats(
        recentStats: List<CategoryFocusStat>,
        totalStats: List<CategoryFocusStat>
    ): String {
        if (recentStats.isEmpty() && totalStats.isEmpty()) return getString(R.string.stat_no_category)
        return buildString {
            append(getString(R.string.stat_category_recent))
            append("：")
            append(formatCategoryBlock(recentStats))
            append('\n')
            append(getString(R.string.stat_category_total))
            append("：")
            append(formatCategoryBlock(totalStats))
        }
    }

    private fun formatCategoryBlock(stats: List<CategoryFocusStat>): String {
        if (stats.isEmpty()) return getString(R.string.stat_no_category)
        return stats.joinToString(separator = " / ") {
            getString(R.string.stat_category_line_fmt, it.category, it.minutes)
        }
    }

    private fun formatCompletionStat(stat: CompletionStat): String {
        return getString(
            R.string.stat_completion_fmt,
            stat.completedCount,
            stat.incompleteCount,
            stat.reviewedCount
        )
    }

    /** 通过 ContentResolver 调用 TaskProvider 统计任务数量（演示内容提供器）。 */
    private fun queryViaProvider() {
        var total = 0
        var pending = 0
        var done = 0
        try {
            requireContext().contentResolver.query(
                TaskProvider.CONTENT_URI, arrayOf("isDone"), null, null, null
            )?.use { cursor ->
                val idx = cursor.getColumnIndex("isDone")
                while (cursor.moveToNext()) {
                    total++
                    if (idx >= 0 && cursor.getInt(idx) == 0) pending++ else done++
                }
            }
            val msg = getString(R.string.provider_demo_result, total, pending, done)
            Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
        } catch (e: Exception) {
            Snackbar.make(binding.root, "查询失败：${e.message}", Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
