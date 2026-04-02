package com.example.finalapp.data.model

import com.google.gson.annotations.SerializedName

// ─── Auth ───
data class LoginRequest(val email: String, val password: String)

data class UserDto(
    val id: String,
    val email: String,
    val name: String,
    @SerializedName("avatar_url") val avatarUrl: String?,
    @SerializedName("date_of_birth") val dateOfBirth: String?,
    val gender: String?,
    val weight: String?,
    val height: String?,
    @SerializedName("blood_type") val bloodType: String?,
    @SerializedName("emergency_contact") val emergencyContact: String?
)

data class LoginResponse(val success: Boolean, val token: String?, val user: UserDto?, val message: String?)

data class RegisterRequest(
    val email: String, val password: String, val name: String,
    @SerializedName("date_of_birth") val dateOfBirth: String? = null,
    val gender: String? = null, val weight: String? = null,
    val height: String? = null, @SerializedName("blood_type") val bloodType: String? = null,
    @SerializedName("emergency_contact") val emergencyContact: String? = null
)

// ─── Dashboard ───
data class DashboardSummaryDto(
    @SerializedName("latest_heart_rate") val latestHeartRate: Int,
    @SerializedName("latest_systolic") val latestSystolic: Int,
    @SerializedName("latest_diastolic") val latestDiastolic: Int,
    @SerializedName("latest_spo2") val latestSpo2: Int,
    @SerializedName("latest_temperature") val latestTemperature: Double,
    @SerializedName("health_status") val healthStatus: String,
    @SerializedName("last_updated") val lastUpdated: Long
)

data class DashboardResponse(val success: Boolean, val summary: DashboardSummaryDto?, val message: String?)

// ─── Readings ───
data class HealthReadingDto(
    val id: String,
    @SerializedName("user_id") val userId: String,
    val timestamp: Long,
    @SerializedName("heart_rate") val heartRate: Int?,
    @SerializedName("systolic_bp") val systolicBp: Int?,
    @SerializedName("diastolic_bp") val diastolicBp: Int?,
    val spo2: Int?,
    val temperature: Double?,
    @SerializedName("ecg_data") val ecgData: List<Double>?,
    @SerializedName("ppg_data") val ppgData: List<Double>?
)

data class HealthReadingsResponse(val success: Boolean, val readings: List<HealthReadingDto>, val message: String?)

// ─── BLE ───
data class BLEStatusResponse(
    val available: Boolean,
    val connected: Boolean,
    @SerializedName("device_name") val deviceName: String,
    @SerializedName("device_address") val deviceAddress: String?,
    @SerializedName("connected_at") val connectedAt: String?,
    @SerializedName("data_stats") val dataStats: Map<String, Any>
)

data class CommandResponse(val success: Boolean, val message: String)
