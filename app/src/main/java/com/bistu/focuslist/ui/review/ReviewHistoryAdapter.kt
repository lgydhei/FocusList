package com.bistu.focuslist.ui.review

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bistu.focuslist.R
import com.bistu.focuslist.data.FocusSession
import com.bistu.focuslist.databinding.ItemReviewHistoryBinding
import com.bistu.focuslist.util.TimeUtils

class ReviewHistoryAdapter(
    private val onItemClick: (FocusSession) -> Unit
) : ListAdapter<FocusSession, ReviewHistoryAdapter.ReviewViewHolder>(DIFF) {

    inner class ReviewViewHolder(val binding: ItemReviewHistoryBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReviewViewHolder {
        val binding = ItemReviewHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ReviewViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ReviewViewHolder, position: Int) {
        val session = getItem(position)
        val ctx = holder.itemView.context
        with(holder.binding) {
            textReviewTitle.text = session.taskTitle.ifBlank { ctx.getString(R.string.free_focus) }
            textReviewMeta.text = ctx.getString(
                R.string.review_history_meta_fmt,
                TimeUtils.formatDateTime(session.endTime),
                session.durationMinutes,
                session.focusMode,
                if (session.completed) {
                    ctx.getString(R.string.review_status_completed)
                } else {
                    ctx.getString(R.string.session_incomplete)
                }
            )
            textReviewMood.text = ctx.getString(
                R.string.review_history_mood_fmt,
                session.reviewMood.ifBlank { ctx.getString(R.string.review_not_filled) }
            )
            textReviewReason.text = ctx.getString(
                R.string.review_history_reason_fmt,
                session.interruptionReason.ifBlank { ctx.getString(R.string.review_not_filled) }
            )
            textReviewNotes.text = ctx.getString(
                R.string.review_history_notes_fmt,
                session.reviewNotes.ifBlank { ctx.getString(R.string.review_not_filled) }
            )
            root.setOnClickListener { onItemClick(session) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<FocusSession>() {
            override fun areItemsTheSame(oldItem: FocusSession, newItem: FocusSession) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: FocusSession, newItem: FocusSession) =
                oldItem == newItem
        }
    }
}
