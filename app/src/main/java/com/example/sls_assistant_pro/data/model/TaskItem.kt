package com.example.sls_assistant_pro.data.model

import com.google.gson.JsonObject

enum class PaymentKind {
    cashOnDelivery,
    prepaid,
}

enum class TaskProgress {
    pending,
    completed,
}

data class TaskItem(
    val id: String = "",
    val referenceNumber: String = "",
    val realAwb: String = "",
    val officialOrderId: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val address: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val storeName: String = "",
    val statusCode: String = "",
    val statusLabel: String = "",
    val statusId: Any? = null,
    val codAmount: Double? = null,
    val isCashOnDelivery: Boolean = false,
    val paymentKind: PaymentKind = PaymentKind.prepaid,
    val progress: TaskProgress = TaskProgress.pending,
    val assigneeId: Any? = null,
    val orderTypeId: Any? = null,
    val orderType: String = "",
    val isRvp: Boolean = false,
    val deliveryOtpValue: String = "",
    val rawMap: Map<String, Any?> = emptyMap()
) {
    val displayReference: String
        get() = referenceNumber.ifBlank { id.ifBlank { "N/A" } }

    val displayStoreName: String
        get() = storeName.ifBlank { "SLS Express" }

    val hasNavigableLocation: Boolean
        get() = latitude != null && longitude != null && latitude != 0.0 && longitude != 0.0

    companion object {
        fun fromMap(map: Map<String, Any?>): TaskItem {
            return ShipmentFieldMapper.parseTaskItem(map)
        }
    }
}
