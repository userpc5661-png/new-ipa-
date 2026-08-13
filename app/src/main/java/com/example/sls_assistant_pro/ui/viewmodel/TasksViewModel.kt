package com.example.sls_assistant_pro.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sls_assistant_pro.data.local.LocalContactEntity
import com.example.sls_assistant_pro.data.local.LocationCorrectionEntity
import com.example.sls_assistant_pro.data.model.TaskItem
import com.example.sls_assistant_pro.data.model.TaskProgress
import com.example.sls_assistant_pro.data.repository.SlsRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class TasksUiState(
    val isLoading: Boolean = false,
    val tasks: List<TaskItem> = emptyList(),
    val filteredTasks: List<TaskItem> = emptyList(),
    val notContactedTasks: List<TaskItem> = emptyList(),
    val answeredTasks: List<TaskItem> = emptyList(),
    val noAnswerTasks: List<TaskItem> = emptyList(),
    val localContactMap: Map<String, String> = emptyMap(), // taskKey -> status
    val locationCorrectionMap: Map<String, LocationCorrectionEntity> = emptyMap(),
    val searchQuery: String = "",
    val error: String? = null,
    val driverLat: Double = 24.7136, // Riyadh fallback
    val driverLng: Double = 46.6753
)

class TasksViewModel(private val repository: SlsRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(TasksUiState())
    val uiState: StateFlow<TasksUiState> = _uiState.asStateFlow()

    init {
        observeLocalData()
    }

    private fun observeLocalData() {
        viewModelScope.launch {
            combine(
                repository.localContacts,
                repository.locationCorrections
            ) { contacts, corrections ->
                val contactMap = contacts.associate { it.taskKey to it.status }
                val correctionMap = corrections.associateBy { it.referenceNumber }
                Pair(contactMap, correctionMap)
            }.collectLatest { (contactMap, correctionMap) ->
                _uiState.update { current ->
                    val updatedState = current.copy(
                        localContactMap = contactMap,
                        locationCorrectionMap = correctionMap
                    )
                    applyFiltersAndGroups(updatedState)
                }
            }
        }
    }

    fun updateDriverLocation(lat: Double, lng: Double) {
        _uiState.update { it.copy(driverLat = lat, driverLng = lng) }
    }

    fun loadTasks(lat: Double? = null, lng: Double? = null) {
        val currentLat = lat ?: _uiState.value.driverLat
        val currentLng = lng ?: _uiState.value.driverLng

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, driverLat = currentLat, driverLng = currentLng) }
            try {
                val fetched = repository.fetchTasks(currentLat, currentLng)
                _uiState.update { current ->
                    val updated = current.copy(isLoading = false, tasks = fetched)
                    applyFiltersAndGroups(updated)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "تعذر جلب المهام.") }
            }
        }
    }

    fun addExtraTasks(newTasks: List<TaskItem>) {
        _uiState.update { current ->
            val existingRefs = current.tasks.map { it.displayReference }.toSet()
            val filteredNew = newTasks.filter { it.displayReference !in existingRefs }
            val combined = current.tasks + filteredNew
            applyFiltersAndGroups(current.copy(tasks = combined))
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { current ->
            val updated = current.copy(searchQuery = query)
            applyFiltersAndGroups(updated)
        }
    }

    fun setLocalContactStatus(taskKey: String, status: String) {
        viewModelScope.launch {
            repository.setLocalContactStatus(taskKey, status)
        }
    }

    fun saveLocationCorrection(refNumber: String, lat: Double, lng: Double) {
        viewModelScope.launch {
            repository.saveLocationCorrection(refNumber, lat, lng)
        }
    }

    private fun applyFiltersAndGroups(state: TasksUiState): TasksUiState {
        val query = state.searchQuery.trim().lowercase()

        // Apply corrected coordinates to task items if present
        val processedTasks = state.tasks.map { task ->
            val ref = task.displayReference
            val correction = state.locationCorrectionMap[ref]
            if (correction != null) {
                task.copy(latitude = correction.latitude, longitude = correction.longitude)
            } else {
                task
            }
        }

        val filtered = processedTasks.filter { task ->
            if (query.isBlank()) return@filter true
            task.displayReference.lowercase().contains(query) ||
                    task.customerName.lowercase().contains(query) ||
                    task.customerPhone.lowercase().contains(query) ||
                    task.displayStoreName.lowercase().contains(query) ||
                    task.address.lowercase().contains(query)
        }

        val notContacted = mutableListOf<TaskItem>()
        val answered = mutableListOf<TaskItem>()
        val noAnswer = mutableListOf<TaskItem>()

        for (task in filtered) {
            val key = task.displayReference
            val status = state.localContactMap[key] ?: "none"
            when (status) {
                "answered" -> answered.add(task)
                "no_answer" -> noAnswer.add(task)
                else -> notContacted.add(task)
            }
        }

        return state.copy(
            tasks = processedTasks,
            filteredTasks = filtered,
            notContactedTasks = notContacted,
            answeredTasks = answered,
            noAnswerTasks = noAnswer
        )
    }
}
