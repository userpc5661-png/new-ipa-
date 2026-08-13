package com.example.sls_assistant_pro.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sls_assistant_pro.data.local.DeliveryHistoryEntity
import com.example.sls_assistant_pro.data.model.TaskItem
import com.example.sls_assistant_pro.data.model.TaskProgress
import com.example.sls_assistant_pro.data.repository.SlsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class CollectionWalletUiState(
    val totalRequired: Double = 0.0,
    val collectedAmount: Double = 0.0,
    val remainingAmount: Double = 0.0,
    val cashShipmentsCount: Int = 0,
    val collectedCount: Int = 0,
    val historyList: List<DeliveryHistoryEntity> = emptyList()
)

class CollectionWalletViewModel(
    private val repository: SlsRepository,
    private val currentTasks: List<TaskItem>
) : ViewModel() {

    private val _uiState = MutableStateFlow(CollectionWalletUiState())
    val uiState: StateFlow<CollectionWalletUiState> = _uiState.asStateFlow()

    init {
        observeHistory()
    }

    private fun observeHistory() {
        viewModelScope.launch {
            repository.getTodayHistory().collectLatest { history ->
                calculateMetrics(history)
            }
        }
    }

    private fun calculateMetrics(history: List<DeliveryHistoryEntity>) {
        val collectedAwbs = history.filter { it.collected }.map { it.awb }.toSet()

        val cashTasks = currentTasks.filter { task ->
            task.isCashOnDelivery && !collectedAwbs.contains(task.displayReference)
        }

        val completedServerCash = cashTasks.filter {
            it.progress == TaskProgress.completed && (it.codAmount ?: 0.0) > 0
        }

        val historyCollectedSum = history.sumOf { it.codAmount }
        val cashTasksSum = cashTasks.sumOf { it.codAmount ?: 0.0 }
        val completedServerCashSum = completedServerCash.sumOf { it.codAmount ?: 0.0 }

        val total = cashTasksSum + historyCollectedSum
        val collected = completedServerCashSum + historyCollectedSum
        val remaining = (total - collected).coerceAtLeast(0.0)

        _uiState.value = CollectionWalletUiState(
            totalRequired = total,
            collectedAmount = collected,
            remainingAmount = remaining,
            cashShipmentsCount = cashTasks.size + history.size,
            collectedCount = completedServerCash.size + history.size,
            historyList = history
        )
    }
}
