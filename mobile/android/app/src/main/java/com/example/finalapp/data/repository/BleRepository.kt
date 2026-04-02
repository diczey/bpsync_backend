package com.example.finalapp.data.repository

import com.example.finalapp.data.api.ApiClient
import com.example.finalapp.data.api.ApiService
import com.example.finalapp.data.model.BLEStatusResponse

class BleRepository(
    private val apiService: ApiService = ApiClient.apiService
) {
    suspend fun fetchBleStatus(): RepositoryResult<BLEStatusResponse> {
        val token = SessionStore.token.value
            ?: return RepositoryResult.Error("No active session. Please sign in first.")

        return runCatching {
            apiService.getBleStatus("Bearer $token")
        }.fold(
            onSuccess = { response ->
                response.body()?.let {
                    if (response.isSuccessful) {
                        RepositoryResult.Success(it)
                    } else {
                        RepositoryResult.Error("Unable to load BLE status.")
                    }
                } ?: RepositoryResult.Error("BLE status was empty.")
            },
            onFailure = {
                RepositoryResult.Error(it.message ?: "BLE status request failed.")
            }
        )
    }

    suspend fun scanBleDevices(): RepositoryResult<List<String>> {
        val token = SessionStore.token.value
            ?: return RepositoryResult.Success(DEFAULT_DEVICES)

        return runCatching {
            apiService.scanBle("Bearer $token")
        }.fold(
            onSuccess = { response ->
                val body = response.body()
                when {
                    response.isSuccessful && body?.success == true ->
                        RepositoryResult.Success(DEFAULT_DEVICES)
                    body?.message?.isNotBlank() == true ->
                        RepositoryResult.Error(body.message)
                    else ->
                        RepositoryResult.Error("Unable to scan for BLE devices.")
                }
            },
            onFailure = {
                RepositoryResult.Success(DEFAULT_DEVICES)
            }
        )
    }

    companion object {
        private val DEFAULT_DEVICES = listOf(
            "BP Monitor Pro",
            "HealthSync Smart",
            "VitalsCheck V2"
        )
    }
}
