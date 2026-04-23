package com.example.finalapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.finalapp.data.repository.DashboardRepository
import com.example.finalapp.data.repository.ReadingRepository
import com.example.finalapp.data.repository.RepositoryResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HealthStatusUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val message: String? = null,
    val healthScore: Int = 0,
    val overallStatus: String = "No Data",
    val bloodPressureStatus: String = "No Data",
    val heartRateStatus: String = "No Data",
    val calibrationStartedAt: Long? = null,
    val calibrationReadyAt: Long? = null,
    val weeklyStatusReadyAt: Long? = null,
    val secondsUntilCalibrated: Int = 0,
    val secondsUntilWeeklyStatus: Int = 0,
    val trackingDay: Int = 0,
    val isCalibrated: Boolean = false,
    val isWeekReady: Boolean = false,
    val countdownPhase: String = "awaiting_device",
    val statusMode: String = "standard",
)

class HealthStatusViewModel(
    private val dashboardRepository: DashboardRepository = DashboardRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(HealthStatusUiState())
    val uiState: StateFlow<HealthStatusUiState> = _uiState.asStateFlow()

    init {
        observeReadings()
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            when (val result = dashboardRepository.fetchHealthStatus()) {
                is RepositoryResult.Success -> {
                    val data = result.data
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = null,
                            message = data.message,
                            healthScore = data.healthScore,
                            overallStatus = data.overallStatus,
                            bloodPressureStatus = data.bloodPressureStatus,
                            heartRateStatus = data.heartRateStatus,
                            calibrationStartedAt = data.calibrationStartedAt,
                            calibrationReadyAt = data.calibrationReadyAt,
                            weeklyStatusReadyAt = data.weeklyStatusReadyAt,
                            secondsUntilCalibrated = data.secondsUntilCalibrated,
                            secondsUntilWeeklyStatus = data.secondsUntilWeeklyStatus,
                            trackingDay = data.trackingDay,
                            isCalibrated = data.isCalibrated,
                            isWeekReady = data.isWeekReady,
                            countdownPhase = data.countdownPhase,
                            statusMode = data.statusMode,
                        )
                    }
                    applyReadingFallback()
                }

                is RepositoryResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.message,
                            message = result.message
                        )
                    }
                    applyReadingFallback()
                }
            }
        }
    }

    private fun observeReadings() {
        viewModelScope.launch {
            ReadingRepository.readings.collectLatest {
                applyReadingFallback()
            }
        }
    }

    private fun applyReadingFallback() {
        val latestReading = ReadingRepository.latestReading()

        _uiState.update { current ->
            val fallbackBpStatus = latestReading?.let {
                classifyBloodPressureStatus(it.systolic.toIntOrNull(), it.diastolic.toIntOrNull())
            } ?: current.bloodPressureStatus

            val fallbackHeartRateStatus = latestReading?.pulse?.toIntOrNull()?.let(::classifyHeartRateStatus)
                ?: current.heartRateStatus

            val resolvedBpStatus = current.bloodPressureStatus.takeUnless { it == "No Data" } ?: fallbackBpStatus
            val resolvedHeartRateStatus = current.heartRateStatus.takeUnless { it == "No Data" } ?: fallbackHeartRateStatus
            val resolvedOverallStatus = current.overallStatus.takeUnless { it == "No Data" }
                ?: listOf(resolvedBpStatus, resolvedHeartRateStatus)
                    .firstOrNull { status -> status != "No Data" }
                ?: "No Data"

            current.copy(
                overallStatus = resolvedOverallStatus,
                bloodPressureStatus = resolvedBpStatus,
                heartRateStatus = resolvedHeartRateStatus
            )
        }
    }
}

private fun classifyBloodPressureStatus(systolic: Int?, diastolic: Int?): String {
    if (systolic == null || diastolic == null || systolic <= 0 || diastolic <= 0) return "No Data"
    if (systolic < 90 || diastolic < 60) return "Low"
    if (systolic >= 140 || diastolic >= 90) return "High"
    if (systolic >= 120 || diastolic >= 80) return "Elevated"
    return "Normal"
}

private fun classifyHeartRateStatus(heartRate: Int?): String {
    if (heartRate == null || heartRate <= 0) return "No Data"
    if (heartRate < 60) return "Low"
    if (heartRate > 100) return "High"
    return "Normal"
}
