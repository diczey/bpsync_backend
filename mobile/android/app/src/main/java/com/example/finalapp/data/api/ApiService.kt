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

    @GET("readings")
    suspend fun getReadings(@Header("Authorization") token: String): Response<HealthReadingsResponse>

    @GET("profile")
    suspend fun getProfile(@Header("Authorization") token: String): Response<ProfileResponse>

    @PUT("profile")
    suspend fun updateProfile(
        @Header("Authorization") token: String,
        @Body request: ProfileUpdateRequest
    ): Response<ProfileResponse>

    @GET("ble/status")
    suspend fun getBleStatus(@Header("Authorization") token: String): Response<BLEStatusResponse>

    @POST("ble/scan")
    suspend fun scanBle(@Header("Authorization") token: String): Response<ScanResult>

    @POST("ble/start")
    suspend fun startStreaming(@Header("Authorization") token: String): Response<CommandResponse>

    @POST("ble/stop")
    suspend fun stopStreaming(@Header("Authorization") token: String): Response<CommandResponse>

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
