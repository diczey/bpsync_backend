package com.example.finalapp.data.repository

import com.example.finalapp.data.api.ApiClient
import com.example.finalapp.data.api.ApiService
import com.example.finalapp.data.model.HealthReportDto

class ReportsRepository(
    private val apiService: ApiService = ApiClient.apiService
) {
    suspend fun fetchWeeklyReport(weekOffset: Int = 0): RepositoryResult<HealthReportDto> {
        val token = SessionStore.token.value
            ?: return RepositoryResult.Error("No active session.")

        return runCatching {
            apiService.getWeeklyReport("Bearer $token", weekOffset)
        }.fold(
            onSuccess = { response ->
                val body = response.body()
                if (response.isSuccessful && body?.success == true && body.report != null) {
                    RepositoryResult.Success(body.report)
                } else {
                    RepositoryResult.Error(body?.message ?: "Failed to load weekly report.")
                }
            },
            onFailure = {
                RepositoryResult.Error(it.message ?: "Network error while loading weekly report.")
            }
        )
    }

    suspend fun fetchMonthlyReport(monthOffset: Int = 0): RepositoryResult<HealthReportDto> {
        val token = SessionStore.token.value
            ?: return RepositoryResult.Error("No active session.")

        return runCatching {
            apiService.getMonthlyReport("Bearer $token", monthOffset)
        }.fold(
            onSuccess = { response ->
                val body = response.body()
                if (response.isSuccessful && body?.success == true && body.report != null) {
                    RepositoryResult.Success(body.report)
                } else {
                    RepositoryResult.Error(body?.message ?: "Failed to load monthly report.")
                }
            },
            onFailure = {
                RepositoryResult.Error(it.message ?: "Network error while loading monthly report.")
            }
        )
    }
}
