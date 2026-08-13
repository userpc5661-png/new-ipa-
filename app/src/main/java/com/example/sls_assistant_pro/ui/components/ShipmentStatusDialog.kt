package com.example.sls_assistant_pro.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sls_assistant_pro.data.model.PaymentKind
import com.example.sls_assistant_pro.data.model.TaskItem
import com.example.sls_assistant_pro.data.repository.SlsRepository
import com.example.sls_assistant_pro.ui.theme.SLSBlue
import com.example.sls_assistant_pro.ui.theme.SLSOrange
import com.example.sls_assistant_pro.ui.viewmodel.ShipmentStatusViewModel
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShipmentStatusDialog(
    task: TaskItem,
    repository: SlsRepository,
    driverLat: Double,
    driverLng: Double,
    onDismiss: () -> Unit,
    onStatusUpdated: () -> Unit
) {
    val context = LocalContext.current
    val viewModel = remember(task) { ShipmentStatusViewModel(repository, task) }
    val state by viewModel.uiState.collectAsState()

    var statusDropdownExpanded by remember { mutableStateOf(false) }

    // Camera Launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            try {
                val file = File(context.cacheDir, "proof_cam_${System.currentTimeMillis()}.jpg")
                val fos = FileOutputStream(file)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos)
                fos.flush()
                fos.close()
                viewModel.setImageFile(file)
            } catch (e: Exception) {
                android.util.Log.e("StatusDialog", "Error saving camera bitmap: ${e.message}")
            }
        }
    }

    // Gallery Launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val file = File(context.cacheDir, "proof_gal_${System.currentTimeMillis()}.jpg")
                    val fos = FileOutputStream(file)
                    inputStream.copyTo(fos)
                    inputStream.close()
                    fos.flush()
                    fos.close()
                    viewModel.setImageFile(file)
                }
            } catch (e: Exception) {
                android.util.Log.e("StatusDialog", "Error copying gallery uri: ${e.message}")
            }
        }
    }

    LaunchedEffect(state.isSuccess) {
        if (state.isSuccess) {
            onStatusUpdated()
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "تحديث حالة الشحنة",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${task.displayReference} | ${task.customerName.ifBlank { "عميل" }}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            if (state.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator()
                        Text("جاري جلب خيارات الحالة من السيرفر...", fontSize = 12.sp)
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Current Status Info
                    if (!state.currentServerStatusLabel.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "الحالة الحالية: ${state.currentServerStatusLabel}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    // Status Dropdown
                    Text("اختر الحالة الجديدة:", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                    ExposedDropdownMenuBox(
                        expanded = statusDropdownExpanded,
                        onExpandedChange = { statusDropdownExpanded = !statusDropdownExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = state.selectedOption?.labelText ?: "اختر الحالة",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusDropdownExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        ExposedDropdownMenu(
                            expanded = statusDropdownExpanded,
                            onDismissRequest = { statusDropdownExpanded = false }
                        ) {
                            state.options.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt.labelText) },
                                    onClick = {
                                        viewModel.selectOption(opt)
                                        statusDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    val currentOpt = state.selectedOption
                    val lowerVal = "${currentOpt?.labelText ?: ""} ${currentOpt?.apiValue ?: ""}".lowercase()

                    // Image requirement
                    val isImageRequired = currentOpt?.raw?.get("pod_required") == true ||
                            currentOpt?.raw?.get("image_required") == true ||
                            currentOpt?.raw?.get("require_attachment") == true ||
                            currentOpt?.raw?.get("proof_required") == true

                    // OTP requirement
                    val isOtpRequired = when {
                        currentOpt?.raw?.get("otp_required") == true || currentOpt?.raw?.get("o2b_required") == true ||
                                currentOpt?.raw?.get("require_otp") == true || currentOpt?.raw?.get("o2b") == true -> true
                        currentOpt?.raw?.get("otp_required") == false || currentOpt?.raw?.get("o2b_required") == false ||
                                currentOpt?.raw?.get("require_otp") == false || currentOpt?.raw?.get("o2b") == false -> false
                        else -> currentOpt != null && currentOpt.isDelivered && task.paymentKind == PaymentKind.prepaid
                    }

                    // Reschedule requirement
                    val isRescheduleRequired = lowerVal.contains("reschedule") || lowerVal.contains("تأجيل") ||
                            lowerVal.contains("جدول") || currentOpt?.raw?.get("reschedule_required") == true ||
                            currentOpt?.raw?.get("requires_date") == true

                    // National address requirement
                    val isUnclearNationalAddress = lowerVal.contains("national address") || lowerVal.contains("العنوان الوطني")

                    // National Address Input (if unclear address)
                    if (isUnclearNationalAddress) {
                        Text("العنوان الوطني الجديد *:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        OutlinedTextField(
                            value = state.nationalAddress,
                            onValueChange = { viewModel.setNationalAddress(it) },
                            placeholder = { Text("مثال: الرياض - حي الملقا - 1234") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    // Reschedule Date
                    if (isRescheduleRequired) {
                        Text("تاريخ إعادة الجدولة (YYYY-MM-DD) *:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        OutlinedTextField(
                            value = state.rescheduleDate ?: "",
                            onValueChange = { viewModel.setRescheduleDate(it) },
                            placeholder = { Text("2026-08-15") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    // OTP Input
                    if (isOtpRequired) {
                        Text("رمز التحقق OTP (4 أرقام) *:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        OutlinedTextField(
                            value = state.otpInput,
                            onValueChange = { if (it.length <= 4) viewModel.setOtpInput(it) },
                            placeholder = { Text("مثال: 1234") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    // COD Payment Method (if delivered and cash on delivery)
                    if (currentOpt != null && currentOpt.isDelivered && task.isCashOnDelivery) {
                        Text("طريقة تحصيل COD (${task.codAmount} ريال):", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = state.codPaymentMethod == "cash",
                                onClick = { viewModel.setCodPaymentMethod("cash") },
                                label = { Text("نقداً (كاش)") },
                                leadingIcon = { Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = state.codPaymentMethod == "softpos",
                                onClick = { viewModel.setCodPaymentMethod("softpos") },
                                label = { Text("SoftPOS (مدى)") },
                                leadingIcon = { Icon(Icons.Default.CreditCard, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (state.codPaymentMethod == "softpos") {
                            if (state.softPosPaid) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "تم التحصيل بنجاح عبر SoftPOS: ${state.softPosTransactionId}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            } else {
                                Button(
                                    onClick = { viewModel.startSoftPosPayment() },
                                    enabled = !state.isSubmitting,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    if (state.isSubmitting) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp))
                                    } else {
                                        Text("تمرير بطاقة العميل SoftPOS (${task.codAmount} ريال)")
                                    }
                                }
                            }
                        }
                    }

                    // Image Attachment
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (isImageRequired) "إرفاق صورة إثبات (مطلوب *):" else "إرفاق صورة إثبات (اختياري):",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (isImageRequired) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )

                        if (state.selectedImageFile != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = state.selectedImageFile!!.name,
                                        fontSize = 12.sp,
                                        maxLines = 1
                                    )
                                }

                                IconButton(onClick = { viewModel.setImageFile(null) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "حذف الصورة", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { cameraLauncher.launch() },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("الكاميرا", fontSize = 12.sp)
                                }

                                OutlinedButton(
                                    onClick = { galleryLauncher.launch("image/*") },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("المعرض", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // Error text
                    if (!state.error.isNullOrBlank()) {
                        Text(
                            text = state.error!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { viewModel.submit(driverLat, driverLng) },
                enabled = !state.isLoading && !state.isSubmitting && state.selectedOption != null,
                shape = RoundedCornerShape(8.dp)
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("إرسال التحديث")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}
