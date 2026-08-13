package com.example.sls_assistant_pro.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sls_assistant_pro.data.model.PaymentKind
import com.example.sls_assistant_pro.data.model.TaskItem
import com.example.sls_assistant_pro.data.repository.SlsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class StatusOption(
    val id: Any,
    val labelText: String,
    val apiValue: String,
    val isDelivered: Boolean = false,
    val raw: Map<String, Any?> = emptyMap()
)

data class ShipmentStatusUiState(
    val awb: String = "",
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val currentServerStatus: String? = null,
    val currentServerStatusLabel: String? = null,
    val options: List<StatusOption> = emptyList(),
    val selectedOption: StatusOption? = null,
    val otpInput: String = "",
    val codPaymentMethod: String = "cash", // "cash" or "softpos"
    val softPosPaid: Boolean = false,
    val softPosTransactionId: String? = null,
    val nationalAddress: String = "",
    val rescheduleDate: String? = null,
    val selectedImageFile: File? = null,
    val error: String? = null,
    val isSuccess: Boolean = false
)

class ShipmentStatusViewModel(
    private val repository: SlsRepository,
    private val task: TaskItem
) : ViewModel() {

    private val initialAwb = task.realAwb.ifBlank { task.referenceNumber }

    private val _uiState = MutableStateFlow(
        ShipmentStatusUiState(awb = initialAwb)
    )
    val uiState: StateFlow<ShipmentStatusUiState> = _uiState.asStateFlow()

    init {
        loadLiveServerData(initialAwb)
    }

    fun loadLiveServerData(actualAwb: String) {
        val cleanAwb = actualAwb.trim().ifBlank { initialAwb }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, awb = cleanAwb)
            try {
                // Phase 1: Fetch Full Shipment from Server
                val liveScanned = try {
                    if (cleanAwb.isNotBlank()) repository.scanOrder(cleanAwb) else null
                } catch (e: Exception) {
                    null
                }

                val curStatus = liveScanned?.statusCode ?: task.statusCode
                val curLabel = liveScanned?.statusLabelCode ?: task.statusLabel

                android.util.Log.d("SLS_STATUS_FLOW", """
                    CURRENT SERVER STATUS:
                    awb: $cleanAwb
                    status: $curStatus
                    status_label: $curLabel
                """.trimIndent())

                // Phase 2: Fetch Driver Statuses
                val taskToUse = liveScanned?.task ?: task
                val rawOptions = repository.fetchDriverStatuses(taskToUse)

                val mapped = rawOptions.map { m ->
                    val display = (m["text"] ?: m["label"] ?: m["name"] ?: "").toString().trim()
                    val apiVal = (m["value"] ?: m["status_label"] ?: display).toString().trim()
                    val idVal = m["status_id"] ?: m["id"] ?: 3
                    val lower = "$display $apiVal".lowercase()
                    val delivered = lower.contains("delivered") || lower.contains("تسليم") || lower.contains("توصيل")
                    StatusOption(id = idVal, labelText = display, apiValue = apiVal, isDelivered = delivered, raw = m)
                }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    currentServerStatus = curStatus,
                    currentServerStatusLabel = curLabel,
                    options = mapped,
                    selectedOption = mapped.firstOrNull()
                )
            } catch (e: Exception) {
                android.util.Log.e("StatusUpdate", "Error loading status options: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "تعذر جلب خيارات الحالات من السيرفر."
                )
            }
        }
    }

    fun selectOption(option: StatusOption) {
        _uiState.value = _uiState.value.copy(
            selectedOption = option,
            otpInput = "",
            softPosPaid = false,
            softPosTransactionId = null,
            error = null
        )
    }

    fun setOtpInput(otp: String) {
        _uiState.value = _uiState.value.copy(otpInput = otp, error = null)
    }

    fun setCodPaymentMethod(method: String) {
        _uiState.value = _uiState.value.copy(codPaymentMethod = method)
    }

    fun setNationalAddress(address: String) {
        _uiState.value = _uiState.value.copy(nationalAddress = address)
    }

    fun setRescheduleDate(date: String) {
        _uiState.value = _uiState.value.copy(rescheduleDate = date, error = null)
    }

    fun setImageFile(file: File?) {
        _uiState.value = _uiState.value.copy(selectedImageFile = file, error = null)
    }

    fun startSoftPosPayment() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true)
            // NearPay / SoftPOS integration mock flow for testing
            kotlinx.coroutines.delay(1200)
            val txId = "SPOS_${System.currentTimeMillis()}"
            _uiState.value = _uiState.value.copy(
                isSubmitting = false,
                softPosPaid = true,
                softPosTransactionId = txId
            )
        }
    }

    fun submit(driverLat: Double, driverLng: Double) {
        val state = _uiState.value
        val option = state.selectedOption ?: return
        val actualAwb = state.awb.ifBlank { initialAwb }

        // 1. Check OTP Requirement (Server metadata first -> Fallback)
        val needsOtp = when {
            option.raw["otp_required"] == true || option.raw["o2b_required"] == true ||
                    option.raw["require_otp"] == true || option.raw["o2b"] == true -> true
            option.raw["otp_required"] == false || option.raw["o2b_required"] == false ||
                    option.raw["require_otp"] == false || option.raw["o2b"] == false -> false
            else -> option.isDelivered && task.paymentKind == PaymentKind.prepaid
        }

        if (needsOtp) {
            if (state.otpInput.trim().length != 4) {
                _uiState.value = state.copy(error = "أدخل رمز OTP المكون من 4 أرقام.")
                return
            }
        }

        // 2. Check Image Requirement
        val needsImage = when {
            option.raw["pod_required"] == true || option.raw["image_required"] == true ||
                    option.raw["require_attachment"] == true || option.raw["proof_required"] == true -> true
            else -> false
        }

        if (needsImage && state.selectedImageFile == null) {
            _uiState.value = state.copy(error = "يرجى إرفاق صورة إثبات لإكمال هذه الحالة.")
            return
        }

        // 3. Check Reschedule Date Requirement
        val lowerVal = "${option.labelText} ${option.apiValue}".lowercase()
        val isReschedule = lowerVal.contains("reschedule") || lowerVal.contains("تأجيل") || lowerVal.contains("جدول") ||
                option.raw["reschedule_required"] == true || option.raw["requires_date"] == true

        if (isReschedule && state.rescheduleDate.isNullOrBlank()) {
            _uiState.value = state.copy(error = "يرجى تحديد تاريخ إعادة الجدولة.")
            return
        }

        // 4. Check National Address requirement
        val isUnclearAddress = lowerVal.contains("national address") || lowerVal.contains("العنوان الوطني")
        if (isUnclearAddress && state.nationalAddress.isBlank()) {
            _uiState.value = state.copy(error = "يرجى إدخال العنوان الوطني الجديد.")
            return
        }

        if (option.isDelivered && task.paymentKind == PaymentKind.cashOnDelivery) {
            if (state.codPaymentMethod == "softpos" && !state.softPosPaid) {
                _uiState.value = state.copy(error = "أكمل عملية الدفع عبر SoftPOS أولاً.")
                return
            }
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isSubmitting = true, error = null)
            try {
                android.util.Log.d("SLS_STATUS_FLOW", """
                    SELECTED OPTION FOR SUBMISSION:
                    awb: $actualAwb
                    status_id: ${option.id}
                    status_label: ${option.apiValue}
                    text: ${option.labelText}
                    lat: $driverLat
                    lng: $driverLng
                """.trimIndent())

                val success = repository.updateShipmentStatus(
                    statusId = option.id,
                    statusLabel = option.apiValue,
                    awb = actualAwb,
                    imageFile = state.selectedImageFile,
                    rescheduleDate = if (isReschedule) state.rescheduleDate else null,
                    codPaymentMethod = if (option.isDelivered && task.isCashOnDelivery) state.codPaymentMethod else null,
                    customerCodPaymentId = state.softPosTransactionId,
                    nationalAddress = if (isUnclearAddress) state.nationalAddress else null,
                    lat = driverLat,
                    lng = driverLng,
                    taskItem = task
                )

                if (!success) {
                    _uiState.value = state.copy(
                        isSubmitting = false,
                        error = "تعذر تحديث حالة الشحنة"
                    )
                    return@launch
                }

                // Server Verification: Re-fetch order from server
                val verifiedScan = try {
                    repository.scanOrder(actualAwb)
                } catch (e: Exception) {
                    null
                }

                android.util.Log.d("SLS_STATUS_FLOW", """
                    STATUS UPDATE SUCCESS
                    AWB: $actualAwb
                    HTTP: 200
                    success: true
                    SERVER VERIFIED STATUS: ${verifiedScan?.statusCode} / ${verifiedScan?.statusLabelCode}
                """.trimIndent())

                _uiState.value = state.copy(isSubmitting = false, isSuccess = true)
            } catch (e: Exception) {
                android.util.Log.e("SLS_STATUS_FLOW", "Update failed: ${e.message}")
                _uiState.value = state.copy(
                    isSubmitting = false,
                    error = "تعذر تحديث حالة الشحنة: ${e.message}"
                )
            }
        }
    }
}
