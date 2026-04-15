package com.example.finalapp.data.repository

import com.example.finalapp.data.api.ApiClient
import com.example.finalapp.data.api.ApiService
import com.example.finalapp.data.model.BleInferredReadingDto
import com.example.finalapp.data.model.HealthReadingDto
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Reading(
    val id: Int,
    val systolic: String,
    val diastolic: String,
    val pulse: String,
    val spo2: String,
    val date: String,
    val time: String,
    val status: String
)

object ReadingRepository {
    private val _readings = MutableStateFlow<List<Reading>>(emptyList())
    val readings: StateFlow<List<Reading>> = _readings.asStateFlow()

    fun clear() {
        _readings.value = emptyList()
    }

    fun addReading(systolic: String, diastolic: String, pulse: String, spo2: String) {
        val sdfDate = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
        val sdfTime = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val currentDate = sdfDate.format(Date())
        val currentTime = sdfTime.format(Date())

        val reading = Reading(
            id = (_readings.value.maxOfOrNull { it.id } ?: 0) + 1,
            systolic = systolic,
            diastolic = diastolic,
            pulse = pulse,
            spo2 = spo2,
            date = currentDate,
            time = currentTime,
            status = resolveStatus(systolic, diastolic)
        )

        _readings.value = listOf(reading) + _readings.value
    }

    fun upsertFromBleInference(reading: BleInferredReadingDto) {
        val timestamp = normalizeTimestamp(reading.timestamp)
        val date = Date(timestamp)
        val sdfDate = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
        val sdfTime = SimpleDateFormat("hh:mm a", Locale.getDefault())

        val mapped = Reading(
            id = "ble-${reading.timestamp}".hashCode(),
            systolic = reading.systolic.toString(),
            diastolic = reading.diastolic.toString(),
            pulse = reading.heartRate.toString(),
            spo2 = "--",
            date = sdfDate.format(date),
            time = sdfTime.format(date),
            status = reading.category.ifBlank {
                resolveStatus(reading.systolic.toString(), reading.diastolic.toString())
            }
        )

        _readings.value = listOf(mapped) + _readings.value.filterNot { it.id == mapped.id }
    }

    suspend fun syncFromApi(apiService: ApiService = ApiClient.apiService): RepositoryResult<List<Reading>> {
        val token = SessionStore.token.value
            ?: return RepositoryResult.Success(_readings.value)

        return runCatching {
            apiService.getMeasurements("Bearer $token")
        }.fold(
            onSuccess = { response ->
                val body = response.body()
                when {
                    response.isSuccessful && body?.success == true -> {
                        val mapped = body.readings
                            .sortedByDescending { normalizeTimestamp(it.timestamp) }
                            .map(::mapToReading)
                        _readings.value = mapped
                        RepositoryResult.Success(mapped)
                    }
                    body?.message?.isNotBlank() == true ->
                        RepositoryResult.Error(body.message)
                    else ->
                        RepositoryResult.Error("Unable to load measurements.")
                }
            },
            onFailure = {
                RepositoryResult.Error(it.message ?: "Measurements request failed.")
            }
        )
    }

    fun latestReading(): Reading? = _readings.value.firstOrNull()

    private fun mapToReading(dto: HealthReadingDto): Reading {
        val timestamp = normalizeTimestamp(dto.timestamp)
        val date = Date(timestamp)
        val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val systolic = dto.systolicBp?.toString() ?: "--"
        val diastolic = dto.diastolicBp?.toString() ?: "--"

        return Reading(
            id = dto.id.hashCode(),
            systolic = systolic,
            diastolic = diastolic,
            pulse = dto.heartRate?.toString() ?: "--",
            spo2 = dto.spo2?.toString() ?: "--",
            date = dateFormat.format(date),
            time = timeFormat.format(date),
            status = resolveStatus(systolic, diastolic)
        )
    }

    private fun normalizeTimestamp(timestamp: Long): Long {
        return if (timestamp < 1_000_000_000_000L) timestamp * 1000 else timestamp
    }

    private fun resolveStatus(systolic: String, diastolic: String): String {
        val systolicValue = systolic.toIntOrNull()
        val diastolicValue = diastolic.toIntOrNull()

        return if (
            systolicValue != null &&
            diastolicValue != null &&
            (systolicValue > 130 || diastolicValue > 85)
        ) {
            "Elevated"
        } else {
            "Normal"
        }
    }
}
