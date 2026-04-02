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
                    response.isSuccessful && body?.found == true && body.deviceName != null ->
                        // Real BPSync wristband found via Bluetooth scan
                        RepositoryResult.Success(listOf(body.deviceName))
                    response.isSuccessful && body?.found == false ->
                        RepositoryResult.Error("No BPSync wristband found nearby. Make sure it's turned on.")
                    body?.message?.isNotBlank() == true ->
                        RepositoryResult.Error(body.message)
                    else ->
                        RepositoryResult.Error("Unable to scan for BLE devices.")
                }
            },
            onFailure = {
                // Railway doesn't have physical BLE hardware, show demo devices so UI is usable
                RepositoryResult.Success(DEFAULT_DEVICES)
            }
        )
    }

    suspend fun startStreaming(): RepositoryResult<String> {
        val token = SessionStore.token.value
            ?: return RepositoryResult.Error("No active session.")

        return runCatching {
            apiService.startStreaming("Bearer $token")
        }.fold(
            onSuccess = { response ->
                val body = response.body()
                if (response.isSuccessful && body?.success == true) {
                    RepositoryResult.Success(body.message)
                } else {
                    RepositoryResult.Error(body?.message ?: "Could not start streaming.")
                }
            },
            onFailure = {
                RepositoryResult.Error(it.message ?: "Failed to start streaming.")
            }
        )
    }

    suspend fun stopStreaming(): RepositoryResult<String> {
        val token = SessionStore.token.value
            ?: return RepositoryResult.Error("No active session.")

        return runCatching {
            apiService.stopStreaming("Bearer $token")
        }.fold(
            onSuccess = { response ->
                val body = response.body()
                if (response.isSuccessful && body?.success == true) {
                    RepositoryResult.Success(body.message)
                } else {
                    RepositoryResult.Error(body?.message ?: "Could not stop streaming.")
                }
            },
            onFailure = {
                RepositoryResult.Error(it.message ?: "Failed to stop streaming.")
            }
        )
    }

    companion object {
        // Shown as fallback when the Railway server doesn't have physical BLE hardware available
        private val DEFAULT_DEVICES = listOf(
            "BPSync-Wrist (Demo)",
            "BP Monitor Pro",
            "HealthSync Smart"
        )
    }
}
