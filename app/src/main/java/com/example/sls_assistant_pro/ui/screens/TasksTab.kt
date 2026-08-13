package com.example.sls_assistant_pro.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sls_assistant_pro.data.model.TaskItem
import com.example.sls_assistant_pro.ui.components.LocationCorrectionDialog
import com.example.sls_assistant_pro.ui.components.TaskCard
import com.example.sls_assistant_pro.ui.viewmodel.TasksUiState
import com.example.sls_assistant_pro.ui.viewmodel.TasksViewModel

@Composable
fun TasksTab(
    tasksViewModel: TasksViewModel,
    tasksState: TasksUiState,
    onOpenTaskDetails: (TaskItem) -> Unit,
    onOpenStatusUpdate: (TaskItem) -> Unit,
    onCallRequested: (TaskItem) -> Unit
) {
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    var selectedTaskForLocationCorrection by remember { mutableStateOf<TaskItem?>(null) }

    val categories = listOf(
        "لم يتم التواصل (${tasksState.notContactedTasks.size})",
        "العميل أجاب (${tasksState.answeredTasks.size})",
        "العميل لم يجيب (${tasksState.noAnswerTasks.size})"
    )

    val currentTasksList = when (selectedCategoryIndex) {
        0 -> tasksState.notContactedTasks
        1 -> tasksState.answeredTasks
        else -> tasksState.noAnswerTasks
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search Bar
        OutlinedTextField(
            value = tasksState.searchQuery,
            onValueChange = { tasksViewModel.setSearchQuery(it) },
            placeholder = { Text("بحث بالرقم، المتجر، العميل...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (tasksState.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { tasksViewModel.setSearchQuery("") }) {
                        Icon(Icons.Default.Clear, contentDescription = null)
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(12.dp)
        )

        // Contact Status Tabs
        ScrollableTabRow(
            selectedTabIndex = selectedCategoryIndex,
            edgePadding = 16.dp,
            divider = {}
        ) {
            categories.forEachIndexed { index, title ->
                Tab(
                    selected = selectedCategoryIndex == index,
                    onClick = { selectedCategoryIndex = index },
                    text = { Text(title, fontSize = 13.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Tasks List
        if (tasksState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (currentTasksList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "لا توجد شحنات في هذا القسم حالياً.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(currentTasksList, key = { it.displayReference }) { task ->
                    val status = tasksState.localContactMap[task.displayReference] ?: "none"
                    TaskCard(
                        task = task,
                        contactStatus = status,
                        onContactStatusChanged = { newStatus ->
                            tasksViewModel.setLocalContactStatus(task.displayReference, newStatus)
                        },
                        onCorrectLocationRequested = {
                            selectedTaskForLocationCorrection = task
                        },
                        onOpenDetails = { onOpenTaskDetails(task) },
                        onOpenStatusUpdate = { onOpenStatusUpdate(task) },
                        onCallRequested = { onCallRequested(task) }
                    )
                }
            }
        }
    }

    // Location Correction Dialog
    selectedTaskForLocationCorrection?.let { task ->
        LocationCorrectionDialog(
            task = task,
            currentDriverLat = tasksState.driverLat,
            currentDriverLng = tasksState.driverLng,
            onDismiss = { selectedTaskForLocationCorrection = null },
            onSaveCorrection = { lat, lng ->
                tasksViewModel.saveLocationCorrection(task.displayReference, lat, lng)
            }
        )
    }
}
