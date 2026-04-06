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
    @SerializedName("emergency_contact") val emergencyContact: String?,
    @SerializedName("last_checkup_date") val lastCheckupDate: String?
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

data class ScanResult(
    val found: Boolean,
    @SerializedName("device_name") val deviceName: String?,
    @SerializedName("device_address") val deviceAddress: String?,
    val message: String
)

data class BleFrameUploadRequest(
    @SerializedName("raw_frame") val rawFrame: String,
    @SerializedName("source_device_name") val sourceDeviceName: String? = null,
    @SerializedName("source_device_address") val sourceDeviceAddress: String? = null
)

data class BleInferredReadingDto(
    val timestamp: Long,
    val systolic: Int,
    val diastolic: Int,
    @SerializedName("heart_rate") val heartRate: Int,
    val ptt: Float,
    val quality: Int,
    val category: String
)

data class BleFrameUploadResponse(
    val success: Boolean,
    @SerializedName("reading_id") val readingId: String? = null,
    @SerializedName("seq_num") val seqNum: Int,
    val quality: Float,
    @SerializedName("buffer_fill") val bufferFill: String? = null,
    @SerializedName("reading_created") val readingCreated: Boolean = false,
    val reading: BleInferredReadingDto? = null,
    val message: String? = null
)

// ─── Trends ───
data class TrendDataPoint(
    val timestamp: Long,
    val value: Float
)

data class TrendDataDto(
    val type: String,
    @SerializedName("data_points") val dataPoints: List<TrendDataPoint>,
    val average: Float,
    val min: Float,
    val max: Float
)

data class TrendResponse(
    val success: Boolean,
    val trends: List<TrendDataDto> = emptyList(),
    val message: String?
)

// ─── Profile ───
data class ProfileUpdateRequest(
    val name: String? = null,
    val email: String? = null,
    @SerializedName("date_of_birth") val dateOfBirth: String? = null,
    val gender: String? = null,
    val weight: String? = null,
    val height: String? = null,
    @SerializedName("last_checkup_date") val lastCheckupDate: String? = null
)

data class ProfileResponse(
    val success: Boolean,
    val user: UserDto?,
    val message: String?
)

data class UserSettingsDto(
    val language: String,
    @SerializedName("push_notifications_enabled") val pushNotificationsEnabled: Boolean,
    @SerializedName("weekly_reports_enabled") val weeklyReportsEnabled: Boolean
)

data class SettingsUpdateRequest(
    val language: String,
    @SerializedName("push_notifications_enabled") val pushNotificationsEnabled: Boolean,
    @SerializedName("weekly_reports_enabled") val weeklyReportsEnabled: Boolean
)

data class SettingsResponse(
    val success: Boolean,
    val settings: UserSettingsDto?,
    val message: String? = null
)

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
    val notifications: List<NotificationDto> = emptyList(),
    @SerializedName("unread_count") val unreadCount: Int = 0,
    val message: String? = null
)

data class DailyReportDto(
    val date: String,
    @SerializedName("avg_systolic") val avgSystolic: Float? = null,
    @SerializedName("avg_diastolic") val avgDiastolic: Float? = null,
    @SerializedName("avg_heart_rate") val avgHeartRate: Float? = null,
    @SerializedName("reading_count") val readingCount: Int = 0
)

data class HealthReportDto(
    @SerializedName("week_start") val weekStart: String,
    @SerializedName("week_end") val weekEnd: String,
    @SerializedName("avg_systolic") val avgSystolic: Float? = null,
    @SerializedName("avg_diastolic") val avgDiastolic: Float? = null,
    @SerializedName("avg_heart_rate") val avgHeartRate: Float? = null,
    @SerializedName("readings_count") val readingsCount: Int = 0,
    @SerializedName("health_score") val healthScore: Int = 0,
    @SerializedName("daily_summaries") val dailySummaries: List<DailyReportDto> = emptyList()
)

data class HealthReportResponse(
    val success: Boolean,
    val report: HealthReportDto? = null,
    val message: String? = null
)

// ─── PPG Signal ───
data class PpgDataPoint(
    val timestamp: Long,
    val quality: Float
)

data class PpgSignalResponse(
    val success: Boolean,
    @SerializedName("avg_quality") val avgQuality: Int = 0,
    @SerializedName("signal_stability") val signalStability: Int = 0,
    @SerializedName("highest_quality") val highestQuality: Int = 0,
    @SerializedName("lowest_quality") val lowestQuality: Int = 0,
    @SerializedName("chart_points") val chartPoints: List<PpgDataPoint> = emptyList(),
    val message: String? = null
)

// ─── Pulse / ECG ───
data class PulseDataPoint(
    val timestamp: Long,
    val value: Float
)

data class PulseResponse(
    val success: Boolean,
    @SerializedName("current_bpm") val currentBpm: Int = 0,
    @SerializedName("resting_hr") val restingHr: Int = 0,
    @SerializedName("min_hr") val minHr: Int = 0,
    @SerializedName("avg_hr") val avgHr: Int = 0,
    @SerializedName("max_hr") val maxHr: Int = 0,
    @SerializedName("status_label") val statusLabel: String = "",
    @SerializedName("pattern_24h") val pattern24h: List<PulseDataPoint> = emptyList(),
    @SerializedName("ecg_waveform_points") val ecgWaveformPoints: List<Float> = emptyList(),
    val message: String? = null
)
