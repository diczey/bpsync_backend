package com.example.finalapp.data.repository

import com.example.finalapp.data.api.ApiClient
import com.example.finalapp.data.api.ApiService
import com.example.finalapp.data.model.HealthReadingsResponse
import com.example.finalapp.data.model.HealthReadingDto
import com.example.finalapp.data.model.PpgDataPoint
import com.example.finalapp.data.model.PpgSignalResponse

class PpgRepository(private val apiService: ApiService = ApiClient.apiService) {

    suspend fun fetchPpgSignal(): RepositoryResult<PpgSignalResponse> {
        val token = SessionStore.token.value
            ?: return RepositoryResult.Error("No active session.")

        return runCatching {
            apiService.getReadings("Bearer $token")
        }.fold(
            onSuccess = { response ->
                val body = response.body()
                if (response.isSuccessful && body?.success == true) {
                    val mapped = mapToPpgResponse(body)
                    if (mapped != null) {
                        RepositoryResult.Success(mapped)
                    } else {
                        RepositoryResult.Error(body.message ?: "No raw PPG data available from /readings.")
                    }
                } else {
                    RepositoryResult.Error(body?.message ?: "Failed to load PPG signal from /readings")
                }
            },
            onFailure = {
                RepositoryResult.Error(it.message ?: "Network error fetching PPG signal")
            }
        )
    }

    private fun mapToPpgResponse(body: HealthReadingsResponse): PpgSignalResponse? {
        val readings = body.readings
            .sortedByDescending { normalizeTimestamp(it.timestamp) }
        if (readings.isEmpty()) return null

        val latestWithPpg = readings.firstOrNull { !it.ppgData.isNullOrEmpty() } ?: return null
        val qualitySeries = latestWithPpg.ppgData.orEmpty()
        if (qualitySeries.isEmpty()) return null

        val chartPoints = qualitySeries.mapIndexed { index, quality ->
            PpgDataPoint(
                timestamp = normalizeTimestamp(latestWithPpg.timestamp) + (index * 1000L),
                quality = quality.toFloat()
            )
        }

        val avgQuality = chartPoints.map { it.quality }.average().toInt()
        val highestQuality = chartPoints.maxOf { it.quality }.toInt()
        val lowestQuality = chartPoints.minOf { it.quality }.toInt()
        val signalStability = maxOf(0, 100 - (highestQuality - lowestQuality))

        return PpgSignalResponse(
            success = true,
            avgQuality = avgQuality,
            signalStability = signalStability,
            highestQuality = highestQuality,
            lowestQuality = lowestQuality,
            chartPoints = chartPoints,
            message = "PPG screen using raw wristband rows from /readings."
        )
    }

    private fun normalizeTimestamp(timestamp: Long): Long {
        return if (timestamp < 1_000_000_000_000L) timestamp * 1000 else timestamp
    }
}
