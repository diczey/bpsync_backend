package com.example.bpsync.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Backend API (FastAPI) endpoint'leri.
 * bpsync-devfixed backend yapısına göre (referans alındı, backend değiştirilmedi).
 */
interface BPSyncApi {

    /** Token gerekmez; backend erişilebilir mi test için */
    @GET("health")
    suspend fun getHealth(): Response<ResponseBody>

    // --- Auth ---
    @POST(ApiConstants.AUTH_LOGIN)
    suspend fun login(@Body body: LoginRequest): Response<LoginResponse>

    @POST(ApiConstants.AUTH_REGISTER)
    suspend fun register(@Body body: RegisterRequest): Response<LoginResponse>

    @POST(ApiConstants.AUTH_LOGOUT)
    suspend fun logout(): Response<MessageResponse>

    // --- Dashboard ---
    @GET(ApiConstants.DASHBOARD_SUMMARY)
    suspend fun getDashboardSummary(): Response<DashboardResponse>

    // --- Readings ---
    @GET(ApiConstants.READINGS)
    suspend fun getReadings(@Query("limit") limit: Int = 50): Response<HealthReadingsResponse>

    @POST(ApiConstants.READINGS_PREDICT_BP)
    suspend fun predictBp(@Body body: BPPredictionRequest): Response<BPPredictionResponse>

    @POST(ApiConstants.READINGS_CALIBRATE_BP)
    suspend fun calibrateBp(@Body body: BPCalibrationRequest): Response<CalibrateResponse>

    @GET(ApiConstants.READINGS_MODEL_INFO)
    suspend fun getModelInfo(): Response<ModelInfoResponse>

    // --- Trends ---
    @GET(ApiConstants.TRENDS)
    suspend fun getTrends(@Query("period") period: String = "week"): Response<TrendResponse>

    // --- Reports ---
    @GET(ApiConstants.REPORTS_WEEKLY)
    suspend fun getWeeklyReport(@Query("week_offset") weekOffset: Int? = 0): Response<WeeklyReportResponse>

    @GET(ApiConstants.REPORTS_MONTHLY)
    suspend fun getMonthlyReport(@Query("month_offset") monthOffset: Int? = 0): Response<WeeklyReportResponse>

    // --- Notifications ---
    @GET(ApiConstants.NOTIFICATIONS)
    suspend fun getNotifications(
        @Query("limit") limit: Int? = 20,
        @Query("unread_only") unreadOnly: Boolean = false
    ): Response<NotificationsResponse>

    @PUT("${ApiConstants.NOTIFICATIONS}/{id}/read")
    suspend fun markNotificationRead(@Path("id") id: String): Response<MessageResponse>

    @PUT("${ApiConstants.NOTIFICATIONS}/read-all")
    suspend fun markAllNotificationsRead(): Response<MessageResponse>

    @DELETE("${ApiConstants.NOTIFICATIONS}/{id}")
    suspend fun deleteNotification(@Path("id") id: String): Response<MessageResponse>

    // --- Profile ---
    @GET(ApiConstants.PROFILE)
    suspend fun getProfile(): Response<ProfileResponse>

    @PUT(ApiConstants.PROFILE)
    suspend fun updateProfile(@Body body: ProfileUpdateRequest): Response<ProfileResponse>

    @DELETE(ApiConstants.PROFILE)
    suspend fun deleteProfile(): Response<MessageResponse>
}
