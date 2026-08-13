package com.example.sls_assistant_pro.data.model

import android.util.Log

object ShipmentFieldMapper {

    private fun getValueByPath(raw: Map<String, Any?>, path: String): Any? {
        val orderMap = raw["order"] as? Map<String, Any?>
        val taskMap = raw["task"] as? Map<String, Any?>
        val shipmentMap = raw["shipment"] as? Map<String, Any?>

        val mapsToTry = listOfNotNull(raw, orderMap, taskMap, shipmentMap)

        for (m in mapsToTry) {
            val parts = path.split(".")
            var current: Any? = m
            var matchFound = true

            for (part in parts) {
                if (current is Map<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    val currentMap = current as Map<String, Any?>
                    if (currentMap.containsKey(part)) {
                        current = currentMap[part]
                    } else {
                        matchFound = false
                        break
                    }
                } else {
                    matchFound = false
                    break
                }
            }

            if (matchFound && current != null) {
                val strVal = current.toString().trim()
                if (strVal.isNotBlank() && strVal != "null") {
                    return current
                }
            }
        }
        return null
    }

    fun readFirstString(raw: Map<String, Any?>, paths: List<String>): String {
        for (path in paths) {
            val value = getValueByPath(raw, path)
            if (value != null) {
                val str = value.toString().trim()
                if (str.isNotBlank() && str != "null") {
                    return str
                }
            }
        }
        return ""
    }

    fun readFirstDouble(raw: Map<String, Any?>, paths: List<String>): Double? {
        for (path in paths) {
            val value = getValueByPath(raw, path)
            if (value != null) {
                if (value is Number) return value.toDouble()
                val str = value.toString().trim()
                val parsed = str.toDoubleOrNull()
                if (parsed != null) return parsed
            }
        }
        return null
    }

    fun readFirstBool(raw: Map<String, Any?>, paths: List<String>): Boolean {
        for (path in paths) {
            val value = getValueByPath(raw, path)
            if (value != null) {
                if (value is Boolean) return value
                if (value is Number) return value.toInt() != 0
                val str = value.toString().trim().lowercase()
                if (str == "true" || str == "1" || str == "yes") return true
                if (str == "false" || str == "0" || str == "no") return false
            }
        }
        return false
    }

    fun parseTaskItem(map: Map<String, Any?>): TaskItem {
        // 1. Recipient Name (اسم العميل)
        val recipientNamePaths = listOf(
            "delivery_location_name",
            "delivery_location_contact",
            "delivery_location.name",
            "delivery_location.contact",
            "recipient_name",
            "recipient.name",
            "receiver_name",
            "receiver.name",
            "consignee_name",
            "consignee.name",
            "customer_name" // fallback only if strictly necessary
        )
        val customerName = readFirstString(map, recipientNamePaths)

        // 2. Recipient Phone (رقم العميل)
        val recipientPhonePaths = listOf(
            "delivery_phone",
            "delivery_location_phone",
            "delivery_location.phone",
            "recipient_phone",
            "recipient_mobile",
            "recipient.phone",
            "recipient.mobile",
            "receiver_phone",
            "receiver_mobile",
            "receiver.phone",
            "receiver.mobile",
            "consignee_phone",
            "consignee_mobile",
            "consignee.phone",
            "consignee.mobile"
        )
        val customerPhone = readFirstString(map, recipientPhonePaths)

        // 3. Store Name (اسم المتجر)
        val storeNamePaths = listOf(
            "collection_location_contact",
            "collection_location_name",
            "collection_location.contact",
            "collection_location.name",
            "requested_by",
            "merchant_name",
            "merchant.name",
            "store_name",
            "store.name",
            "vendor_name",
            "client_name"
        )
        val storeName = readFirstString(map, storeNamePaths)

        // 4. Address (عنوان العميل)
        val addr1 = readFirstString(map, listOf("delivery_location_address1", "delivery_location.address1"))
        val addr2 = readFirstString(map, listOf("delivery_location_address2", "delivery_location.address2"))
        val area = readFirstString(map, listOf("delivery_area_name", "delivery_location.area_name", "delivery_area"))
        val city = readFirstString(map, listOf("delivery_location_city", "delivery_location.city"))
        val postal = readFirstString(map, listOf("delivery_postal_code", "delivery_location.postal_code"))

        val addressParts = listOf(addr1, addr2, area, city, postal)
            .filter { it.isNotBlank() && it != "null" }
            .distinct()

        val fullAddress = if (addressParts.isNotEmpty()) {
            addressParts.joinToString(", ")
        } else {
            readFirstString(map, listOf("address", "consignee_address", "delivery_address", "destination"))
        }

        // 5. Coordinates (إحداثيات العميل)
        val latPaths = listOf(
            "delivery_location_lat",
            "delivery_location.lat",
            "delivery_lat",
            "consignee_lat",
            "lat",
            "latitude"
        )
        val lngPaths = listOf(
            "delivery_location_lng",
            "delivery_location.lng",
            "delivery_lng",
            "consignee_lng",
            "lng",
            "longitude"
        )
        val lat = readFirstDouble(map, latPaths)
        val lng = readFirstDouble(map, lngPaths)

        // 6. Shipment ID / Reference & Actual AWB
        val refPaths = listOf(
            "order_id",
            "outgoing_tn",
            "reference_no",
            "reference_number",
            "order_number",
            "tracking_number",
            "awb",
            "code",
            "id",
            "task_id"
        )
        val referenceNumber = readFirstString(map, refPaths)

        val awbPaths = listOf(
            "awb",
            "tracking_number",
            "outgoing_tn",
            "reference_no",
            "reference_number",
            "code"
        )
        val realAwb = readFirstString(map, awbPaths)

        val idStr = readFirstString(map, listOf("id", "task_id", "order_id"))
        val officialOrderId = readFirstString(map, listOf("order_id", "id", "task_id"))

        // 7. Status
        val statusC = readFirstString(map, listOf("status_code", "status", "state"))
        val statusL = readFirstString(map, listOf("status_label", "status_text", "status_name", "driver_status"))
        val statusIdVal = getValueByPath(map, "status_id") ?: getValueByPath(map, "id")

        // 8. COD & Payment Kind
        val cod = readFirstDouble(map, listOf("cod_amount", "cod", "total_cod", "amount"))
        val isCodFlag = readFirstBool(map, listOf("is_cod", "cash_on_delivery", "is_cash_on_delivery"))
        val isCashOnDelivery = isCodFlag || (cod != null && cod > 0)
        val paymentKind = if (isCashOnDelivery) PaymentKind.cashOnDelivery else PaymentKind.prepaid

        val isCompleted = statusL.lowercase().contains("delivered") ||
                statusL.lowercase().contains("تم التسليم") ||
                statusL.lowercase().contains("تم التوصيل")

        val otpVal = readFirstString(map, listOf("delivery_otp", "otp", "pod_code", "verification_code"))
        val assignee = getValueByPath(map, "assignee_id")
        val orderTypeVal = getValueByPath(map, "order_type_id")

        return TaskItem(
            id = idStr,
            referenceNumber = referenceNumber,
            realAwb = realAwb,
            officialOrderId = officialOrderId,
            customerName = customerName,
            customerPhone = customerPhone,
            address = fullAddress,
            latitude = lat,
            longitude = lng,
            storeName = storeName,
            statusCode = statusC,
            statusLabel = statusL,
            statusId = statusIdVal,
            codAmount = cod,
            isCashOnDelivery = isCashOnDelivery,
            paymentKind = paymentKind,
            progress = if (isCompleted) TaskProgress.completed else TaskProgress.pending,
            assigneeId = assignee,
            orderTypeId = orderTypeVal,
            orderType = readFirstString(map, listOf("order_type")),
            isRvp = readFirstBool(map, listOf("is_rvp")),
            deliveryOtpValue = otpVal,
            rawMap = map
        )
    }

