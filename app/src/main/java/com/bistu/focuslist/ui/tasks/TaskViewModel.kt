package com.bistu.focuslist.ui.tasks

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.bistu.focuslist.data.Repository
import com.bistu.focuslist.data.Task
import com.bistu.focuslist.util.AlarmScheduler
import com.bistu.focuslist.widget.TaskWidgetProvider
import kotlinx.coroutines.launch

/**
 * 任务列表 ViewModel。
 * 负责任务的增删改查、搜索筛选，并在数据变化后同步刷新闹钟与桌面小组件。
 */
class TaskViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repository.get(app)

    // 筛选状态
    private val _searchQuery = MutableLiveData("")
    private val _filterCategory = MutableLiveData("")
    private val _filterPriority = MutableLiveData(-1)
    private val _filterDueOnly = MutableLiveData(false)

    // 使用 MediatorLiveData 代替嵌套 switchMap，避免 LiveData<LiveData<...>> 类型问题
    private val _tasks = MediatorLiveData<List<Task>>()
    val tasks: LiveData<List<Task>> = _tasks

    private var currentSource: LiveData<List<Task>>? = null

    private fun refreshTasks() {
        currentSource?.let { _tasks.removeSource(it) }
        val source = repo.observeFilteredTasks(
            query = _searchQuery.value ?: "",
            category = _filterCategory.value ?: "",
            priority = _filterPriority.value ?: -1,
            dueOnly = _filterDueOnly.value ?: false
        )
        _tasks.addSource(source) { _tasks.value = it }
        currentSource = source
    }

    init {
        // 监听所有筛选状态变化，统一刷新数据源
        listOf(_searchQuery, _filterCategory, _filterPriority, _filterDueOnly).forEach { liveData ->
            liveData.observeForever { refreshTasks() }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilterCategory(category: String) {
        _filterCategory.value = category
    }

    fun setFilterPriority(priority: Int) {
        _filterPriority.value = priority
    }

    fun setFilterDueOnly(dueOnly: Boolean) {
        _filterDueOnly.value = dueOnly
    }

    fun clearFilters() {
        _searchQuery.value = ""
        _filterCategory.value = ""
        _filterPriority.value = -1
        _filterDueOnly.value = false
    }

    /** 勾选 / 取消勾选完成状态 */
    fun toggleDone(task: Task) {
        viewModelScope.launch {
            val updated = task.copy(isDone = !task.isDone)
            repo.updateTask(updated)
            val ctx = getApplication<Application>()
            if (updated.isDone) {
                AlarmScheduler.cancel(ctx, updated.id)
            } else {
                AlarmScheduler.schedule(ctx, updated)
            }
            TaskWidgetProvider.notifyRefresh(ctx)
        }
    }

    fun delete(task: Task) {
        viewModelScope.launch {
            repo.deleteTask(task)
            val ctx = getApplication<Application>()
            AlarmScheduler.cancel(ctx, task.id)
            TaskWidgetProvider.notifyRefresh(ctx)
        }
    }

    fun clearCompleted() {
        viewModelScope.launch {
            repo.clearCompletedTasks()
            TaskWidgetProvider.notifyRefresh(getApplication())
        }
    }

    /** 撤销删除时重新插入 */
    fun insert(task: Task) {
        viewModelScope.launch {
            val newId = repo.insertTask(task.copy(id = 0))
            val ctx = getApplication<Application>()
            AlarmScheduler.schedule(ctx, task.copy(id = newId))
            TaskWidgetProvider.notifyRefresh(ctx)
        }
    }
}
