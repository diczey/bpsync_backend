package com.example.bpsync.network

import com.google.gson.annotations.SerializedName

// --- Auth ---
data class LoginRequest(
    val email: String,
    val password: String
)

data class RegisterRequest(
    val email: String,
    val password: String,
    val name: String
)

data class UserDto(
    val id: String,
    val email: String,
    val name: String,
    @SerializedName("avatar_url") val avatarUrl: String? = null,
    @SerializedName("date_of_birth") val dateOfBirth: String? = null,
    @SerializedName("blood_type") val bloodType: String? = null,
    @SerializedName("emergency_contact") val emergencyContact: String? = null
)

data class LoginResponse(
    val success: Boolean,
    val token: String? = null,
    val user: UserDto? = null,
    val message: String? = null
)

data class MessageResponse(
    val success: Boolean,
    val message: String? = null
)

// --- Dashboard (devfixed: bp_readings özeti) ---
data class DashboardSummaryDto(
    @SerializedName("latest_heart_rate") val latestHeartRate: Int? = null,
    @SerializedName("latest_systolic") val latestSystolic: Int? = null,
    @SerializedName("latest_diastolic") val latestDiastolic: Int? = null,
    @SerializedName("latest_ptt") val latestPtt: Double? = null,
    @SerializedName("latest_quality") val latestQuality: Int? = null,
    @SerializedName("latest_spo2") val latestSpo2: Int? = null,
    @SerializedName("latest_temperature") val latestTemperature: Double? = null,
    @SerializedName("health_status") val healthStatus: String? = null,
    val category: String? = null,
    @SerializedName("last_updated") val lastUpdated: String? = null
)

data class DashboardResponse(
    val success: Boolean,
    val summary: DashboardSummaryDto? = null,
    val message: String? = null
)

// --- Health Readings (devfixed: bp_readings listesi) ---
data class BPReadingDto(
    val time: String,
    @SerializedName("user_id") val userId: String,
    val systolic: Int? = null,
    val diastolic: Int? = null,
    @SerializedName("heart_rate") val heartRate: Int? = null,
    val ptt: Double? = null,
    val quality: Int? = null,
    val category: String? = null
)

data class HealthReadingDto(
    val id: String? = null,
    @SerializedName("user_id") val userId: String,
    val timestamp: Long? = null,
    val time: String? = null,
    @SerializedName("heart_rate") val heartRate: Int? = null,
    @SerializedName("systolic_bp") val systolicBp: Int? = null,
    val systolic: Int? = null,
    @SerializedName("diastolic_bp") val diastolicBp: Int? = null,
    val diastolic: Int? = null,
    val spo2: Int? = null,
    val temperature: Double? = null,
    @SerializedName("ecg_data") val ecgData: List<Double>? = null,
    @SerializedName("ppg_data") val ppgData: List<Double>? = null
)

data class HealthReadingCreate(
    val timestamp: Long,
    @SerializedName("heart_rate") val heartRate: Int? = null,
    @SerializedName("systolic_bp") val systolicBp: Int? = null,
    @SerializedName("diastolic_bp") val diastolicBp: Int? = null,
    val spo2: Int? = null,
    val temperature: Double? = null,
    @SerializedName("ecg_data") val ecgData: List<Double>? = null,
    @SerializedName("ppg_data") val ppgData: List<Double>? = null
)

data class HealthReadingsResponse(
    val success: Boolean,
    val readings: List<BPReadingDto>? = null,
    val message: String? = null
)

// --- BP Prediction (XGBoost) ---
data class BPPredictionRequest(
    val ptt: Double,
    @SerializedName("heart_rate") val heartRate: Double,
    val age: Double? = 40.0,
    @SerializedName("ptt_std") val pttStd: Double? = 15.0
)

data class BPPredictionResponse(
    val success: Boolean,
    val systolic: Int,
    val diastolic: Int,
    val category: String,
    val message: String? = null
)

data class BPCalibrationRequest(
    @SerializedName("measured_systolic") val measuredSystolic: Int,
    @SerializedName("measured_diastolic") val measuredDiastolic: Int,
    val ptt: Double,
    @SerializedName("heart_rate") val heartRate: Double,
    val age: Double? = 40.0
)

data class CalibrateResponse(
    val success: Boolean,
    val message: String? = null
)

