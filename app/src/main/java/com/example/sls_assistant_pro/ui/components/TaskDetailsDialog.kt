package com.example.sls_assistant_pro.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sls_assistant_pro.data.model.TaskItem

@Composable
fun TaskDetailsDialog(
    task: TaskItem,
    onDismiss: () -> Unit,
    onOpenStatusUpdate: () -> Unit,
    onCallRequested: ((TaskItem) -> Unit)? = null
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "تفاصيل الشحنة",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = task.displayReference,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DetailItem(label = "المتجر / الشاحن", value = task.displayStoreName)
                DetailItem(label = "اسم العميل", value = task.customerName.ifBlank { "غير متاح" })
                DetailItem(label = "رقم الجوال", value = task.customerPhone.ifBlank { "غير متاح" })
                DetailItem(label = "العنوان / الموقع", value = task.address.ifBlank { "غير متاح" })

                if (task.isCashOnDelivery) {
                    DetailItem(label = "الدفع عند الاستلام (COD)", value = "${task.codAmount} ريال")
                } else {
                    DetailItem(label = "نوع الدفع", value = "مدفوع مسبقاً (Prepaid)")
                }

                if (task.hasNavigableLocation) {
                    DetailItem(label = "الإحداثيات", value = "${task.latitude}, ${task.longitude}")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action Buttons Row
                Text("إجراءات سريعة:", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Call Phone
                    if (task.customerPhone.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                val cleanPhone = task.customerPhone.replace("-", "").replace(" ", "").replace("(", "").replace(")", "").trim()
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanPhone"))
                                context.startActivity(intent)
                                onCallRequested?.invoke(task)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("اتصال", fontSize = 12.sp)
                        }

                        // WhatsApp
                        OutlinedButton(
                            onClick = {
                                val formattedPhone = com.example.sls_assistant_pro.data.model.ShipmentFieldMapper.formatSaudiPhone(task.customerPhone)
                                val msg = com.example.sls_assistant_pro.data.model.ShipmentFieldMapper.buildWhatsAppMessage(task)
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$formattedPhone&text=${Uri.encode(msg)}"))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("واتساب", fontSize = 12.sp)
                        }
                    }
                }

                // Navigation App
                if (task.hasNavigableLocation) {
                    Button(
                        onClick = {
                            val uri = Uri.parse("google.navigation:q=${task.latitude},${task.longitude}")
                            val intent = Intent(Intent.ACTION_VIEW, uri)
                            intent.setPackage("com.google.android.apps.maps")
                            if (intent.resolveActivity(context.packageManager) != null) {
                                context.startActivity(intent)
                            } else {
                                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=${task.latitude},${task.longitude}"))
                                context.startActivity(browserIntent)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("فتح خريطة الملاحة (Google Maps)")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val awb = task.realAwb.ifBlank { task.referenceNumber }
                    android.util.Log.d("StatusUpdate", "STATUS BUTTON PRESSED")
                    android.util.Log.d("StatusUpdate", "awb: $awb")
                    android.util.Log.d("StatusUpdate", "task id: ${task.id}")
                    android.util.Log.d("StatusUpdate", "navigation started")
                    android.util.Log.d("StatusUpdate", "status screen opened=true")
                    onDismiss()
                    onOpenStatusUpdate()
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("تحديث حالة الشحنة")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق")
            }
        }
    )
}

@Composable
private fun DetailItem(label: String, value: String) {
    Column {
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
