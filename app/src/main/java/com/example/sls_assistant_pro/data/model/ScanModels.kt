package com.example.sls_assistant_pro.data.model

data class ScanActionResult(
    val success: Boolean,
    val message: String? = null,
    val statusCode: Int? = null,
    val rawResponse: Any? = null
)

data class LinehaulGroup(
    val id: Int,
    val status: String = "",
    val originHubName: String = "",
    val destinationHubName: String = "",
    val orders: List<LinehaulOrder> = emptyList(),
    val raw: Map<String, Any?> = emptyMap()
) {
    val groupId: String get() = id.toString()
    val totalOrders: Int get() = orders.size
    val isClosed: Boolean get() = status.equals("closed", ignoreCase = true)
    val isOutToDestination: Boolean get() = status.contains("Out to Destination", ignoreCase = true) || status.contains("out", ignoreCase = true)
}

data class LinehaulOrder(
    val id: Int?,
    val orderId: String,
    val status: String,
    val statusLabel: String,
    val referenceNumber: String
)

data class ScannedOrderGroup(
    val id: Int,
    val orders: List<GroupOrder> = emptyList(),
    val raw: Map<String, Any?> = emptyMap()
) {
    val groupId: String get() = id.toString()
    val totalOrders: Int get() = orders.size
    val confirmedOrders: List<GroupOrder> get() = orders.filter { it.isConfirmed }
    val confirmedCount: Int get() = confirmedOrders.size
    val allConfirmed: Boolean get() = orders.isNotEmpty() && confirmedCount == totalOrders
}

data class GroupOrder(
    val id: Int?,
    val orderId: String,
    val referenceNumber: String,
    val confirmStatus: String,
    val isConfirmed: Boolean
)

data class ScannedShipment(
    val id: Int,
    val actualAwb: String,
    val referenceNumber: String,
    val statusCode: String,
    val statusLabelCode: String,
    val statusText: String,
    val customerName: String,
    val customerPhone: String,
    val storeName: String,
    val paymentMethod: String,
    val amount: String,
    val address: String,
    val task: TaskItem,
    val raw: Map<String, Any?> = emptyMap()
) {
    val awb: String get() = actualAwb
}

enum class ScanType {
    linehaulGroup,
    orderGroup,
    singleShipment,
}

data class ScanAttemptResult(
    val scanType: ScanType,
    val linehaulGroup: LinehaulGroup? = null,
    val orderGroup: ScannedOrderGroup? = null,
    val shipment: ScannedShipment? = null
)

