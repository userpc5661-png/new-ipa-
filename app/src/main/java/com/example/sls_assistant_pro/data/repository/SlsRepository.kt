package com.example.sls_assistant_pro.data.repository

import com.example.sls_assistant_pro.data.local.*
import com.example.sls_assistant_pro.data.model.*
import com.example.sls_assistant_pro.data.remote.LoginRequest
import com.example.sls_assistant_pro.data.remote.SlsApiService
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.Flow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*

class SlsRepository(
    private val apiService: SlsApiService,
    private val tokenStore: TokenStore,
    private val db: AppDatabase
) {
    private val gson = Gson()

    val savedAccounts: Flow<List<SavedAccountEntity>> = db.savedAccountDao().getAllAccounts()
    val localContacts: Flow<List<LocalContactEntity>> = db.localContactDao().getAllContacts()
    val locationCorrections: Flow<List<LocationCorrectionEntity>> = db.locationCorrectionDao().getAllCorrections()

    fun getTodayHistory(): Flow<List<DeliveryHistoryEntity>> {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return db.deliveryHistoryDao().getHistoryForDate(today)
    }

    suspend fun login(email: String, pass: String): String {
        val req = LoginRequest(email = email, password = pass)
        val res = apiService.login(req)

        if (!res.isSuccessful || res.body() == null) {
            val errBody = res.errorBody()?.string() ?: ""
            throw Exception(parseErrorMessage(errBody, "فشل تسجيل الدخول (${res.code()})"))
        }

        val json = res.body()!!
        var token: String? = null
        val idsMap = mutableMapOf<String, Any?>()

        if (json.has("user") && json.get("user").isJsonObject) {
            val userObj = json.getAsJsonObject("user")
            if (userObj.has("api_token") && !userObj.get("api_token").isJsonNull) {
                token = userObj.get("api_token").asString
            }
            for (entry in userObj.entrySet()) {
                val k = entry.key.lowercase()
                if (k.contains("id") || k == "id" || k == "driver_id" || k == "assignee_id" || k == "user_id") {
                    idsMap[entry.key] = entry.value.toString().replace("\"", "")
                }
            }
        }
        if (token.isNullOrBlank() && json.has("api_token") && !json.get("api_token").isJsonNull) {
            token = json.get("api_token").asString
        }

        val cookieHeader = res.headers().get("Set-Cookie")

        if (token.isNullOrBlank() && cookieHeader.isNullOrBlank()) {
            throw Exception("نجح تسجيل الدخول ولكن لم يتم استلام رمز الجلسة.")
        }

        val sessionMap = mutableMapOf<String, Any?>("v" to 2)
        if (!token.isNullOrBlank()) sessionMap["bearer"] = token
        if (!cookieHeader.isNullOrBlank()) sessionMap["cookie"] = cookieHeader
        sessionMap["ids"] = idsMap

        val sessionJson = gson.toJson(sessionMap)
        tokenStore.saveSessionToken(sessionJson)

        // Save account locally
        db.savedAccountDao().insertAccount(SavedAccountEntity(email = email))

        return sessionJson
    }

    fun hasSavedSession(): Boolean = tokenStore.getSessionToken() != null
    fun getSavedSession(): String? = tokenStore.getSessionToken()
    fun logout() = tokenStore.clearSession()

    suspend fun removeSavedAccount(email: String) {
        db.savedAccountDao().deleteAccount(email)
    }

    fun getSessionIds(): Map<String, Any?> {
        val sessionJson = tokenStore.getSessionToken() ?: return emptyMap()
        return try {
            val map = gson.fromJson(sessionJson, Map::class.java) as? Map<String, Any?> ?: emptyMap()
            @Suppress("UNCHECKED_CAST")
            (map["ids"] as? Map<String, Any?>) ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    suspend fun fetchTasks(lat: Double, lng: Double): List<TaskItem> {
        val token = tokenStore.getBearerToken()
            ?: throw Exception("جلسة الدخول مفقودة. يرجى تسجيل الدخول.")
        val cookie = tokenStore.getCookie()

        val response = apiService.getTasks(
            apiToken = token,
            lat = lat.toString(),
            lng = lng.toString(),
            cookie = cookie
        )

        if (!response.isSuccessful || response.body() == null) {
            val err = response.errorBody()?.string() ?: ""
            throw Exception(parseErrorMessage(err, "تعذر جلب المهام (${response.code()})"))
        }

        val body = response.body()!!
        val taskRows = extractTaskRows(body)

        val taskItems = mutableListOf<TaskItem>()
        var printedFirstTask = false
        for (row in taskRows) {
            val map = gson.fromJson(row, Map::class.java) as? Map<String, Any?> ?: continue
            val item = TaskItem.fromMap(map)
            if (item.id.isNotBlank() || item.referenceNumber.isNotBlank() || item.customerName.isNotBlank()) {
                taskItems.add(item)
                if (!printedFirstTask) {
                    ShipmentFieldMapper.printDiagnosticLog("RAW TASK", map, item)
                    printedFirstTask = true
                }
            }
        }

        return taskItems
    }

    suspend fun setLocalContactStatus(taskKey: String, status: String, notes: String = "") {
        db.localContactDao().setContact(
            LocalContactEntity(taskKey = taskKey, status = status, notes = notes)
        )
    }

    suspend fun saveLocationCorrection(refNumber: String, lat: Double, lng: Double) {
        db.locationCorrectionDao().saveCorrection(
            LocationCorrectionEntity(referenceNumber = refNumber, latitude = lat, longitude = lng)
        )
    }

    // ==========================================
    // SMART SCANNER APIS
    // ==========================================

    suspend fun scanOrder(awb: String): ScannedShipment {
        val token = tokenStore.getBearerToken() ?: ""
        val cookie = tokenStore.getCookie()
        val cleanAwb = awb.trim()

        val response = apiService.scanOrder(awb = cleanAwb, apiToken = token, cookie = cookie)

        if (!response.isSuccessful || response.body() == null) {
            val err = response.errorBody()?.string() ?: ""
            throw Exception(parseErrorMessage(err, "فشل البحث عن الشحنة (${response.code()})"))
        }

        val resMap = gson.fromJson(response.body()!!, Map::class.java) as Map<String, Any?>
        val orderMap = (resMap["order"] as? Map<String, Any?>) ?: resMap
        val taskItem = TaskItem.fromMap(orderMap)

        val scanned = ScannedShipment(
            id = (orderMap["id"] as? Number)?.toInt() ?: (orderMap["order_id"] as? Number)?.toInt() ?: 0,
            actualAwb = cleanAwb,
            referenceNumber = taskItem.displayReference,
            statusCode = taskItem.statusCode,
            statusLabelCode = taskItem.statusLabel,
            statusText = taskItem.statusLabel.ifBlank { taskItem.statusCode },
            customerName = taskItem.customerName,
            customerPhone = taskItem.customerPhone,
            storeName = taskItem.displayStoreName,
            paymentMethod = if (taskItem.isCashOnDelivery) "COD" else "Prepaid",
            amount = if (taskItem.codAmount != null) "${taskItem.codAmount} SAR" else "Prepaid",
            address = taskItem.address,
            task = taskItem,
            raw = resMap
        )

        ShipmentFieldMapper.printDiagnosticLog("RAW SCAN", resMap, taskItem)
        return scanned
    }

    suspend fun scanOrderGroup(groupCode: String): ScannedOrderGroup {
        val token = tokenStore.getBearerToken() ?: ""
        val cookie = tokenStore.getCookie()
        val cleanGroup = groupCode.trim()

        val response = apiService.scanOrderGroup(groupId = cleanGroup, apiToken = token, cookie = cookie)

        if (!response.isSuccessful || response.body() == null) {
            val err = response.errorBody()?.string() ?: ""
            throw Exception(parseErrorMessage(err, "فشل العثور على مجموعة الطلبات (${response.code()})"))
        }

        val resMap = gson.fromJson(response.body()!!, Map::class.java) as Map<String, Any?>
        val groupObj = (resMap["order_group"] as? Map<String, Any?>) ?: resMap
        val ordersList = (groupObj["orders"] as? List<*>) ?: emptyList<Any>()

        val orders = mutableListOf<GroupOrder>()
        for (item in ordersList) {
            if (item is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                val m = item as Map<String, Any?>
                val pivot = m["pivot"] as? Map<String, Any?>
                val confirmStatus = (pivot?.get("confirm_status") ?: m["confirm_status"] ?: "").toString().trim()
                val isConf = confirmStatus == "1" || confirmStatus.equals("true", ignoreCase = true) ||
                        confirmStatus.contains("confirm", ignoreCase = true) || confirmStatus.contains("تأكيد")
                orders.add(
                    GroupOrder(
                        id = (m["id"] as? Number)?.toInt(),
                        orderId = (m["order_id"] ?: m["id"] ?: "").toString(),
                        referenceNumber = (m["order_awb"] ?: m["reference_no"] ?: m["awb"] ?: "").toString(),
                        confirmStatus = confirmStatus,
                        isConfirmed = isConf
                    )
                )
            }
        }

        return ScannedOrderGroup(
            id = (groupObj["id"] as? Number)?.toInt() ?: cleanGroup.toIntOrNull() ?: 0,
            orders = orders,
            raw = resMap
        )
    }

    suspend fun confirmOrderInGroup(groupId: Int, orderId: Int, orderAwb: String): ScanActionResult {
        val token = tokenStore.getBearerToken() ?: ""
        val cookie = tokenStore.getCookie()

        val bodyMap = mapOf(
            "group_id" to groupId,
            "order_id" to orderId,
            "order_awb" to orderAwb,
            "api_token" to token
        )
        val jsonStr = gson.toJson(bodyMap)
        val reqBody = jsonStr.toRequestBody("application/json".toMediaTypeOrNull())

        val response = apiService.confirmOrder(body = reqBody, cookie = cookie)
        if (!response.isSuccessful || response.body() == null) {
            val err = response.errorBody()?.string() ?: ""
            throw Exception(parseErrorMessage(err, "فشل تأكيد الشحنة (${response.code()})"))
        }

        val body = response.body()!!
        val success = body.has("success") && (body.get("success").asString == "true" || body.get("success").asString == "1" || body.get("success").asBoolean)
        val msg = if (body.has("message")) body.get("message").asString else "تم تأكيد الشحنة بنجاح"
        return ScanActionResult(success = success, message = msg, statusCode = response.code(), rawResponse = body)
    }

    suspend fun moveOrderGroupToOfd(groupId: Int): ScanActionResult {
        val token = tokenStore.getBearerToken() ?: ""
        val cookie = tokenStore.getCookie()

        val response = apiService.moveOrderGroupToOfd(orderGroupId = groupId, apiToken = token, cookie = cookie)
        if (!response.isSuccessful || response.body() == null) {
            val err = response.errorBody()?.string() ?: ""
            throw Exception(parseErrorMessage(err, "فشل تحويل المجموعة إلى OFD (${response.code()})"))
        }

        val body = response.body()!!
        val success = body.has("success") && (body.get("success").asString == "true" || body.get("success").asString == "1" || body.get("success").asBoolean)
        val msg = if (body.has("message")) body.get("message").asString else "تم تحويل المجموعة للتوصيل بنجاح"
        return ScanActionResult(success = success, message = msg, statusCode = response.code(), rawResponse = body)
    }

    suspend fun scanLinehaulGroup(groupCode: String): LinehaulGroup {
        val token = tokenStore.getBearerToken() ?: ""
        val cookie = tokenStore.getCookie()
        val cleanGroup = groupCode.trim()

        val response = apiService.scanLinehaulGroup(groupId = cleanGroup, apiToken = token, cookie = cookie)
        if (!response.isSuccessful || response.body() == null) {
            val err = response.errorBody()?.string() ?: ""
            throw Exception(parseErrorMessage(err, "فشل العثور على مجموعة Linehaul (${response.code()})"))
        }

        val resMap = gson.fromJson(response.body()!!, Map::class.java) as Map<String, Any?>
        val groupObj = (resMap["group"] as? Map<String, Any?>) ?: resMap
        val originHub = groupObj["origin_hub"] as? Map<String, Any?>
        val destHub = groupObj["destination_hub"] as? Map<String, Any?>
        val ordersList = (groupObj["orders"] as? List<*>) ?: emptyList<Any>()

        val orders = mutableListOf<LinehaulOrder>()
        for (item in ordersList) {
            if (item is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                val m = item as Map<String, Any?>
                orders.add(
                    LinehaulOrder(
                        id = (m["id"] as? Number)?.toInt(),
                        orderId = (m["order_id"] ?: m["id"] ?: "").toString(),
                        status = (m["status"] ?: "").toString(),
                        statusLabel = (m["status_label"] ?: "").toString(),
                        referenceNumber = (m["reference_no"] ?: m["order_awb"] ?: m["awb"] ?: "").toString()
                    )
                )
            }
        }

        return LinehaulGroup(
            id = (groupObj["id"] as? Number)?.toInt() ?: cleanGroup.toIntOrNull() ?: 0,
            status = (groupObj["status"] ?: "").toString(),
            originHubName = (originHub?.get("name") ?: "").toString(),
            destinationHubName = (destHub?.get("name") ?: "").toString(),
            orders = orders,
            raw = resMap
        )
    }

    suspend fun dispatchLinehaulGroups(groupIds: List<Int>): ScanActionResult {
        val token = tokenStore.getBearerToken() ?: ""
        val cookie = tokenStore.getCookie()

        val bodyMap = mapOf(
            "group_id" to groupIds,
            "api_token" to token
        )
        val jsonStr = gson.toJson(bodyMap)
        val reqBody = jsonStr.toRequestBody("application/json".toMediaTypeOrNull())

        val response = apiService.dispatchLinehaulGroups(body = reqBody, cookie = cookie)
        if (!response.isSuccessful || response.body() == null) {
            val err = response.errorBody()?.string() ?: ""
            throw Exception(parseErrorMessage(err, "فشل إرسال المسار (${response.code()})"))
        }

        val body = response.body()!!
        val success = body.has("success") && (body.get("success").asString == "true" || body.get("success").asString == "1" || body.get("success").asBoolean)
        val msg = if (body.has("message")) body.get("message").asString else "تم إرسال المسار بنجاح"
        return ScanActionResult(success = success, message = msg, statusCode = response.code(), rawResponse = body)
    }

    suspend fun receiveLinehaulGroups(groupIds: List<Int>): ScanActionResult {
        val token = tokenStore.getBearerToken() ?: ""
        val cookie = tokenStore.getCookie()

        val bodyMap = mapOf(
            "group_id" to groupIds,
            "api_token" to token
        )
        val jsonStr = gson.toJson(bodyMap)
        val reqBody = jsonStr.toRequestBody("application/json".toMediaTypeOrNull())

        val response = apiService.receiveLinehaulGroups(body = reqBody, cookie = cookie)
        if (!response.isSuccessful || response.body() == null) {
            val err = response.errorBody()?.string() ?: ""
            throw Exception(parseErrorMessage(err, "فشل استلام المسار (${response.code()})"))
        }

        val body = response.body()!!
        val success = body.has("success") && (body.get("success").asString == "true" || body.get("success").asString == "1" || body.get("success").asBoolean)
        val msg = if (body.has("message")) body.get("message").asString else "تم استلام المسار بنجاح"
        return ScanActionResult(success = success, message = msg, statusCode = response.code(), rawResponse = body)
    }

    // ==========================================
    // STATUS UPDATE & DRIVER STATUSES DISCOVERY
    // ==========================================

    suspend fun fetchDriverStatuses(task: TaskItem): List<Map<String, Any?>> {
        val token = tokenStore.getBearerToken() ?: ""
        val cookie = tokenStore.getCookie()

        // 1. Authoritative check inside task raw data
        var options = extractOptions(task.rawMap)
        if (options.isNotEmpty()) return options

        // 2. Scan Order API to get authoritative options
        try {
            val awb = if (task.realAwb.isNotBlank()) task.realAwb else task.referenceNumber
            if (awb.isNotBlank()) {
                val scan = scanOrder(awb)
                options = extractOptions(scan.raw)
                if (options.isNotEmpty()) return options
            }
        } catch (_: Exception) {}

        // 3. Statuses Discovery API (Official endpoints & query params)
        try {
            val res1 = apiService.getDriverStatuses(
                apiToken = token,
                currentStatus = task.statusId?.toString() ?: task.statusCode,
                currentStatusLabel = task.statusLabel,
                currentIsRvp = task.isRvp,
                currentOrderType = task.orderTypeId?.toString() ?: task.orderType,
                cookie = cookie
            )
            if (res1.isSuccessful && res1.body() != null) {
                options = extractOptions(gson.fromJson(res1.body()!!, Map::class.java) as Map<String, Any?>)
                if (options.isNotEmpty()) return options
            }
        } catch (_: Exception) {}

        try {
            val res2 = apiService.getDriverStatusesWithoutScan(
                apiToken = token,
                currentStatus = task.statusId?.toString() ?: task.statusCode,
                currentStatusLabel = task.statusLabel,
                currentIsRvp = task.isRvp,
                currentOrderType = task.orderTypeId?.toString() ?: task.orderType,
                cookie = cookie
            )
            if (res2.isSuccessful && res2.body() != null) {
                options = extractOptions(gson.fromJson(res2.body()!!, Map::class.java) as Map<String, Any?>)
                if (options.isNotEmpty()) return options
            }
        } catch (_: Exception) {}

        // 4. Clean Official SLS Standard Fallback
        return getDefaultDriverStatuses()
    }

    private fun getDefaultDriverStatuses(): List<Map<String, Any?>> {
        return listOf(
            mapOf("status_id" to 3, "id" to 3, "value" to "Delivered", "text" to "تم التسليم", "status_text" to "Delivered"),
            mapOf("status_id" to 6, "id" to 1, "value" to "Consignee is not answering", "text" to "العميل لا يجيب", "status_text" to "Failed to Attempt"),
            mapOf("status_id" to 6, "id" to 2, "value" to "Consignee refused the shipment", "text" to "العميل رفض الشحنة", "status_text" to "Failed to Attempt"),
            mapOf("status_id" to 6, "id" to 3, "value" to "Unclear National Address", "text" to "العنوان الوطني غير واضح", "status_text" to "Failed to Attempt"),
            mapOf("status_id" to 6, "id" to 4, "value" to "Consignee wrong number", "text" to "رقم العميل خاطئ", "status_text" to "Failed to Attempt"),
            mapOf("status_id" to 6, "id" to 5, "value" to "consignee reschedule the delivery", "text" to "العميل أعاد جدولة الاستلام", "status_text" to "Failed to Attempt")
        )
    }

    suspend fun updateShipmentStatus(
        statusId: Any,
        statusLabel: String,
        awb: String,
        imageFile: File? = null,
        rescheduleDate: String? = null,
        codPaymentMethod: String? = null,
        customerCodPaymentId: String? = null,
        nationalAddress: String? = null,
        lat: Double? = null,
        lng: Double? = null,
        taskItem: TaskItem? = null
    ): Boolean {
        val token = tokenStore.getBearerToken() ?: ""
        val cookie = tokenStore.getCookie()
        val sessionIds = getSessionIds()

        val assigneeId = taskItem?.assigneeId
            ?: sessionIds["assignee_id"]
            ?: sessionIds["driver_id"]
            ?: sessionIds["id"]
            ?: sessionIds["user_id"]

        val partsMap = mutableMapOf<String, RequestBody>()

        fun addPart(key: String, value: String) {
            partsMap[key] = value.toRequestBody("text/plain".toMediaTypeOrNull())
        }

        addPart("api_token", token)
        addPart("status", statusId.toString())
        addPart("status_label", statusLabel)
        addPart("awbs[0]", awb.trim())

        if (assigneeId != null) {
            addPart("assignee_id", assigneeId.toString())
        }
        if (lat != null) addPart("lat", lat.toString())
        if (lng != null) addPart("lng", lng.toString())

        if (!rescheduleDate.isNullOrBlank()) {
            addPart("reschedule_date", rescheduleDate)
        }
        if (!codPaymentMethod.isNullOrBlank()) {
            addPart("cod_payment_method", codPaymentMethod)
        }
        if (!customerCodPaymentId.isNullOrBlank()) {
            addPart("customer_cod_payment_id", customerCodPaymentId)
        }
        if (!nationalAddress.isNullOrBlank()) {
            addPart("new_address_details", nationalAddress)
        }

        var imagePart: MultipartBody.Part? = null
        if (imageFile != null && imageFile.exists()) {
            val requestFile = imageFile.asRequestBody("image/*".toMediaTypeOrNull())
            imagePart = MultipartBody.Part.createFormData("poc_attachment", imageFile.name, requestFile)
        }

        val response = apiService.updateStatusMultipart(
            parts = partsMap,
            image = imagePart,
            cookie = cookie
        )

        if (!response.isSuccessful || response.body() == null) {
            val err = response.errorBody()?.string() ?: ""
            throw Exception(parseErrorMessage(err, "فشل تحديث الحالة (${response.code()})"))
        }

        val body = response.body()!!
        val isSuccess = body.has("success") && (
                body.get("success").asString == "true" ||
                        body.get("success").asString == "1" ||
                        (body.get("success").isJsonPrimitive && body.get("success").asBoolean)
                )

        if (!isSuccess && body.has("message")) {
            val msg = body.get("message").asString
            if (msg.isNotBlank() && !msg.equals("true", ignoreCase = true)) {
                throw Exception(msg)
            }
        }

        // If national address was updated, send location update
        if (!nationalAddress.isNullOrBlank() && lat != null && lng != null) {
            try {
                val encodedLoc = URLEncoder.encode(nationalAddress, "UTF-8")
                val locMap = mapOf("latitude" to lat, "longitude" to lng, "api_token" to token)
                val locBody = gson.toJson(locMap).toRequestBody("application/json".toMediaTypeOrNull())
                apiService.addPickupLocation(location = encodedLoc, body = locBody, cookie = cookie)
            } catch (_: Exception) {}
        }

        // Record history if delivered
        val lowerLabel = statusLabel.lowercase()
        if (lowerLabel.contains("delivered") || lowerLabel.contains("تسليم") || lowerLabel.contains("توصيل")) {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            db.deliveryHistoryDao().recordDelivery(
                record = DeliveryHistoryEntity(
                    awb = awb,
                    customerName = taskItem?.customerName ?: awb,
                    codAmount = taskItem?.codAmount ?: 0.0,
                    collected = true,
                    dateString = today
                )
            )
        }

        return true
    }

    private fun parseErrorMessage(raw: String, fallback: String): String {
        return try {
            val obj = gson.fromJson(raw, JsonObject::class.java)
            when {
                obj.has("message") -> obj.get("message").asString
                obj.has("error") -> obj.get("error").asString
                else -> fallback
            }
        } catch (_: Exception) {
            if (raw.isNotBlank()) raw else fallback
        }
    }

    private fun extractTaskRows(body: JsonObject): List<JsonObject> {
        val list = mutableListOf<JsonObject>()
        fun walk(element: Any) {
            if (element is JsonObject) {
                if (element.has("tasks") && element.get("tasks").isJsonArray) {
                    val arr = element.getAsJsonArray("tasks")
                    for (item in arr) {
                        if (item.isJsonObject) list.add(item.asJsonObject)
                    }
                    return
                }
                if (element.has("orders") && element.get("orders").isJsonArray) {
                    val arr = element.getAsJsonArray("orders")
                    for (item in arr) {
                        if (item.isJsonObject) list.add(item.asJsonObject)
                    }
                    return
                }
                for (entry in element.entrySet()) {
                    walk(entry.value)
                }
            }
        }
        walk(body)
        return list
    }

    private fun extractOptions(source: Map<String, Any?>): List<Map<String, Any?>> {
        val rawResults = mutableListOf<Map<String, Any?>>()

        fun addOption(statusNode: Map<String, Any?>, labelNode: Map<String, Any?>) {
            val statusId = statusNode["id"] ?: statusNode["status_id"] ?: labelNode["status_id"] ?: labelNode["status"]
            val statusText = (statusNode["text"] ?: statusNode["name"] ?: statusNode["status"] ?: "").toString()
            val labelId = labelNode["id"] ?: labelNode["status_label_id"] ?: labelNode["reason_id"] ?: labelNode["label_id"]

            val rawDisplay = (labelNode["text"] ?: labelNode["name"] ?: labelNode["label"] ?: labelNode["title"] ?: labelNode["status_label"] ?: "").toString()
            val rawValue = (labelNode["value"] ?: labelNode["status_label"] ?: labelNode["text"] ?: labelNode["name"] ?: "").toString()

            val displayLabel = translateStatusLabel(rawDisplay.ifBlank { rawValue })
            val apiValue = rawValue.ifBlank { rawDisplay }

            if (statusId != null && displayLabel.isNotBlank()) {
                val item = mutableMapOf<String, Any?>()
                item.putAll(labelNode)
                item["status_id"] = statusId
                item["status_text"] = statusText
                item["id"] = labelId ?: statusId
                item["status_label_id"] = labelId
                item["text"] = displayLabel
                item["value"] = apiValue
                rawResults.add(item)
            }
        }

        fun walk(node: Any?) {
            if (node is List<*>) {
                for (item in node) walk(item)
                return
            }
            if (node !is Map<*, *>) return

            @Suppress("UNCHECKED_CAST")
            val map = node as Map<String, Any?>

            val labels = map["driver_status_labels"] as? List<*>
                ?: map["status_labels"] as? List<*>
                ?: map["labels"] as? List<*>
                ?: map["reasons"] as? List<*>

            if (labels != null) {
                for (label in labels) {
                    if (label is Map<*, *>) {
                        @Suppress("UNCHECKED_CAST")
                        addOption(map, label as Map<String, Any?>)
                    }
                }
            }

            for (entry in map.entries) {
                val key = entry.key.toString()
                if (key in setOf("driver_status_labels", "status_labels", "labels", "reasons")) continue
                if (entry.value is Map<*, *> || entry.value is List<*>) {
                    walk(entry.value)
                }
            }
        }

        walk(source)

        // Deduplicate
        val unique = mutableMapOf<String, Map<String, Any?>>()
        for (item in rawResults) {
            val key = "${item["status_id"]}|${item["status_label_id"]}|${item["text"]}"
            if (!unique.containsKey(key)) {
                unique[key] = item
            }
        }

        // Sort with Delivered on top
        return unique.values.sortedWith(Comparator { a, b ->
            val aDel = isDeliveredOption(a)
            val bDel = isDeliveredOption(b)
            if (aDel != bDel) if (aDel) -1 else 1
            else (a["text"]?.toString() ?: "").compareTo(b["text"]?.toString() ?: "")
        })
    }

    private fun translateStatusLabel(label: String): String {
        val trimmed = label.trim()
        val lower = trimmed.lowercase()
        return when {
            lower == "delivered" || lower.contains("تم التسليم") -> "تم التسليم"
            lower.contains("consignee is not answering") || lower.contains("not answering") || lower.contains("لا يجيب") -> "العميل لا يجيب"
            lower.contains("refused") || lower.contains("رفض") -> "العميل رفض الشحنة"
            lower.contains("unclear national address") || lower.contains("national address") || lower.contains("العنوان الوطني") -> "العنوان الوطني غير واضح"
            lower.contains("wrong number") || lower.contains("رقم خاطئ") || lower.contains("wrong phone") -> "رقم العميل خاطئ"
            lower.contains("reschedule") || lower.contains("إعادة جدولة") || lower.contains("جدول") -> "العميل أعاد جدولة الاستلام"
            lower.contains("failed to attempt") || lower.contains("تعذر التوصيل") -> "تعذر التوصيل"
            else -> trimmed
        }
    }

    private fun isDeliveredOption(option: Map<String, Any?>): Boolean {
        val text = "${option["text"]} ${option["value"]}".lowercase()
        return text.contains("delivered") || text.contains("تم التسليم") || text.contains("توصيل")
    }
}

