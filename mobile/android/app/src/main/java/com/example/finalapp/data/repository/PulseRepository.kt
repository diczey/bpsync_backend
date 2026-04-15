package com.example.finalapp.data.repository

import com.example.finalapp.data.api.ApiClient
import com.example.finalapp.data.api.ApiService
import com.example.finalapp.data.model.PulseDataPoint
import com.example.finalapp.data.model.PulseResponse
import com.example.finalapp.data.model.HealthReadingsResponse
import com.example.finalapp.data.model.HealthReadingDto
import java.util.Calendar
import java.util.TimeZone

class PulseRepository(private val apiService: ApiService = ApiClient.apiService) {

    suspend fun fetchPulseData(): RepositoryResult<PulseResponse> {
        val token = SessionStore.token.value
            ?: return RepositoryResult.Error("No active session.")

        return runCatching {
            apiService.getReadings("Bearer $token")
        }.fold(
            onSuccess = { response ->
                val body = response.body()
                if (response.isSuccessful && body?.success == true) {
                    val mapped = mapToPulseResponse(body)
                    if (mapped != null) {
                        RepositoryResult.Success(mapped)
                    } else {
                        RepositoryResult.Error(body.message ?: "No raw ECG data available from /readings.")
                    }
                } else {
                    RepositoryResult.Error(body?.message ?: "Failed to load Pulse data from /readings")
                }
            },
            onFailure = {
                RepositoryResult.Error(it.message ?: "Network error fetching Pulse data")
            }
        )
    }

    private fun mapToPulseResponse(body: HealthReadingsResponse): PulseResponse? {
        val readings = body.readings
            .sortedByDescending { normalizeTimestamp(it.timestamp) }
        if (readings.isEmpty()) return null

        val heartRates = readings.mapNotNull { it.heartRate }
        val latest = readings.firstOrNull() ?: return null
        val latestWithEcg = readings.firstOrNull { !it.ecgData.isNullOrEmpty() } ?: latest
        val waveform = latestWithEcg.ecgData?.map { it.toFloat() } ?: emptyList()
        if (waveform.isEmpty() && heartRates.isEmpty()) return null

        val currentBpm = latest.heartRate ?: heartRates.firstOrNull() ?: 0
        val minHr = heartRates.minOrNull() ?: currentBpm
        val avgHr = if (heartRates.isNotEmpty()) heartRates.average().toInt() else currentBpm
        val maxHr = heartRates.maxOrNull() ?: currentBpm
        val restingHr = minHr
        val statusLabel = classifyStatus(avgHr.takeIf { it > 0 } ?: currentBpm)
        val pattern24h = buildPattern(readings)

        return PulseResponse(
            success = true,
            currentBpm = currentBpm,
            restingHr = restingHr,
            minHr = minHr,
            avgHr = avgHr,
            maxHr = maxHr,
            statusLabel = statusLabel,
            pattern24h = pattern24h,
            ecgWaveformPoints = waveform,
            message = if (waveform.isNotEmpty()) {
                "Pulse screen using raw ECG data from /readings."
            } else {
                "No raw ECG data available in /readings."
            }
        )
    }

    private fun buildPattern(readings: List<HealthReadingDto>): List<PulseDataPoint> {
        return readings
            .asSequence()
            .filter { it.heartRate != null }
            .groupBy { hourBucket(normalizeTimestamp(it.timestamp)) }
            .mapNotNull { (bucket, items) ->
                val values = items.mapNotNull { it.heartRate }
                if (values.isEmpty()) return@mapNotNull null
                PulseDataPoint(
                    timestamp = bucket,
                    value = values.average().toFloat()
                )
            }
            .sortedBy { it.timestamp }
    }

    private fun classifyStatus(bpm: Int): String {
        return when {
            bpm <= 0 -> "No Data"
            bpm < 60 -> "Bradycardia"
            bpm > 100 -> "Tachycardia"
            else -> "Normal"
        }
    }

    private fun hourBucket(timestampMs: Long): Long {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.timeInMillis = timestampMs
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun normalizeTimestamp(timestamp: Long): Long {
        return if (timestamp < 1_000_000_000_000L) timestamp * 1000 else timestamp
    }
}
