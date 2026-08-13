package com.example.sls_assistant_pro.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sls_assistant_pro.data.model.TaskItem
import com.example.sls_assistant_pro.ui.components.CameraScannerView
import com.example.sls_assistant_pro.ui.viewmodel.ScannerViewModel

@Composable
fun ScannerTab(
    scannerViewModel: ScannerViewModel,
    onOpenTaskDetails: (TaskItem) -> Unit,
    onShowOnMap: ((List<TaskItem>) -> Unit)? = null
) {
    val state by scannerViewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            CameraScannerView(
                onBarcodeScanned = { code -> scannerViewModel.onBarcodeScanned(code) }
            )

            // Camera Overlay Guideline
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(240.dp, 160.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                ),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            if (state.isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        // Bottom Result Panel
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val result = state.result
                if (result?.shipment != null) {
                    val task = result.shipment!!.task
                    val rawStatus = task.statusLabel.ifBlank { task.statusCode }
                    val isDelivered = rawStatus.lowercase().contains("delivered") ||
                            rawStatus.contains("تم التسليم") ||
                            rawStatus.contains("تم توصيل") ||
                            rawStatus.lowercase().contains("completed")

                    // Status Badge
                    Surface(
                        color = when {
                            isDelivered -> MaterialTheme.colorScheme.primaryContainer
                            rawStatus.lowercase().contains("cancelled") || rawStatus.lowercase().contains("refused") -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.secondaryContainer
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = when {
                                    isDelivered -> MaterialTheme.colorScheme.primary
                                    rawStatus.lowercase().contains("cancelled") -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.secondary
                                },
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when {
                                    isDelivered -> "الحالة: تم توصيل الشحنة مسبقاً ($rawStatus)"
                                    rawStatus.lowercase().contains("out for delivery") || rawStatus.contains("خارج للتوصيل") -> "الحالة: خارج للتوصيل"
                                    rawStatus.isNotBlank() -> "الحالة: $rawStatus"
                                    else -> "الحالة: نشطة"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    // Top Row: Ref & Store Name & Refresh
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "شحنة: ${task.displayReference}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (task.realAwb.isNotBlank() && task.realAwb != task.displayReference) {
                                Text(
                                    text = "Actual AWB: ${task.realAwb}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "المتجر: ${task.displayStoreName}",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { scannerViewModel.reset() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "إعادة المسح")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Customer Details
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (task.customerName.isNotBlank()) {
                            Text(text = "العميل: ${task.customerName}", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                        if (task.customerPhone.isNotBlank()) {
                            Text(text = "الجوال: ${task.customerPhone}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (task.address.isNotBlank()) {
                            Text(text = "العنوان: ${task.address}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                        }
                        Text(
                            text = if (task.isCashOnDelivery) "الدفع: 💵 كاش (${task.codAmount ?: 0.0} ريال)" else "الدفع: ✓ مدفوع (Prepaid)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (task.isCashOnDelivery) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onOpenTaskDetails(task) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("التفاصيل والتحديث")
                        }
                        if (onShowOnMap != null) {
                            OutlinedButton(
                                onClick = { onShowOnMap(listOf(task)) }
                            ) {
                                Text("بالخريطة")
                            }
                        }
                        OutlinedButton(
                            onClick = { scannerViewModel.reset() }
                        ) {
                            Text("إنهاء النتيجة")
                        }
                    }
                } else if (result?.orderGroup != null || result?.linehaulGroup != null) {
                    val groupName = result.orderGroup?.groupId ?: result.linehaulGroup?.groupId ?: ""
                    val tasks = state.groupTasks

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "مجموعة طلبات: $groupName",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "إجمالي الطلبات: ${tasks.size} | المؤكدة: ${state.confirmedAwbs.size}",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { scannerViewModel.reset() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "إنهاء النتيجة")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(tasks) { task ->
                            val isConfirmed = state.confirmedAwbs.contains(task.displayReference) ||
                                    state.confirmedAwbs.contains(task.realAwb)
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isConfirmed) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = task.customerName.ifBlank { task.displayReference },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = "المتجر: ${task.displayStoreName} | شحنة: ${task.displayReference}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (isConfirmed) {
                                        Text(
                                            text = "✓ تم المسح",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    } else {
                                        TextButton(onClick = { scannerViewModel.confirmAwbInGroup(task.displayReference) }) {
                                            Text("تأكيد", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (onShowOnMap != null && tasks.isNotEmpty()) {
                            Button(
                                onClick = { onShowOnMap(tasks) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("عرض كافة الشحنات على الخريطة")
                            }
                        }
                        OutlinedButton(
                            onClick = { scannerViewModel.reset() }
                        ) {
                            Text("إنهاء النتيجة")
                        }
                    }
                } else if (!state.error.isNullOrBlank()) {
                    Text(
                        text = state.error!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = { scannerViewModel.reset() }) {
                        Text("إنهاء النتيجة")
                    }
                } else {
                    Text(
                        text = "وجه الكاميرا نحو باركود الشحنة أو البيان",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
