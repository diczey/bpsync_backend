package com.example.bp.data.model

import com.google.gson.annotations.SerializedName

// ─── Auth ────────────────────────────────────────────────────────────────────

data class LoginRequest(
    val email: String,
    val password: String
)

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

data class LoginResponse(
    val success: Boolean,
    val token: String?,
    val user: UserDto?,
    val message: String?
)

data class RegisterRequest(
    val email: String,
    val password: String,
    val name: String,
    @SerializedName("date_of_birth") val dateOfBirth: String?,
    val gender: String?,
    val weight: String?,
    val height: String?,
    @SerializedName("blood_type") val bloodType: String?,
    @SerializedName("emergency_contact") val emergencyContact: String?
)

// ─── Dashboard ───────────────────────────────────────────────────────────────

data class DashboardSummaryDto(
    @SerializedName("latest_heart_rate") val latestHeartRate: Int,
    @SerializedName("latest_systolic") val latestSystolic: Int,
    @SerializedName("latest_diastolic") val latestDiastolic: Int,
    @SerializedName("latest_spo2") val latestSpo2: Int,
    @SerializedName("latest_temperature") val latestTemperature: Double,
    @SerializedName("health_status") val healthStatus: String,
    @SerializedName("last_updated") val lastUpdated: Long
)

data class DashboardResponse(
    val success: Boolean,
    val summary: DashboardSummaryDto?,
    val message: String?
)

// ─── Readings ────────────────────────────────────────────────────────────────

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

data class HealthReadingCreate(
    val timestamp: Long,
    @SerializedName("heart_rate") val heartRate: Int?,
    @SerializedName("systolic_bp") val systolicBp: Int?,
    @SerializedName("diastolic_bp") val diastolicBp: Int?,
    val spo2: Int?,
    val temperature: Double?,
    @SerializedName("ecg_data") val ecgData: List<Double>? = null,
    @SerializedName("ppg_data") val ppgData: List<Double>? = null
)

data class HealthReadingsResponse(
    val success: Boolean,
    val readings: List<HealthReadingDto>,
    val message: String?
)

// ─── Trends ──────────────────────────────────────────────────────────────────

data class TrendDataPoint(
    val timestamp: Long,
    val value: Double
)

data class TrendDataDto(
    val type: String,
    @SerializedName("data_points") val dataPoints: List<TrendDataPoint>,
    val average: Double,
    val min: Double,
    val max: Double
)

data class TrendResponse(
    val success: Boolean,
    val trends: List<TrendDataDto>,
    val message: String?
)
// ─── Reports ─────────────────────────────────────────────────────────────────

data class DailyBP(
    val date: String,
    @SerializedName("avg_systolic") val avgSystolic: Double?,
    @SerializedName("avg_diastolic") val avgDiastolic: Double?,
    @SerializedName("avg_heart_rate") val avgHeartRate: Double?,
    @SerializedName("reading_count") val readingCount: Int
)

data class WeeklyReport(
    @SerializedName("week_start") val weekStart: String,
    @SerializedName("week_end") val weekEnd: String,
    @SerializedName("avg_systolic") val avgSystolic: Double?,
    @SerializedName("avg_diastolic") val avgDiastolic: Double?,
    @SerializedName("avg_heart_rate") val avgHeartRate: Double?,
    @SerializedName("readings_count") val readingsCount: Int,
    @SerializedName("health_score") val healthScore: Int,
    @SerializedName("daily_summaries") val dailySummaries: List<DailyBP>
)

data class WeeklyReportResponse(
    val success: Boolean,
    val report: WeeklyReport?,
    val message: String?
)

// ─── BLE ─────────────────────────────────────────────────────────────────────

data class BLEStatusResponse(
    val available: Boolean,
    val connected: Boolean,
    @SerializedName("device_name") val deviceName: String,
    @SerializedName("device_address") val deviceAddress: String?,
    @SerializedName("connected_at") val connectedAt: String?,
    @SerializedName("data_stats") val dataStats: Map<String, Any>
)

data class ScanResult(
    val found: Boolean,
    @SerializedName("device_name") val deviceName: String?,
    @SerializedName("device_address") val deviceAddress: String?,
    val message: String
)

data class CommandResponse(
    val success: Boolean,
    val message: String
)

// ─── Profile ─────────────────────────────────────────────────────────────────

data class ProfileUpdateRequest(
    val name: String?,
    @SerializedName("date_of_birth") val dateOfBirth: String?,
    val gender: String?,
    val weight: String?,
    val height: String?,
    @SerializedName("blood_type") val bloodType: String?,
    @SerializedName("emergency_contact") val emergencyContact: String?
)

data class ProfileResponse(
    val success: Boolean,
    val user: UserDto?,
    val message: String?
)
