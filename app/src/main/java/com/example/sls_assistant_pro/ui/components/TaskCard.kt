package com.example.sls_assistant_pro.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sls_assistant_pro.data.model.PaymentKind
import com.example.sls_assistant_pro.data.model.TaskItem
import com.example.sls_assistant_pro.data.model.TaskProgress
import com.example.sls_assistant_pro.ui.theme.SLSAccentGreen
import com.example.sls_assistant_pro.ui.theme.SLSBlue
import com.example.sls_assistant_pro.ui.theme.SLSOrange
import com.example.sls_assistant_pro.ui.theme.SLSRed

@Composable
fun TaskCard(
    task: TaskItem,
    contactStatus: String, // "none", "answered", "no_answer"
    onContactStatusChanged: (String) -> Unit,
    onCorrectLocationRequested: () -> Unit,
    onOpenDetails: () -> Unit,
    onOpenStatusUpdate: () -> Unit,
    onCallRequested: ((TaskItem) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val statusBgColor = when (contactStatus) {
        "answered" -> SLSAccentGreen.copy(alpha = 0.12f)
        "no_answer" -> SLSRed.copy(alpha = 0.12f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val statusTextColor = when (contactStatus) {
        "answered" -> SLSAccentGreen
        "no_answer" -> SLSRed
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val statusLabel = when (contactStatus) {
        "answered" -> "العميل أجاب"
        "no_answer" -> "العميل لم يجيب"
        else -> "لم يتم التواصل"
    }

    ElevatedCard(
        onClick = onOpenDetails,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: AWB / Reference & Status Chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Inventory2,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = task.displayReference,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusBgColor)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = statusLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = statusTextColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Store Name & Customer Name
            Text(
                text = "المتجر: ${task.displayStoreName}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "العميل: ${task.customerName.ifBlank { "غير مسمى" }}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Address
            if (task.address.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = task.address,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // COD or Prepaid Badge
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (task.paymentKind == PaymentKind.cashOnDelivery) {
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                "كاش: ${task.codAmount ?: 0.0} ريال",
                                fontWeight = FontWeight.Bold,
                                color = SLSOrange
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Payments, contentDescription = null, tint = SLSOrange)
                        }
                    )
                } else {
                    AssistChip(
                        onClick = {},
                        label = {
                            Text("مدفوع بـ (Prepaid)", fontWeight = FontWeight.SemiBold, color = SLSBlue)
                        },
                        leadingIcon = {
                            Icon(Icons.Default.CreditCard, contentDescription = null, tint = SLSBlue)
                        }
                    )
                }

                if (task.progress == TaskProgress.completed) {
                    AssistChip(
                        onClick = {},
                        label = { Text("تم التسليم", color = SLSAccentGreen) },
                        leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SLSAccentGreen) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons: Call, WhatsApp, Navigation, Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Call
                IconButton(
                    onClick = {
                        if (task.customerPhone.isNotBlank()) {
                            val cleanPhone = task.customerPhone.replace("-", "").replace(" ", "").replace("(", "").replace(")", "").trim()
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanPhone"))
                            context.startActivity(intent)
                            onCallRequested?.invoke(task)
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
                            val formattedPhone = com.example.sls_assistant_pro.data.model.ShipmentFieldMapper.formatSaudiPhone(task.customerPhone)
                            val msg = com.example.sls_assistant_pro.data.model.ShipmentFieldMapper.buildWhatsAppMessage(task)
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

                // Correct Location
                IconButton(onClick = onCorrectLocationRequested) {
                    Icon(Icons.Outlined.EditLocationAlt, contentDescription = "تصحيح الموقع")
                }

                // Contact Status Menu
                IconButton(
                    onClick = {
                        val nextStatus = when (contactStatus) {
                            "none" -> "answered"
                            "answered" -> "no_answer"
                            else -> "none"
                        }
                        onContactStatusChanged(nextStatus)
                    }
                ) {
                    Icon(
                        imageVector = if (contactStatus == "answered") Icons.Default.CheckCircle else Icons.Outlined.HourglassEmpty,
                        contentDescription = "حالة التواصل",
                        tint = statusTextColor
                    )
                }

                // Update Status Button
                FilledTonalButton(
                    onClick = {
                        val awb = task.realAwb.ifBlank { task.referenceNumber }
                        android.util.Log.d("StatusUpdate", "STATUS BUTTON PRESSED")
                        android.util.Log.d("StatusUpdate", "awb: $awb")
                        android.util.Log.d("StatusUpdate", "task id: ${task.id}")
                        android.util.Log.d("StatusUpdate", "navigation started")
                        android.util.Log.d("StatusUpdate", "status screen opened=true")
                        onOpenStatusUpdate()
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("الحالة", fontSize = 12.sp)
                }
            }
        }
    }
}
