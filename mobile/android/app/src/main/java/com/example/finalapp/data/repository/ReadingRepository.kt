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
    val timestamp: Long,
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
        val now = System.currentTimeMillis()
        val sdfDate = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
        val sdfTime = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val currentDate = sdfDate.format(Date(now))
        val currentTime = sdfTime.format(Date(now))

        val reading = Reading(
            id = (_readings.value.maxOfOrNull { it.id } ?: 0) + 1,
            timestamp = now,
            systolic = systolic,
            diastolic = diastolic,
            pulse = pulse,
            spo2 = spo2,
            date = currentDate,
            time = currentTime,
            status = resolveStatus(systolic, diastolic)
        )

        _readings.value = (listOf(reading) + _readings.value).sortedByDescending { it.timestamp }
    }

    fun upsertFromBleInference(reading: BleInferredReadingDto) {
        val timestamp = normalizeTimestamp(reading.timestamp)
        val date = Date(timestamp)
        val sdfDate = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
        val sdfTime = SimpleDateFormat("hh:mm a", Locale.getDefault())

        val mapped = Reading(
            id = "ble-${reading.timestamp}".hashCode(),
            timestamp = timestamp,
            systolic = reading.systolic.takeIf { it > 0 }?.toString() ?: "--",
            diastolic = reading.diastolic.takeIf { it > 0 }?.toString() ?: "--",
            pulse = reading.heartRate.takeIf { it > 0 }?.toString() ?: "--",
            spo2 = "--",
            date = sdfDate.format(date),
            time = sdfTime.format(date),
            status = reading.category.ifBlank {
                resolveStatus(reading.systolic.toString(), reading.diastolic.toString())
            }
        )

        _readings.value = (listOf(mapped) + _readings.value.filterNot { it.id == mapped.id })
            .sortedByDescending { it.timestamp }
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

    fun latestKnownSpo2(): String? {
        return _readings.value
            .firstNotNullOfOrNull { reading ->
                reading.spo2.toIntOrNull()?.takeIf { it > 0 }?.toString()
            }
    }

    private fun mapToReading(dto: HealthReadingDto): Reading {
        val timestamp = normalizeTimestamp(dto.timestamp)
        val date = Date(timestamp)
        val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val systolic = displayValue(dto.systolicBp)
        val diastolic = displayValue(dto.diastolicBp)

        return Reading(
            id = dto.id.hashCode(),
            timestamp = timestamp,
            systolic = systolic,
            diastolic = diastolic,
            pulse = displayValue(dto.heartRate),
            spo2 = displayValue(dto.spo2),
            date = dateFormat.format(date),
            time = timeFormat.format(date),
            status = resolveStatus(systolic, diastolic)
        )
    }

    private fun normalizeTimestamp(timestamp: Long): Long {
        return if (timestamp < 1_000_000_000_000L) timestamp * 1000 else timestamp
    }

    private fun displayValue(value: Int?): String {
        return value?.takeIf { it > 0 }?.toString() ?: "--"
    }

    private fun resolveStatus(systolic: String, diastolic: String): String {
        val systolicValue = systolic.toIntOrNull()
        val diastolicValue = diastolic.toIntOrNull()

        if (systolicValue == null || diastolicValue == null || systolicValue <= 0 || diastolicValue <= 0) {
            return "No Data"
        }

        return if (systolicValue > 130 || diastolicValue > 85) {
            "Elevated"
        } else {
            "Normal"
        }
    }
}