    fun formatSaudiPhone(rawPhone: String): String {
        var cleaned = rawPhone.replace("+", "").replace("-", "").replace(" ", "").replace("(", "").replace(")", "").trim()
        if (cleaned.startsWith("00")) {
            cleaned = cleaned.substring(2)
        }
        if (cleaned.startsWith("05")) {
            cleaned = "966" + cleaned.substring(1)
        } else if (cleaned.startsWith("5") && cleaned.length == 9) {
            cleaned = "966" + cleaned
        }
        return cleaned
    }

    fun buildWhatsAppMessage(task: TaskItem): String {
        val sb = StringBuilder()
        sb.append("السلام عليكم ")
        sb.append(task.customerName.ifBlank { "عزيزي العميل" })
        sb.append("\n\n")
        sb.append("معك مندوب توصيل طلبك من متجر ")
        sb.append(task.displayStoreName)
        sb.append(".\n\n")
        sb.append("رقم الشحنة:\n")
        sb.append(task.displayReference)

        val cod = task.codAmount ?: 0.0
        if (task.paymentKind == PaymentKind.cashOnDelivery && cod > 0) {
            sb.append("\n\nالمبلغ المطلوب عند الاستلام:\n")
            sb.append(cod)
            sb.append(" ريال")
        }
        return sb.toString()
    }

    fun printDiagnosticLog(tag: String, rawMap: Map<String, Any?>, task: TaskItem) {
        Log.d(tag, "================ $tag ================")
        Log.d(tag, "RAW MAP: $rawMap")
        Log.d(tag, "recipientName: ${task.customerName}")
        Log.d(tag, "recipientPhone: ${task.customerPhone}")
        Log.d(tag, "merchantName: ${task.storeName}")
        Log.d(tag, "address: ${task.address}")
        Log.d(tag, "actualAwb: ${task.realAwb}")
        Log.d(tag, "displayShipmentNumber: ${task.displayReference}")
        Log.d(tag, "statusLabel: ${task.statusLabel}")
        Log.d(tag, "isCod: ${task.isCashOnDelivery}")
        Log.d(tag, "codAmount: ${task.codAmount}")
        Log.d(tag, "lat: ${task.latitude}")
        Log.d(tag, "lng: ${task.longitude}")
        Log.d(tag, "========================================")
    }
}
