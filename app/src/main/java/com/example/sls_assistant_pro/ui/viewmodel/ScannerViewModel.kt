package com.example.sls_assistant_pro.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sls_assistant_pro.data.model.*
import com.example.sls_assistant_pro.data.repository.SlsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ScannerUiState(
    val isScanning: Boolean = true,
    val isProcessing: Boolean = false,
    val result: ScanAttemptResult? = null,
    val error: String? = null,
    val message: String? = null,
    val expectedAwbVerification: String? = null,
    val isVerified: Boolean = false,
    val groupTasks: List<TaskItem> = emptyList(),
    val confirmedAwbs: Set<String> = emptySet()
)

class ScannerViewModel(private val repository: SlsRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    fun setVerificationAwb(awb: String?) {
        _uiState.value = _uiState.value.copy(expectedAwbVerification = awb, isVerified = false)
    }

    fun confirmAwbInGroup(awb: String) {
        val clean = awb.trim()
        val currentSet = _uiState.value.confirmedAwbs.toMutableSet()
        currentSet.add(clean)
        _uiState.value = _uiState.value.copy(
            confirmedAwbs = currentSet,
            message = "تم تأكيد الشحنة: $clean"
        )
    }

    fun onBarcodeScanned(code: String) {
        val trimmed = code.trim()
        if (trimmed.isBlank() || _uiState.value.isProcessing) return

        val expected = _uiState.value.expectedAwbVerification
        if (!expected.isNullOrBlank()) {
            if (trimmed.equals(expected.trim(), ignoreCase = true)) {
                _uiState.value = _uiState.value.copy(
                    isVerified = true,
                    message = "تم التحقق من الباركود بنجاح!"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    error = "الباركود غير مطبق ($trimmed != $expected)"
                )
            }
            return
        }

        // Check if sweeping in group mode
        val currentGroup = _uiState.value.result?.orderGroup ?: _uiState.value.result?.linehaulGroup
        if (currentGroup != null && _uiState.value.groupTasks.isNotEmpty()) {
            val matchedTask = _uiState.value.groupTasks.find {
                it.displayReference.equals(trimmed, ignoreCase = true) ||
                        it.realAwb.equals(trimmed, ignoreCase = true)
            }
            if (matchedTask != null) {
                confirmAwbInGroup(matchedTask.displayReference)
                return
            }
        }

        processCode(trimmed)
    }

    private fun processCode(code: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true, error = null, message = null)
            val upper = code.uppercase()
            val isGroupHint = upper.startsWith("GRP") || upper.startsWith("GROUP") || upper.startsWith("LINEHAUL") || upper.startsWith("LH") || upper.startsWith("ROUTE")

            if (isGroupHint) {
                // Try Group Scan
                try {
                    if (upper.startsWith("LH") || upper.startsWith("LINEHAUL") || upper.startsWith("ROUTE")) {
                        val lh = repository.scanLinehaulGroup(code)
                        val tasks = extractGroupTasks(lh.raw)
                        val attempt = ScanAttemptResult(
                            scanType = ScanType.linehaulGroup,
                            linehaulGroup = lh
                        )
                        _uiState.value = _uiState.value.copy(
                            isProcessing = false,
                            result = attempt,
                            groupTasks = tasks,
                            confirmedAwbs = emptySet(),
                            message = "تم جلب مجموعة Linehaul (${tasks.size} شحنة)"
                        )
                        return@launch
                    } else {
                        val og = repository.scanOrderGroup(code)
                        val tasks = extractGroupTasks(og.raw)
                        val attempt = ScanAttemptResult(
                            scanType = ScanType.orderGroup,
                            orderGroup = og
                        )
                        _uiState.value = _uiState.value.copy(
                            isProcessing = false,
                            result = attempt,
                            groupTasks = tasks,
                            confirmedAwbs = emptySet(),
                            message = "تم جلب مجموعة الطلبات (${tasks.size} شحنة)"
                        )
                        return@launch
                    }
                } catch (_: Exception) {}
            }

            // Fallback or Direct Single Shipment Scan
            try {
                val scannedShipment = repository.scanOrder(code)
                val task = scannedShipment.task

                android.util.Log.d("SlsScan", """
                    ================ SMART SCANNER RESULT ================
                    rawScannedCode: $code
                    detectedType: SHIPMENT
                    actualAwb: ${task.realAwb.ifBlank { code }}
                    HTTP status: 200
                    success: true
                    order.status: ${task.statusCode}
                    order.status_code: ${task.statusCode}
                    order.status_label: ${task.statusLabel}
                    ======================================================
                """.trimIndent())

                val attempt = ScanAttemptResult(
                    scanType = ScanType.singleShipment,
                    shipment = scannedShipment
                )
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    result = attempt,
                    message = "تم جلب بيانات الشحنة بنجاح"
                )
            } catch (e: Exception) {
                // Try Group scan as fallback before giving error
                try {
                    val og = repository.scanOrderGroup(code)
                    val tasks = extractGroupTasks(og.raw)
                    val attempt = ScanAttemptResult(
                        scanType = ScanType.orderGroup,
                        orderGroup = og
                    )
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        result = attempt,
                        groupTasks = tasks,
                        confirmedAwbs = emptySet(),
                        message = "تم جلب مجموعة الطلبات (${tasks.size} شحنة)"
                    )
                    return@launch
                } catch (_: Exception) {}

                val errMessage = e.message ?: "تعذر قراءة الباركود."
                android.util.Log.d("SlsScan", """
                    ================ SMART SCANNER RESULT ================
                    rawScannedCode: $code
                    detectedType: UNKNOWN
                    actualAwb: $code
                    HTTP status: Error
                    success: false
                    error: $errMessage
                    ======================================================
                """.trimIndent())

                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    error = errMessage
                )
            }
        }
    }

    private fun extractGroupTasks(map: Map<String, Any?>): List<TaskItem> {
        val items = mutableListOf<TaskItem>()
        fun walk(obj: Any?) {
            if (obj is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                val m = obj as Map<String, Any?>
                val orders = m["orders"] as? List<*> ?: m["tasks"] as? List<*> ?: m["shipments"] as? List<*>
                if (orders != null) {
                    for (o in orders) {
                        if (o is Map<*, *>) {
                            @Suppress("UNCHECKED_CAST")
                            val taskMap = o as Map<String, Any?>
                            val item = TaskItem.fromMap(taskMap)
                            if (item.id.isNotBlank() || item.referenceNumber.isNotBlank() || item.customerName.isNotBlank()) {
                                items.add(item)
                            }
                        }
                    }
                } else {
                    for (v in m.values) walk(v)
                }
            } else if (obj is List<*>) {
                for (item in obj) walk(item)
            }
        }
        walk(map)
        return items
    }

    fun reset() {
        _uiState.value = ScannerUiState()
    }
}
