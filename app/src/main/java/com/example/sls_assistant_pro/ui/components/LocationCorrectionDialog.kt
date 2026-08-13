package com.example.sls_assistant_pro.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.sls_assistant_pro.data.model.TaskItem

@Composable
fun LocationCorrectionDialog(
    task: TaskItem,
    currentDriverLat: Double,
    currentDriverLng: Double,
    onDismiss: () -> Unit,
    onSaveCorrection: (lat: Double, lng: Double) -> Unit
) {
    var latText by remember { mutableStateOf(task.latitude?.toString() ?: currentDriverLat.toString()) }
    var lngText by remember { mutableStateOf(task.longitude?.toString() ?: currentDriverLng.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تصحيح موقع العميل محلياً") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "الشحنة: ${task.displayReference}\n" +
                            "يمكنك إدخال إحداثيات الموقع الصحيح أو استخدام موقعك الحالي كسائق.",
                    style = MaterialTheme.typography.bodyMedium
                )

                OutlinedTextField(
                    value = latText,
                    onValueChange = { latText = it },
                    label = { Text("خط العرض (Latitude)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = lngText,
                    onValueChange = { lngText = it },
                    label = { Text("خط الطول (Longitude)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedButton(
                    onClick = {
                        latText = currentDriverLat.toString()
                        lngText = currentDriverLng.toString()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("تعيين موشري الحالي كسائق")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val lat = latText.toDoubleOrNull() ?: currentDriverLat
                    val lng = lngText.toDoubleOrNull() ?: currentDriverLng
                    onSaveCorrection(lat, lng)
                    onDismiss()
                }
            ) {
                Text("حفظ الموقع")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}
