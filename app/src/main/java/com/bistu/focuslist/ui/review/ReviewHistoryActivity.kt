package com.bistu.focuslist.ui.review

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.bistu.focuslist.R
import com.bistu.focuslist.databinding.ActivityReviewHistoryBinding

class ReviewHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReviewHistoryBinding
    private val viewModel: ReviewHistoryViewModel by viewModels()
    private lateinit var adapter: ReviewHistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReviewHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.review_history)

        adapter = ReviewHistoryAdapter { session ->
            startActivity(
                Intent(this, FocusReviewActivity::class.java).putExtra(
                    FocusReviewActivity.EXTRA_SESSION_ID,
                    session.id
                )
            )
        }
        binding.recyclerReviewHistory.layoutManager = LinearLayoutManager(this)
        binding.recyclerReviewHistory.adapter = adapter

        viewModel.sessions.observe(this) { list ->
            adapter.submitList(list)
            binding.textNoReviewHistory.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
