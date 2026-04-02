package com.example.finalapp.data.repository

import com.example.finalapp.data.api.ApiClient
import com.example.finalapp.data.model.TrendDataDto

class TrendsRepository {
    private val api = ApiClient.apiService

    suspend fun fetchTrends(period: String = "week"): RepositoryResult<List<TrendDataDto>> {
        val token = SessionStore.token.value
            ?: return RepositoryResult.Error("No valid session. Please login.")

        return try {
            val response = api.getTrends("Bearer $token", period)
            if (response.isSuccessful && response.body()?.success == true) {
                RepositoryResult.Success(response.body()?.trends ?: emptyList())
            } else {
                RepositoryResult.Error(response.body()?.message ?: "Failed to fetch trends.")
            }
        } catch (e: Exception) {
            RepositoryResult.Error("Network error: ${e.localizedMessage}")
        }
    }
}
