package com.example.finalapp.data.api

import com.example.finalapp.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<LoginResponse>

    @GET("dashboard/summary")
    suspend fun getDashboardSummary(@Header("Authorization") token: String): Response<DashboardResponse>

    @GET("dashboard/health-status")
    suspend fun getHealthStatus(@Header("Authorization") token: String): Response<HealthStatusResponseDto>

    @GET("readings")
    suspend fun getReadings(@Header("Authorization") token: String): Response<HealthReadingsResponse>

    @GET("profile")
    suspend fun getProfile(@Header("Authorization") token: String): Response<ProfileResponse>

    @PUT("profile")
    suspend fun updateProfile(
        @Header("Authorization") token: String,
        @Body request: ProfileUpdateRequest
    ): Response<ProfileResponse>

    @GET("settings")
    suspend fun getSettings(@Header("Authorization") token: String): Response<SettingsResponse>

    @PUT("settings")
    suspend fun updateSettings(
        @Header("Authorization") token: String,
        @Body request: SettingsUpdateRequest
    ): Response<SettingsResponse>

    @GET("notifications")
    suspend fun getNotifications(
        @Header("Authorization") token: String,
        @Query("limit") limit: Int = 50,
        @Query("unread_only") unreadOnly: Boolean = false
    ): Response<NotificationsResponse>

    @PUT("notifications/{notificationId}/read")
    suspend fun markNotificationAsRead(
        @Header("Authorization") token: String,
        @Path("notificationId") notificationId: String
    ): Response<CommandResponse>

    @PUT("notifications/read-all")
    suspend fun markAllNotificationsAsRead(
        @Header("Authorization") token: String
    ): Response<CommandResponse>

    @DELETE("notifications/{notificationId}")
    suspend fun deleteNotification(
        @Header("Authorization") token: String,
        @Path("notificationId") notificationId: String
    ): Response<CommandResponse>

    @GET("reports/weekly")
    suspend fun getWeeklyReport(
        @Header("Authorization") token: String,
        @Query("week_offset") weekOffset: Int = 0
    ): Response<HealthReportResponse>

    @GET("reports/monthly")
    suspend fun getMonthlyReport(
        @Header("Authorization") token: String,
        @Query("month_offset") monthOffset: Int = 0
    ): Response<HealthReportResponse>

    @GET("readings/model-info")
    suspend fun getPredictionModelInfo(
        @Header("Authorization") token: String
    ): Response<PredictionModelInfoResponse>

    @POST("ble/mobile/frames/merged")
    suspend fun uploadBleMergedFrame(
        @Header("Authorization") token: String,
        @Body request: BleFrameUploadRequest
    ): Response<BleFrameUploadResponse>

    @POST("ble/mobile/frames/wrist")
    suspend fun uploadWristBleFrame(
        @Header("Authorization") token: String,
        @Body request: BleFrameUploadRequest
    ): Response<BleFrameUploadResponse>

    @POST("ble/mobile/frames/chest")
    suspend fun uploadChestBleFrame(
        @Header("Authorization") token: String,
        @Body request: BleFrameUploadRequest
    ): Response<BleFrameUploadResponse>

    @POST("ble/mobile/session/connected")
    suspend fun notifyMobileBleConnected(
        @Header("Authorization") token: String
    ): Response<CommandResponse>

    @POST("ble/mobile/session/connected")
    suspend fun notifyMobileDeviceConnected(
        @Header("Authorization") token: String,
        @Body request: BleSessionDeviceRequest
    ): Response<CommandResponse>

    @POST("ble/mobile/session/disconnected")
    suspend fun notifyMobileDeviceDisconnected(
        @Header("Authorization") token: String,
        @Body request: BleSessionDeviceRequest
    ): Response<CommandResponse>

    @POST("ble/mobile/session/start")
    suspend fun startMobileMeasurementSession(
        @Header("Authorization") token: String
    ): Response<CommandResponse>

    @POST("ble/mobile/session/stop")
    suspend fun stopMobileMeasurementSession(
        @Header("Authorization") token: String
    ): Response<CommandResponse>

    @GET("ble/mobile/session/status")
    suspend fun getMobileBleSessionStatus(
        @Header("Authorization") token: String
    ): Response<BleMobileSessionStatusResponse>

    @GET("trends")
    suspend fun getTrends(
        @Header("Authorization") token: String,
        @Query("period") period: String = "week"
    ): Response<TrendResponse>

    @GET("dashboard/ppg/signal")
    suspend fun getPpgSignal(@Header("Authorization") token: String): Response<PpgSignalResponse>

    @GET("dashboard/pulse")
    suspend fun getPulseData(@Header("Authorization") token: String): Response<PulseResponse>
}
