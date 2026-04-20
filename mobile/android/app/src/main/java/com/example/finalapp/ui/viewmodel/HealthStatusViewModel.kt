package com.example.finalapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.finalapp.data.repository.DashboardRepository
import com.example.finalapp.data.repository.RepositoryResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val oxygenStatus: String = "No Data",
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
                            oxygenStatus = data.oxygenStatus,
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
                }

                is RepositoryResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.message,
                            message = result.message
                        )
                    }
                }
            }
        }
    }
}