data class ModelInfoResponse(
    val success: Boolean,
    @SerializedName("model_loaded") val modelLoaded: Boolean = false,
    @SerializedName("has_calibration") val hasCalibration: Boolean = false,
    @SerializedName("feature_importance") val featureImportance: Map<String, Double>? = null
)

// --- Trends (backend: period + points with bucket, avg_systolic, avg_diastolic, avg_heart_rate) ---
data class TrendDataPoint(
    val timestamp: Long,
    val value: Double
)

/** Backend /trends format: time-bucketed aggregates */
data class TrendPointDto(
    val bucket: String,
    @SerializedName("avg_systolic") val avgSystolic: Double? = null,
    @SerializedName("avg_diastolic") val avgDiastolic: Double? = null,
    @SerializedName("avg_heart_rate") val avgHeartRate: Double? = null,
    val count: Int = 0
)

data class TrendResponse(
    val success: Boolean,
    val period: String? = null,
    val points: List<TrendPointDto>? = null,
    val message: String? = null,
    /** Eski format uyumluluğu: trends varsa onu da kabul et */
    val trends: List<TrendDataDto>? = null
)

data class TrendDataDto(
    val type: String,
    @SerializedName("data_points") val dataPoints: List<TrendDataPoint>? = null,
    val average: Double = 0.0,
    val min: Double = 0.0,
    val max: Double = 0.0
)

// --- Reports (backend returns { success, report: { ... } }) ---
data class DailyBP(
    val date: String,
    @SerializedName("avg_systolic") val avgSystolic: Double? = null,
    @SerializedName("avg_diastolic") val avgDiastolic: Double? = null,
    @SerializedName("avg_heart_rate") val avgHeartRate: Double? = null,
    @SerializedName("reading_count") val readingCount: Int = 0
)

data class WeeklyReportDto(
    @SerializedName("week_start") val weekStart: String? = null,
    @SerializedName("week_end") val weekEnd: String? = null,
    @SerializedName("avg_heart_rate") val avgHeartRate: Double? = null,
    @SerializedName("avg_systolic") val avgSystolic: Double? = null,
    @SerializedName("avg_diastolic") val avgDiastolic: Double? = null,
    @SerializedName("readings_count") val readingsCount: Int? = null,
    @SerializedName("health_score") val healthScore: Int? = null,
    val recommendations: List<String>? = null,
    @SerializedName("daily_summaries") val dailySummaries: List<DailyBP>? = null
)

data class WeeklyReportResponse(
    val success: Boolean? = null,
    val report: WeeklyReportDto? = null,
    val message: String? = null,
    // Eski flat format uyumluluğu
    @SerializedName("week_start") val weekStart: String? = null,
    @SerializedName("week_end") val weekEnd: String? = null,
    @SerializedName("avg_heart_rate") val avgHeartRate: Double? = null,
    @SerializedName("avg_systolic") val avgSystolic: Double? = null,
    @SerializedName("avg_diastolic") val avgDiastolic: Double? = null,
    @SerializedName("readings_count") val readingsCount: Int? = null,
    @SerializedName("health_score") val healthScore: Int? = null,
    val recommendations: List<String>? = null
)

// --- Notifications ---
data class NotificationDto(
    val id: String,
    val title: String,
    val message: String,
    val type: String,
    val timestamp: Long,
    @SerializedName("is_read") val isRead: Boolean
)

data class NotificationsResponse(
    val success: Boolean,
    val notifications: List<NotificationDto>? = null,
    @SerializedName("unread_count") val unreadCount: Int = 0
)

// --- Profile ---
data class ProfileUpdateRequest(
    val name: String? = null,
    @SerializedName("date_of_birth") val dateOfBirth: String? = null,
    @SerializedName("blood_type") val bloodType: String? = null,
    @SerializedName("emergency_contact") val emergencyContact: String? = null
)

data class ProfileResponse(
    val success: Boolean,
    val user: UserDto? = null,
    val message: String? = null
)

// --- Sensör (TimescaleDB) ---
data class WristbandPayload(
    @SerializedName("patient_id") val patientId: Int? = null,
    @SerializedName("heart_rate") val heartRate: Int? = null,
    val spo2: Double? = null,
    val movement: Double? = null,
    val time: String? = null
)

data class EcgPayload(
    @SerializedName("patient_id") val patientId: Int? = null,
    @SerializedName("ecg_value") val ecgValue: Double? = null,
    val time: String? = null
)

data class SensorOkResponse(
    val ok: Boolean,
    val message: String? = null
)
