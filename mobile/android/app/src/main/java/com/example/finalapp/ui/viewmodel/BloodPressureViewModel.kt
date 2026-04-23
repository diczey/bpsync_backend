package com.example.finalapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.finalapp.data.model.TrendDataPoint
import com.example.finalapp.data.repository.RepositoryResult
import com.example.finalapp.data.repository.ReadingRepository
import com.example.finalapp.data.repository.TrendsRepository
import com.example.finalapp.data.repository.DashboardRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.lang.System.currentTimeMillis

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
        observeReadings()
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

            var latestSys: Int
            var latestDia: Int
            var status: String

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

            // Prefer the newest reading captured locally by the measurement flow.
            ReadingRepository.latestReading()?.let { localLatest ->
                val localSys = localLatest.systolic.toIntOrNull()
                val localDia = localLatest.diastolic.toIntOrNull()
                if (localSys != null && localDia != null && localSys > 0 && localDia > 0) {
                    latestSys = localSys
                    latestDia = localDia
                    status = localLatest.status
                }
            }

            var avgSys = 0f
            var avgDia = 0f
            var sysPoints = emptyList<TrendDataPoint>()
            var diaPoints = emptyList<TrendDataPoint>()
            var sysMin = 0f; var sysMax = 1f
            var diaMin = 0f; var diaMax = 1f
            var error: String? = null

            if (trendsResult is RepositoryResult.Success) {
                val trends = trendsResult.data.trends
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
                if (sysPoints.isEmpty() && diaPoints.isEmpty() && latestSys > 0 && latestDia > 0) {
                    val now = currentTimeMillis()
                    sysPoints = listOf(TrendDataPoint(timestamp = now, value = latestSys.toFloat()))
                    diaPoints = listOf(TrendDataPoint(timestamp = now, value = latestDia.toFloat()))
                    avgSys = latestSys.toFloat()
                    avgDia = latestDia.toFloat()
                    sysMin = latestSys * 0.95f
                    sysMax = latestSys * 1.05f
                    diaMin = latestDia * 0.95f
                    diaMax = latestDia * 1.05f
                }
                if (sysPoints.isEmpty() && diaPoints.isEmpty()) {
                    error = trendsResult.data.message ?: "No trend data yet."
                }
            } else if (trendsResult is RepositoryResult.Error) {
                error = trendsResult.message
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
                    isLoading = false,
                    errorMessage = error
                )
            }
        }
    }

    private fun observeReadings() {
        viewModelScope.launch {
            ReadingRepository.readings.collectLatest { readings ->
                val latest = readings.firstOrNull() ?: return@collectLatest
                val latestSys = latest.systolic.toIntOrNull() ?: return@collectLatest
                val latestDia = latest.diastolic.toIntOrNull() ?: return@collectLatest
                val timestamp = latest.timestamp

                _uiState.update { current ->
                    val updatedSysPoints = appendTrendPoint(current.sysTrendPoints, timestamp, latestSys.toFloat())
                    val updatedDiaPoints = appendTrendPoint(current.diaTrendPoints, timestamp, latestDia.toFloat())

                    current.copy(
                        latestSystolic = latestSys,
                        latestDiastolic = latestDia,
                        statusLabel = latest.status,
                        avgSystolic = averageOf(updatedSysPoints),
                        avgDiastolic = averageOf(updatedDiaPoints),
                        sysTrendPoints = updatedSysPoints,
                        diaTrendPoints = updatedDiaPoints,
                        sysTrendMin = minBoundOf(updatedSysPoints),
                        sysTrendMax = maxBoundOf(updatedSysPoints),
                        diaTrendMin = minBoundOf(updatedDiaPoints),
                        diaTrendMax = maxBoundOf(updatedDiaPoints)
                    )
                }
            }
        }
    }
}

private fun appendTrendPoint(
    points: List<TrendDataPoint>,
    timestamp: Long,
    value: Float
): List<TrendDataPoint> {
    val normalizedTimestamp = if (timestamp < 1_000_000_000_000L) timestamp * 1000 else timestamp
    val updated = points.filterNot { point ->
        val pointTimestamp = if (point.timestamp < 1_000_000_000_000L) point.timestamp * 1000 else point.timestamp
        pointTimestamp == normalizedTimestamp
    } + TrendDataPoint(timestamp = normalizedTimestamp, value = value)

    return updated.sortedBy { it.timestamp }
}

private fun averageOf(points: List<TrendDataPoint>): Float {
    if (points.isEmpty()) return 0f
    return points.map { it.value }.average().toFloat()
}

private fun minBoundOf(points: List<TrendDataPoint>): Float {
    val minValue = points.minOfOrNull { it.value } ?: 0f
    return if (minValue > 0f) minValue * 0.95f else 0f
}

private fun maxBoundOf(points: List<TrendDataPoint>): Float {
    val maxValue = points.maxOfOrNull { it.value } ?: 1f
    return if (maxValue > 0f) maxValue * 1.05f else 1f
}
