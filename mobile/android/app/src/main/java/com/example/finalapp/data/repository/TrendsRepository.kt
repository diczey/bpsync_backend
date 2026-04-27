package com.example.finalapp.data.repository

import com.example.finalapp.data.api.ApiClient
import com.example.finalapp.data.model.TrendDataDto

data class TrendsPayload(
    val trends: List<TrendDataDto>,
    val message: String?
)

class TrendsRepository {
    private val api = ApiClient.apiService

    suspend fun fetchTrends(period: String = "week", date: String? = null): RepositoryResult<TrendsPayload> {
        val token = SessionStore.token.value
            ?: return RepositoryResult.Error("No valid session. Please login.")

        return try {
            val response = api.getTrends("Bearer $token", period, date)
            if (response.isSuccessful && response.body()?.success == true) {
                val body = response.body()
                RepositoryResult.Success(
                    TrendsPayload(
                        trends = body?.trends ?: emptyList(),
                        message = body?.message
                    )
                )
            } else {
                RepositoryResult.Error(response.body()?.message ?: "Failed to fetch trends.")
            }
        } catch (e: Exception) {
            RepositoryResult.Error("Network error: ${e.localizedMessage}")
        }
    }
}
