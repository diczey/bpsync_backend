package com.example.finalapp.data.repository

import com.example.finalapp.data.api.ApiClient
import com.example.finalapp.data.api.ApiService
import com.example.finalapp.data.model.PulseResponse

class PulseRepository(private val apiService: ApiService = ApiClient.apiService) {

    suspend fun fetchPulseData(): RepositoryResult<PulseResponse> {
        val token = SessionStore.token.value
            ?: return RepositoryResult.Error("No active session.")

        return runCatching {
            apiService.getPulseData("Bearer $token")
        }.fold(
            onSuccess = { response ->
                val body = response.body()
                if (response.isSuccessful && body?.success == true) {
                    RepositoryResult.Success(body)
                } else {
                    RepositoryResult.Error(body?.message ?: "Failed to load Pulse data from TimescaleDB")
                }
            },
            onFailure = {
                RepositoryResult.Error(it.message ?: "Network error fetching Pulse data")
            }
        )
    }
}
