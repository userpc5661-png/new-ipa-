package com.example.sls_assistant_pro.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sls_assistant_pro.data.model.PaymentKind
import com.example.sls_assistant_pro.data.model.TaskProgress
import com.example.sls_assistant_pro.ui.theme.SLSAccentGreen
import com.example.sls_assistant_pro.ui.theme.SLSBlue
import com.example.sls_assistant_pro.ui.theme.SLSOrange

@Composable
fun DashboardTab(
    tasksState: com.example.sls_assistant_pro.ui.viewmodel.TasksUiState,
    onNavigateToScan: () -> Unit,
    onNavigateToMap: () -> Unit,
    onNavigateToTasks: () -> Unit
) {
    val tasks = tasksState.tasks
    val completedCount = tasks.count { it.progress == TaskProgress.completed }
    val remainingCount = tasks.size - completedCount
    val codTotal = tasks.filter { it.isCashOnDelivery }.sumOf { it.codAmount ?: 0.0 }
    val prepaidCount = tasks.count { it.paymentKind == PaymentKind.prepaid }
    val locatedCount = tasks.count { it.hasNavigableLocation }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Driver Banner Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "مرحباً بك كابتن SLS! 👋",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "لديك اليوم ${tasks.size} شحنة إجمالية في خطتك اليومية.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quick Actions Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ElevatedCard(
                onClick = onNavigateToScan,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("مسح الباركود", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            ElevatedCard(
                onClick = onNavigateToMap,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Map, contentDescription = null, tint = SLSBlue, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("خريطة التوصيل", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text("ملخص المهام والتحصيل", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))

        // Metrics Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                title = "تم التوصيل",
                value = "$completedCount",
                icon = Icons.Default.CheckCircle,
                color = SLSAccentGreen,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "المتبقي",
                value = "$remainingCount",
                icon = Icons.Default.PendingActions,
                color = SLSOrange,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                title = "إجمالي الكاش (COD)",
                value = "$codTotal ريال",
                icon = Icons.Default.Payments,
                color = SLSOrange,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "شحنات مدفوعة",
                value = "$prepaidCount",
                icon = Icons.Default.CreditCard,
                color = SLSBlue,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // All Tasks List Shortcut Card
        OutlinedCard(
            onClick = onNavigateToTasks,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("عرض قائمة الشحنات التفصيلية", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("محدثة حسب حالة التواصل مع العملاء", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Default.ArrowBackIos, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
