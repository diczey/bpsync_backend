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
    suspend fun getProfile(@Header("Authorization") token: String): Response<LoginResponse>

    @GET("ble/status")
    suspend fun getBleStatus(@Header("Authorization") token: String): Response<BLEStatusResponse>

    @POST("ble/scan")
    suspend fun scanBle(@Header("Authorization") token: String): Response<CommandResponse>
}
