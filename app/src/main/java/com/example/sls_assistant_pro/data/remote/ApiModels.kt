package com.example.sls_assistant_pro.data.remote

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String,
    @SerializedName("app_version") val appVersion: String = "3"
)

data class LoginResponse(
    @SerializedName("user") val user: Map<String, Any?>? = null,
    @SerializedName("api_token") val apiToken: String? = null,
    @SerializedName("message") val message: String? = null
)

data class BulkStatusRequest(
    @SerializedName("status") val status: Any,
    @SerializedName("status_label") val statusLabel: String,
    @SerializedName("awbs") val awbs: List<String>,
    @SerializedName("reschedule_date") val rescheduleDate: String? = null,
    @SerializedName("cod_payment_method") val codPaymentMethod: String? = null,
    @SerializedName("customer_cod_payment_id") val customerCodPaymentId: String? = null
)

data class PosPaymentRequest(
    @SerializedName("awb") val awb: String,
    @SerializedName("user_id") val userId: Any? = null,
    @SerializedName("amount_halalas") val amountHalalas: Int,
    @SerializedName("gateway_response") val gatewayResponse: Any? = null
)

data class PickupLocationRequest(
    @SerializedName("location") val location: String,
    @SerializedName("lat") val latitude: Double,
    @SerializedName("lng") val longitude: Double
)
