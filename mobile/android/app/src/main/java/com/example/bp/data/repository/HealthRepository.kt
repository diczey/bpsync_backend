package com.example.bp.data.repository

import com.example.bp.data.api.ApiService
import com.example.bp.data.model.DashboardSummaryDto
import com.example.bp.data.model.HealthReadingDto
import com.example.bp.data.model.TrendDataDto
import com.example.bp.data.model.UserDto
import com.example.bp.data.model.ProfileUpdateRequest
import com.example.bp.data.model.BLEStatusResponse
import com.example.bp.data.model.ScanResult
import com.example.bp.data.model.CommandResponse
import com.example.bp.data.model.WeeklyReportResponse
import com.example.bp.data.model.WeeklyReport

class HealthRepository(private val api: ApiService) {

    private fun bearerToken(token: String) = "Bearer $token"

    suspend fun getDashboardSummary(token: String): Result<DashboardSummaryDto> {
        return try {
            val response = api.getDashboardSummary(bearerToken(token))
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                if (body.success && body.summary != null) {
                    Result.Success(body.summary)
                } else {
                    Result.Error(body.message ?: "Dashboard verisi alınamadı")
                }
            } else {
                Result.Error("Dashboard yüklenemedi: ${response.code()}")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Bağlantı hatası")
        }
    }

    suspend fun getReadings(token: String): Result<List<HealthReadingDto>> {
        return try {
            val response = api.getReadings(bearerToken(token))
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                if (body.success) {
                    Result.Success(body.readings)
                } else {
                    Result.Error(body.message ?: "Ölçümler alınamadı")
                }
            } else {
                Result.Error("Ölçümler yüklenemedi: ${response.code()}")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Bağlantı hatası")
        }
    }

    suspend fun getTrends(token: String, period: String = "week"): Result<List<TrendDataDto>> {
        return try {
            val response = api.getTrends(bearerToken(token), period)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                if (body.success) {
                    Result.Success(body.trends)
                } else {
                    Result.Error(body.message ?: "Trendler alınamadı")
                }
            } else {
                Result.Error("Trendler yüklenemedi: ${response.code()}")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Bağlantı hatası")
        }
    }

    suspend fun getProfile(token: String): Result<UserDto> {
        return try {
            val response = api.getProfile(bearerToken(token))
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                if (body.success && body.user != null) {
                    Result.Success(body.user)
                } else {
                    Result.Error(body.message ?: "Profil bilgisi alınamadı")
                }
            } else {
                Result.Error("Profil yüklenemedi: ${response.code()}")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Bağlantı hatası")
        }
    }

    suspend fun updateProfile(token: String, request: ProfileUpdateRequest): Result<UserDto> {
        return try {
            val response = api.updateProfile(bearerToken(token), request)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                if (body.success && body.user != null) {
                    Result.Success(body.user)
                } else {
                    Result.Error(body.message ?: "Profil güncellenemedi")
                }
            } else {
                Result.Error("Profil güncellenemedi: ${response.code()}")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Bağlantı hatası")
        }
    }

    suspend fun getBleStatus(token: String): Result<BLEStatusResponse> {
        return try {
            val response = api.getBleStatus(bearerToken(token))
            if (response.isSuccessful && response.body() != null) {
                Result.Success(response.body()!!)
            } else {
                Result.Error("BLE durumu alınamadı")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Bağlantı hatası")
        }
    }

    suspend fun scanBle(token: String): Result<ScanResult> {
        return try {
            val response = api.scanBle(bearerToken(token))
            if (response.isSuccessful && response.body() != null) {
                Result.Success(response.body()!!)
            } else {
                Result.Error("BLE taraması başarısız")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Bağlantı hatası")
        }
    }

    suspend fun startStreaming(token: String): Result<CommandResponse> {
        return try {
            val response = api.startBleStreaming(bearerToken(token))
            if (response.isSuccessful && response.body() != null) {
                Result.Success(response.body()!!)
            } else {
                Result.Error("Streaming başlatılamadı")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Bağlantı hatası")
        }
    }

    suspend fun stopStreaming(token: String): Result<CommandResponse> {
        return try {
            val response = api.stopBleStreaming(bearerToken(token))
            if (response.isSuccessful && response.body() != null) {
                Result.Success(response.body()!!)
            } else {
                Result.Error("Streaming durdurulamadı")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Bağlantı hatası")
        }
    }

    suspend fun getWeeklyReport(token: String, offset: Int = 0): Result<WeeklyReport> {
        return try {
            val response = api.getWeeklyReport(bearerToken(token), offset)
            if (response.isSuccessful && response.body()?.report != null) {
                Result.Success(response.body()!!.report!!)
            } else {
                Result.Error("Haftalık rapor alınamadı")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Bağlantı hatası")
        }
    }

    suspend fun getMonthlyReport(token: String, offset: Int = 0): Result<WeeklyReport> {
        return try {
            val response = api.getMonthlyReport(bearerToken(token), offset)
            if (response.isSuccessful && response.body()?.report != null) {
                Result.Success(response.body()!!.report!!)
            } else {
                Result.Error("Aylık rapor alınamadı")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Bağlantı hatası")
        }
    }
}
