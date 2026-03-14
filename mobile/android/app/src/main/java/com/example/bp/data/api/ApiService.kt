package com.example.bp.data.api

import com.example.bp.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // Auth
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<LoginResponse>

    @POST("auth/logout")
    suspend fun logout(@Header("Authorization") token: String): Response<Map<String, Any>>

    // Dashboard
    @GET("dashboard/summary")
    suspend fun getDashboardSummary(
        @Header("Authorization") token: String
    ): Response<DashboardResponse>

    // Readings
    @GET("readings")
    suspend fun getReadings(
        @Header("Authorization") token: String
    ): Response<HealthReadingsResponse>

    @POST("readings")
    suspend fun addReading(
        @Header("Authorization") token: String,
        @Body reading: HealthReadingCreate
    ): Response<Map<String, Any>>

    // Trends
    @GET("trends")
    suspend fun getTrends(
        @Header("Authorization") token: String,
        @Query("period") period: String = "week"
    ): Response<TrendResponse>

    // Reports
    @GET("reports/weekly")
    suspend fun getWeeklyReport(
        @Header("Authorization") token: String,
        @Query("week_offset") offset: Int = 0
    ): Response<WeeklyReportResponse>

    @GET("reports/monthly")
    suspend fun getMonthlyReport(
        @Header("Authorization") token: String,
        @Query("month_offset") offset: Int = 0
    ): Response<WeeklyReportResponse>

    // Profile
    @GET("profile")
    suspend fun getProfile(
        @Header("Authorization") token: String
    ): Response<ProfileResponse>

    @PUT("profile")
    suspend fun updateProfile(
        @Header("Authorization") token: String,
        @Body profile: ProfileUpdateRequest
    ): Response<ProfileResponse>

    // BLE
    @GET("ble/status")
    suspend fun getBleStatus(
        @Header("Authorization") token: String
    ): Response<BLEStatusResponse>

    @POST("ble/scan")
    suspend fun scanBle(
        @Header("Authorization") token: String
    ): Response<ScanResult>

    @POST("ble/start")
    suspend fun startBleStreaming(
        @Header("Authorization") token: String
    ): Response<CommandResponse>

    @POST("ble/stop")
    suspend fun stopBleStreaming(
        @Header("Authorization") token: String
    ): Response<CommandResponse>
}
