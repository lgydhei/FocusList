package com.bistu.focuslist.ui.review

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.bistu.focuslist.data.Repository

class ReviewHistoryViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repository.get(app)

    val sessions = repo.observeAllSessions()
}
