package com.bistu.focuslist.data

/** 最近一段时间内，不同任务分类对应的专注分钟数。 */
data class CategoryFocusStat(
    val category: String,
    val minutes: Int
)
