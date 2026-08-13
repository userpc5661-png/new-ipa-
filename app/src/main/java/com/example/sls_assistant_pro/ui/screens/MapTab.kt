package com.example.sls_assistant_pro.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sls_assistant_pro.data.model.PaymentKind
import com.example.sls_assistant_pro.data.model.ShipmentFieldMapper
import com.example.sls_assistant_pro.data.model.TaskItem
import com.example.sls_assistant_pro.ui.components.OsmMapView
import com.example.sls_assistant_pro.ui.theme.SLSAccentGreen
import com.example.sls_assistant_pro.ui.theme.SLSBlue
import com.example.sls_assistant_pro.ui.theme.SLSOrange
import com.example.sls_assistant_pro.ui.viewmodel.TasksUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapTab(
    tasksState: TasksUiState,
    onDriverLocationUpdated: (Double, Double) -> Unit,
    onSelectTaskDetails: (TaskItem) -> Unit,
    onOpenStatusUpdate: (TaskItem) -> Unit,
    onCallRequested: (TaskItem) -> Unit,
    onNavigateToScan: () -> Unit = {}
) {
    val context = LocalContext.current
    var paymentFilter by remember { mutableStateOf<PaymentKind?>(null) } // null = All
    var selectedTask by remember { mutableStateOf<TaskItem?>(null) }
    var selectedClusterTasks by remember { mutableStateOf<List<TaskItem>?>(null) }
    var recenterTrigger by remember { mutableIntStateOf(0) }

    val filteredTasks = tasksState.tasks.filter { task ->
        if (paymentFilter == null) true else task.paymentKind == paymentFilter
    }

    val locatedTasks = filteredTasks.filter { it.hasNavigableLocation }
    val unlocatedCount = filteredTasks.size - locatedTasks.size

    Box(modifier = Modifier.fillMaxSize()) {
        // Map Component
        OsmMapView(
            driverLat = tasksState.driverLat,
            driverLng = tasksState.driverLng,
            tasks = locatedTasks,
            contactStatusMap = tasksState.localContactMap,
            onDriverLocationUpdated = { lat, lng, _ -> onDriverLocationUpdated(lat, lng) },
            onTaskSelected = { task ->
                selectedClusterTasks = null
                selectedTask = task
            },
            onClusterSelected = { tasks ->
                selectedTask = null
                selectedClusterTasks = tasks
            },
            recenterTrigger = recenterTrigger,
            modifier = Modifier.fillMaxSize()
        )

        // Top Filter Chips Overlay
        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = paymentFilter == null,
                    onClick = { paymentFilter = null },
                    label = { Text("الكل (${filteredTasks.size})", fontSize = 12.sp) }
                )
                FilterChip(
                    selected = paymentFilter == PaymentKind.cashOnDelivery,
                    onClick = { paymentFilter = PaymentKind.cashOnDelivery },
                    label = { Text("كاش (COD)", fontSize = 12.sp) }
                )
                FilterChip(
                    selected = paymentFilter == PaymentKind.prepaid,
                    onClick = { paymentFilter = PaymentKind.prepaid },
                    label = { Text("مدفوع", fontSize = 12.sp) }
                )
            }
        }

        // Floating Action Buttons (Recenter & Smart Scanner)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Recenter Button
            SmallFloatingActionButton(
                onClick = { recenterTrigger++ },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "موقعي الحالي")
            }

            // Scanner Button
            SmallFloatingActionButton(
                onClick = onNavigateToScan,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = "الماسح الذكي")
            }
        }

        // Bottom Info Bar if unlocated shipments exist
        if (unlocatedCount > 0 && selectedTask == null && selectedClusterTasks == null) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PinDrop, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "هناك $unlocatedCount شحنة بدون إحداثيات موقع",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Bottom Sheet for Clustered Tasks
        selectedClusterTasks?.let { clusterTasks ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "الشحنات المجمعة في هذا الموقع (${clusterTasks.size})",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = { selectedClusterTasks = null }) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق")
                        }
                    }

                    HorizontalDivider()

                    clusterTasks.forEach { task ->
                        Card(
                            onClick = {
                                selectedClusterTasks = null
                                selectedTask = task
                            },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = task.customerName.ifBlank { "عميل غير محدد" },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "المتجر: ${task.displayStoreName} | شحنة: ${task.displayReference}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = if (task.isCashOnDelivery) "💵 ${task.codAmount ?: 0.0} ريال" else "✓ مدفوع",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (task.isCashOnDelivery) SLSOrange else SLSBlue
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom Sheet / Card for selected task marker
        selectedTask?.let { task ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Header: Ref & Payment Badge & Close Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = task.displayReference,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "المتجر: ${task.displayStoreName}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (task.isCashOnDelivery) {
                                Surface(
                                    color = SLSOrange.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "💵 كاش: ${task.codAmount ?: 0.0} ريال",
                                        color = SLSOrange,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            } else {
                                Surface(
                                    color = SLSBlue.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "✓ مدفوع",
                                        color = SLSBlue,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            IconButton(onClick = { selectedTask = null }) {
                                Icon(Icons.Default.Close, contentDescription = "إغلاق")
                            }
                        }
                    }

                    HorizontalDivider()

                    // Customer Details
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "العميل: ${task.customerName.ifBlank { "غير محدد" }}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (task.customerPhone.isNotBlank()) {
                                Text(
                                    text = "الهاتف: ${task.customerPhone}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (task.address.isNotBlank()) {
                                Text(
                                    text = "العنوان: ${task.address}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2
                                )
                            }
                        }
                    }

                    // Action Buttons Row: Call, WhatsApp, Navigation, Status Update
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Call
                        IconButton(
                            onClick = {
                                if (task.customerPhone.isNotBlank()) {
                                    val cleanPhone = task.customerPhone.replace("-", "").replace(" ", "").replace("(", "").replace(")", "").trim()
                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanPhone"))
                                    context.startActivity(intent)
                                    onCallRequested(task)
                                }
                            },
                            enabled = task.customerPhone.isNotBlank()
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = "اتصال", tint = MaterialTheme.colorScheme.primary)
                        }

                        // WhatsApp
                        IconButton(
                            onClick = {
                                if (task.customerPhone.isNotBlank()) {
                                    val formattedPhone = ShipmentFieldMapper.formatSaudiPhone(task.customerPhone)
                                    val msg = ShipmentFieldMapper.buildWhatsAppMessage(task)
                                    val uri = Uri.parse("https://api.whatsapp.com/send?phone=$formattedPhone&text=${Uri.encode(msg)}")
                                    val intent = Intent(Intent.ACTION_VIEW, uri)
                                    context.startActivity(intent)
                                }
                            },
                            enabled = task.customerPhone.isNotBlank()
                        ) {
                            Icon(Icons.Default.Chat, contentDescription = "واتساب", tint = SLSAccentGreen)
                        }

                        // Navigation
                        IconButton(
                            onClick = {
                                if (task.hasNavigableLocation) {
                                    val uri = Uri.parse("google.navigation:q=${task.latitude},${task.longitude}")
                                    val intent = Intent(Intent.ACTION_VIEW, uri)
                                    context.startActivity(intent)
                                }
                            },
                            enabled = task.hasNavigableLocation
                        ) {
                            Icon(Icons.Default.Navigation, contentDescription = "ملاحة", tint = SLSBlue)
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Status Update Button
                        Button(
                            onClick = {
                                val target = selectedTask
                                selectedTask = null
                                if (target != null) {
                                    onOpenStatusUpdate(target)
                                }
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("تحديث الحالة", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
