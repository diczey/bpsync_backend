package com.example.finalapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.finalapp.data.model.TrendDataPoint
import com.example.finalapp.data.repository.RepositoryResult
import com.example.finalapp.data.repository.TrendsRepository
import com.example.finalapp.data.repository.DashboardRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BloodPressureUiState(
    val latestSystolic: Int = 0,
    val latestDiastolic: Int = 0,
    val statusLabel: String = "Normal",
    val avgSystolic: Float = 0f,
    val avgDiastolic: Float = 0f,
    val sysTrendPoints: List<TrendDataPoint> = emptyList(),
    val diaTrendPoints: List<TrendDataPoint> = emptyList(),
    val sysTrendMin: Float = 0f,
    val sysTrendMax: Float = 1f,
    val diaTrendMin: Float = 0f,
    val diaTrendMax: Float = 1f,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class BloodPressureViewModel(
    private val trendsRepository: TrendsRepository = TrendsRepository(),
    private val dashboardRepository: DashboardRepository = DashboardRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(BloodPressureUiState())
    val uiState: StateFlow<BloodPressureUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            // Fetch dashboard summary AND 7-day trends in parallel
            val dashboardDeferred = async { dashboardRepository.fetchDashboardSummary() }
            val trendsDeferred = async { trendsRepository.fetchTrends("week") }

            val dashboardResult = dashboardDeferred.await()
            val trendsResult = trendsDeferred.await()

            val latestSys: Int
            val latestDia: Int
            val status: String

            if (dashboardResult is RepositoryResult.Success) {
                val summary = dashboardResult.data
                latestSys = summary.latestSystolic
                latestDia = summary.latestDiastolic
                status = summary.healthStatus
            } else {
                latestSys = 0
                latestDia = 0
                status = "No Data"
            }

            var avgSys = 0f
            var avgDia = 0f
            var sysPoints = emptyList<TrendDataPoint>()
            var diaPoints = emptyList<TrendDataPoint>()
            var sysMin = 0f; var sysMax = 1f
            var diaMin = 0f; var diaMax = 1f

            if (trendsResult is RepositoryResult.Success) {
                val trends = trendsResult.data
                val sysTrend = trends.find { it.type == "systolic" }
                val diaTrend = trends.find { it.type == "diastolic" }

                if (sysTrend != null) {
                    avgSys = sysTrend.average
                    sysPoints = sysTrend.dataPoints
                    sysMin = sysTrend.min * 0.95f
                    sysMax = sysTrend.max * 1.05f
                }
                if (diaTrend != null) {
                    avgDia = diaTrend.average
                    diaPoints = diaTrend.dataPoints
                    diaMin = diaTrend.min * 0.95f
                    diaMax = diaTrend.max * 1.05f
                }
            }

            _uiState.update {
                it.copy(
                    latestSystolic = latestSys,
                    latestDiastolic = latestDia,
                    statusLabel = status,
                    avgSystolic = avgSys,
                    avgDiastolic = avgDia,
                    sysTrendPoints = sysPoints,
                    diaTrendPoints = diaPoints,
                    sysTrendMin = sysMin,
                    sysTrendMax = sysMax,
                    diaTrendMin = diaMin,
                    diaTrendMax = diaMax,
                    isLoading = false
                )
            }
        }
    }
}
