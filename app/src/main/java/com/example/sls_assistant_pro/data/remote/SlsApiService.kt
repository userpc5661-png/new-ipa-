package com.example.sls_assistant_pro.data.remote

import com.google.gson.JsonObject
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface SlsApiService {

    @POST("users/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<JsonObject>

    @GET("tasks")
    suspend fun getTasks(
        @Query("api_token") apiToken: String,
        @Query("filters") filters: String = "{\"task_type\":\"\",\"order_type\":\"\"}",
        @Query("sort") sort: String = "{\"column\":\"\",\"order\":\"\"}",
        @Query("app_version") appVersion: String = "3",
        @Query("lat") lat: String,
        @Query("lng") lng: String,
        @Header("Cookie") cookie: String? = null
    ): Response<JsonObject>

    // 1. Single Shipment Scan by AWB
    @GET("orders/awb/{awb}")
    suspend fun scanOrder(
        @Path("awb") awb: String,
        @Query("api_token") apiToken: String,
        @Query("app_version") appVersion: String = "3",
        @Header("Cookie") cookie: String? = null
    ): Response<JsonObject>

    // 2. Order Group Scan
    @GET("order-groups/{groupId}")
    suspend fun scanOrderGroup(
        @Path("groupId") groupId: String,
        @Query("api_token") apiToken: String,
        @Query("app_version") appVersion: String = "3",
        @Header("Cookie") cookie: String? = null
    ): Response<JsonObject>

    // 3. Confirm Order inside Group
    @POST("order-groups/confirm-order")
    suspend fun confirmOrder(
        @Body body: RequestBody,
        @Header("Cookie") cookie: String? = null
    ): Response<JsonObject>

    // 4. Move Order Group to OFD
    @FormUrlEncoded
    @POST("order-groups/ofd")
    suspend fun moveOrderGroupToOfd(
        @Field("order_group_id") orderGroupId: Any,
        @Field("api_token") apiToken: String,
        @Header("Cookie") cookie: String? = null
    ): Response<JsonObject>

    // 5. Linehaul Group Scan
    @GET("linehaul/group")
    suspend fun scanLinehaulGroup(
        @Query("group_id") groupId: String,
        @Query("api_token") apiToken: String,
        @Header("Cookie") cookie: String? = null
    ): Response<JsonObject>

    // 6. Receive Linehaul Groups
    @POST("linehaul/receive")
    suspend fun receiveLinehaulGroups(
        @Body body: RequestBody,
        @Header("Cookie") cookie: String? = null
    ): Response<JsonObject>

    // 7. Dispatch Linehaul Groups
    @POST("linehaul/dispatch")
    suspend fun dispatchLinehaulGroups(
        @Body body: RequestBody,
        @Header("Cookie") cookie: String? = null
    ): Response<JsonObject>

    // 8. Driver Statuses Discovery (Official params: current_status, current_status_label, current_is_rvp, current_order_type, app_version=3, api_token)
    @GET("statuses/driver-statuses")
    suspend fun getDriverStatuses(
        @Query("api_token") apiToken: String,
        @Query("current_status") currentStatus: String? = null,
        @Query("current_status_label") currentStatusLabel: String? = null,
        @Query("current_is_rvp") currentIsRvp: Boolean? = null,
        @Query("current_order_type") currentOrderType: String? = null,
        @Query("app_version") appVersion: String = "3",
        @Header("Cookie") cookie: String? = null
    ): Response<JsonObject>

    @GET("statuses/driver-statuses-without-scan")
    suspend fun getDriverStatusesWithoutScan(
        @Query("api_token") apiToken: String,
        @Query("current_status") currentStatus: String? = null,
        @Query("current_status_label") currentStatusLabel: String? = null,
        @Query("current_is_rvp") currentIsRvp: Boolean? = null,
        @Query("current_order_type") currentOrderType: String? = null,
        @Query("app_version") appVersion: String = "3",
        @Header("Cookie") cookie: String? = null
    ): Response<JsonObject>

    // 9. Update Status (Multipart/FormData)
    @Multipart
    @POST("orders/bulk/status")
    suspend fun updateStatusMultipart(
        @PartMap parts: Map<String, @JvmSuppressWildcards RequestBody>,
        @Part image: MultipartBody.Part? = null,
        @Header("Cookie") cookie: String? = null
    ): Response<JsonObject>

    // 10. Register SoftPOS Payment
    @Multipart
    @POST("orders/bulk/pos")
    suspend fun registerPosPayment(
        @PartMap parts: Map<String, @JvmSuppressWildcards RequestBody>,
        @Header("Cookie") cookie: String? = null
    ): Response<JsonObject>

    // 11. Add Pickup Location (National Address update)
    @POST("add-pickup-location/{location}")
    suspend fun addPickupLocation(
        @Path("location", encoded = true) location: String,
        @Body body: RequestBody,
        @Header("Cookie") cookie: String? = null
    ): Response<JsonObject>

    // 12. Driver Location Tracker
    @POST("driver-location/add")
    suspend fun updateDriverLocation(
        @Body body: RequestBody,
        @Header("Cookie") cookie: String? = null
    ): Response<JsonObject>

    companion object {
        const val BASE_URL = "https://sls-express.com/api/mobile/"

        fun create(): SlsApiService {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(25, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(25, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(SlsApiService::class.java)
        }
    }
}
