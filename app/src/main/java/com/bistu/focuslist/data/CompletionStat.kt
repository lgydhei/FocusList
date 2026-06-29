package com.bistu.focuslist.data

/** 最近一段时间内专注记录的完成与复盘统计。 */
data class CompletionStat(
    val completedCount: Int,
    val incompleteCount: Int,
    val reviewedCount: Int
)
