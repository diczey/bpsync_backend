package com.example.finalapp.data.repository

import com.example.finalapp.data.api.ApiClient
import com.example.finalapp.data.api.ApiService
import com.example.finalapp.data.model.DashboardSummaryDto

class DashboardRepository(
    private val apiService: ApiService = ApiClient.apiService
) {
    suspend fun fetchDashboardSummary(): RepositoryResult<DashboardSummaryDto> {
        val token = SessionStore.token.value
            ?: return RepositoryResult.Error("No active session. Please sign in first.")

        return runCatching {
            apiService.getDashboardSummary("Bearer $token")
        }.fold(
            onSuccess = { response ->
                val body = response.body()
                when {
                    response.isSuccessful && body?.success == true && body.summary != null ->
                        RepositoryResult.Success(body.summary)
                    body?.message?.isNotBlank() == true ->
                        RepositoryResult.Error(body.message)
                    else ->
                        RepositoryResult.Error("Unable to load dashboard summary.")
                }
            },
            onFailure = {
                RepositoryResult.Error(it.message ?: "Dashboard request failed.")
            }
        )
    }
}
