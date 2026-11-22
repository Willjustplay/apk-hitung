package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AgendaItem
import com.example.data.AgendaRepository
import com.example.data.Formatters
import com.example.data.TaskPriority
import com.example.data.UiState
import com.example.data.friendlyFirestoreErrorMessage
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class TaskStatusFilter(val label: String) {
    ALL("Semua"),
    ACTIVE("Aktif"),
    COMPLETED("Selesai")
}

data class DayAgendaView(
    val selectedDateKey: String = Formatters.todayDateKey(),
    val schedules: List<AgendaItem> = emptyList(),
    val tasks: List<AgendaItem> = emptyList(),
    val totalDayItems: Int = 0,
    val completedDayItems: Int = 0,
    val datesWithItems: Set<String> = emptySet()
)

data class TodayAgendaSummary(
    val totalCount: Int = 0,
    val completedCount: Int = 0,
    val previewItems: List<AgendaItem> = emptyList()
)

class AgendaViewModel(
    private val repository: AgendaRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _uiMessage = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val uiMessage: SharedFlow<String> = _uiMessage.asSharedFlow()

    private val _selectedDateKey = MutableStateFlow(Formatters.todayDateKey())
    val selectedDateKey: StateFlow<String> = _selectedDateKey.asStateFlow()

    private val _statusFilter = MutableStateFlow(TaskStatusFilter.ALL)
    val statusFilter: StateFlow<TaskStatusFilter> = _statusFilter.asStateFlow()

    private val _priorityFilter = MutableStateFlow<TaskPriority?>(null)
    val priorityFilter: StateFlow<TaskPriority?> = _priorityFilter.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    val tasksState: StateFlow<UiState<List<AgendaItem>>> = repository.observeTasks(currentUserId)
        .map<List<AgendaItem>, UiState<List<AgendaItem>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing tasks", rootError)
            emit(UiState.Error(friendlyFirestoreErrorMessage(rootError)))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val dayAgendaView: StateFlow<DayAgendaView> = combine(
        tasksState,
        _selectedDateKey,
        _statusFilter,
        _priorityFilter
    ) { state, dateKey, statusFilter, priorityFilter ->
        val all = (state as? UiState.Success)?.data.orEmpty()
        val datesWithItems = all.map { it.dateKey }.toSet()
        val forDay = all.filter { it.dateKey == dateKey }

        val filtered = forDay.filter { item ->
            val matchStatus = when (statusFilter) {
                TaskStatusFilter.ALL -> true
                TaskStatusFilter.ACTIVE -> !item.completed
                TaskStatusFilter.COMPLETED -> item.completed
            }
            val matchPriority = priorityFilter == null || item.taskPriority == priorityFilter
            matchStatus && matchPriority
        }

        val schedules = filtered
            .filter { it.hasScheduleTime }
            .sortedWith(compareBy<AgendaItem> { it.startTime ?: "99:99" }.thenBy { it.completed })

        val tasks = filtered
            .filter { !it.hasScheduleTime }
            .sortedWith(
                compareBy<AgendaItem> { it.completed }
                    .thenBy { it.taskPriority.sortOrder }
                    .thenByDescending { it.createdAt?.seconds ?: 0L }
            )

        DayAgendaView(
            selectedDateKey = dateKey,
            schedules = schedules,
            tasks = tasks,
            totalDayItems = forDay.size,
            completedDayItems = forDay.count { it.completed },
            datesWithItems = datesWithItems
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = DayAgendaView()
    )

    val todaySummary: StateFlow<TodayAgendaSummary> = tasksState.map { state ->
        val today = Formatters.todayDateKey()
        val todayItems = (state as? UiState.Success)?.data.orEmpty().filter { it.dateKey == today }
        val sorted = todayItems.sortedWith(
            compareBy<AgendaItem> { it.completed }
                .thenBy { if (it.hasScheduleTime) 0 else 1 }
                .thenBy { it.startTime ?: "99:99" }
                .thenBy { it.taskPriority.sortOrder }
        )
        TodayAgendaSummary(
            totalCount = todayItems.size,
            completedCount = todayItems.count { it.completed },
            previewItems = sorted.take(4)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = TodayAgendaSummary()
    )

    fun selectDate(dateKey: String) {
        _selectedDateKey.value = dateKey
    }

    fun jumpToToday() {
        _selectedDateKey.value = Formatters.todayDateKey()
    }

    fun updateStatusFilter(filter: TaskStatusFilter) {
        _statusFilter.value = filter
    }

    fun updatePriorityFilter(priority: TaskPriority?) {
        _priorityFilter.value = if (_priorityFilter.value == priority) null else priority
    }

    fun saveAgendaItem(
        existingItem: AgendaItem?,
        title: String,
        notes: String,
        dateKey: String,
        startTime: String?,
        endTime: String?,
        priority: TaskPriority,
        category: String,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = if (existingItem == null) {
                repository.addTask(
                    userId = currentUserId,
                    title = title,
                    notes = notes,
                    dateKey = dateKey,
                    startTime = startTime,
                    endTime = endTime,
                    priority = priority,
                    category = category
                )
            } else {
                repository.updateTask(
                    userId = currentUserId,
                    taskId = existingItem.id,
                    title = title,
                    notes = notes,
                    dateKey = dateKey,
                    startTime = startTime,
                    endTime = endTime,
                    priority = priority,
                    category = category,
                    completed = existingItem.completed
                )
            }
            _isSaving.value = false
            result.onSuccess {
                _uiMessage.tryEmit(if (existingItem == null) "Agenda ditambahkan" else "Agenda diperbarui")
                onComplete()
            }.onFailure { err ->
                _uiMessage.tryEmit(friendlyFirestoreErrorMessage(err))
            }
        }
    }

    fun toggleCompleted(item: AgendaItem) {
        viewModelScope.launch {
            val nextState = !item.completed
            repository.toggleCompleted(currentUserId, item.id, nextState)
                .onFailure { err ->
                    _uiMessage.tryEmit(friendlyFirestoreErrorMessage(err))
                }
        }
    }

    fun deleteAgendaItem(taskId: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.deleteTask(currentUserId, taskId)
            _isSaving.value = false
            result.onSuccess {
                _uiMessage.tryEmit("Agenda dihapus")
                onComplete()
            }.onFailure { err ->
                _uiMessage.tryEmit(friendlyFirestoreErrorMessage(err))
            }
        }
    }

    private companion object {
        const val TAG = "AgendaVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
