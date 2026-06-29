package com.bistu.focuslist.ui.stats

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.bistu.focuslist.R
import com.bistu.focuslist.data.DailyStat
import com.bistu.focuslist.data.CategoryStat
import com.bistu.focuslist.databinding.FragmentStatsBinding
import com.bistu.focuslist.provider.TaskProvider
import com.google.android.material.snackbar.Snackbar

/**
 * 统计页面。
 * 展示今日 / 累计专注数据、连续天数、7天简易柱状图、分类统计与最近记录；
 * 并提供一个"内容提供器查询"演示按钮，直观体现 ContentProvider 的使用。
 */
class StatsFragment : Fragment() {

    private var _binding: FragmentStatsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: StatsViewModel by viewModels()
    private lateinit var adapter: SessionAdapter

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
        viewModel.streakDays.observe(viewLifecycleOwner) {
            binding.textStreakDays!!.text = it.toString()
        }

        // 7天图表
        viewModel.dailyStats.observe(viewLifecycleOwner) { stats ->
            renderDailyChart(stats)
        }

        // 分类统计
        viewModel.categoryStats.observe(viewLifecycleOwner) { stats ->
            renderCategoryStats(stats)
        }

        viewModel.recentSessions.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            binding.textNoSession.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }

        binding.btnProviderDemo.setOnClickListener { queryViaProvider() }
    }

    /** 简易7天柱状图：用 View 高度比例模拟 */
    private fun renderDailyChart(stats: List<DailyStat>) {
        if (stats.isEmpty()) {
            binding.layoutDailyChart!!.visibility = View.GONE
            return
        }
        binding.layoutDailyChart!!.visibility = View.VISIBLE
        val maxMinutes = stats.maxOf { it.minutes }.coerceAtLeast(1)

        val bars = listOf(
            binding.barDay1!! to binding.textBarDay1!!,
            binding.barDay2!! to binding.textBarDay2!!,
            binding.barDay3!! to binding.textBarDay3!!,
            binding.barDay4!! to binding.textBarDay4!!,
            binding.barDay5!! to binding.textBarDay5!!,
            binding.barDay6!! to binding.textBarDay6!!,
            binding.barDay7!! to binding.textBarDay7!!
        )

        val msPerDay = 86400000L
        val todayStart = (System.currentTimeMillis() / msPerDay) * msPerDay
        val density = resources.displayMetrics.density
        val minH = (16 * density).toInt()
        val maxH = (96 * density).toInt()

        for (i in 0 until 7) {
            val dayStart = todayStart - (6 - i) * msPerDay
            val stat = stats.find { it.dayStart == dayStart }
            val minutes = stat?.minutes ?: 0
            val weight = if (maxMinutes > 0) (minutes.toFloat() / maxMinutes).coerceAtMost(1f) else 0f

            val lp = bars[i].first.layoutParams
            lp.height = (minH + (weight * (maxH - minH))).toInt()
            bars[i].first.layoutParams = lp
            bars[i].first.requestLayout()
            bars[i].second.text = if (minutes > 0) "${minutes}分" else ""
        }
    }

    private fun renderCategoryStats(stats: List<CategoryStat>) {
        if (stats.isEmpty()) {
            binding.layoutCategoryStats!!.visibility = View.GONE
            return
        }
        binding.layoutCategoryStats!!.visibility = View.VISIBLE
        val sb = StringBuilder()
        for (s in stats) {
            sb.append("${s.category}: ${s.totalMinutes}分钟 (${s.sessionCount}次)\n")
        }
        binding.textCategoryStats!!.text = sb.toString().trimEnd()
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
