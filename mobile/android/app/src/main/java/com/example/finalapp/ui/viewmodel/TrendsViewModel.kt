package com.example.finalapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.finalapp.data.model.TrendDataDto
import com.example.finalapp.data.model.TrendDataPoint
import com.example.finalapp.data.repository.Reading
import com.example.finalapp.data.repository.ReadingRepository
import com.example.finalapp.data.repository.RepositoryResult
import com.example.finalapp.data.repository.TrendsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TrendsUiState(
    val selectedPeriod: String = "Weekly",
    val trends: List<TrendDataDto> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

class TrendsViewModel(
    private val repository: TrendsRepository = TrendsRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrendsUiState())
    val uiState: StateFlow<TrendsUiState> = _uiState.asStateFlow()

    private var backendPeriod: String = "week"
    private var remoteTrends: List<TrendDataDto> = emptyList()
    private var remoteInfoMessage: String? = null
    private var remoteErrorMessage: String? = null
    private var latestReadings: List<Reading> = emptyList()

    init {
        observeReadings()
        loadTrends("week")
    }

    fun setPeriod(period: String) {
        backendPeriod = when (period) {
            "Daily" -> "day"
            "Weekly" -> "week"
            "Monthly" -> "month"
            else -> "week"
        }

        _uiState.update { it.copy(selectedPeriod = period) }
        loadTrends(backendPeriod)
    }

    private fun observeReadings() {
        viewModelScope.launch {
            ReadingRepository.readings.collectLatest { readings ->
                latestReadings = readings
                applyMergedState()
            }
        }
    }

    private fun loadTrends(period: String) {
        backendPeriod = period
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            when (val result = repository.fetchTrends(period)) {
                is RepositoryResult.Success -> {
                    remoteTrends = result.data.trends
                    remoteInfoMessage = result.data.message
                    remoteErrorMessage = null
                }

                is RepositoryResult.Error -> {
                    remoteTrends = emptyList()
                    remoteInfoMessage = null
                    remoteErrorMessage = result.message
                }
            }

            applyMergedState()
        }
    }

    private fun applyMergedState() {
        val mergedTrends = mergeTrends(
            remoteTrends = remoteTrends,
            readings = latestReadings,
            period = backendPeriod
        )

        _uiState.update {
            it.copy(
                trends = mergedTrends,
                isLoading = false,
                errorMessage = remoteErrorMessage.takeIf { message -> mergedTrends.isEmpty() && !message.isNullOrBlank() },
                infoMessage = remoteInfoMessage
            )
        }
    }
}

private fun mergeTrends(
    remoteTrends: List<TrendDataDto>,
    readings: List<Reading>,
    period: String
): List<TrendDataDto> {
    val localByType = buildLocalTrends(readings, period).associateBy { it.type }
    val remoteByType = remoteTrends.associateBy { it.type }
    val orderedTypes = linkedSetOf("systolic", "diastolic", "heart_rate").apply {
        addAll(remoteByType.keys)
        addAll(localByType.keys)
    }

    return orderedTypes.mapNotNull { type ->
        val remoteTrend = remoteByType[type]
        val localTrend = localByType[type]

        when {
            remoteTrend == null && localTrend == null -> null
            remoteTrend == null -> localTrend
            localTrend == null -> remoteTrend
            else -> mergeTrend(remoteTrend, localTrend)
        }
    }
}

private fun buildLocalTrends(
    readings: List<Reading>,
    period: String
): List<TrendDataDto> {
    val now = System.currentTimeMillis()
    val cutoff = when (period) {
        "day" -> now - 86_400_000L
        "month" -> now - 30L * 86_400_000L
        else -> now - 7L * 86_400_000L
    }

    val filtered = readings
        .asSequence()
        .filter { it.timestamp >= cutoff }
        .sortedBy { it.timestamp }
        .toList()

    return listOfNotNull(
        buildLocalTrend("systolic", filtered) { it.systolic.toFloatOrNull() },
        buildLocalTrend("diastolic", filtered) { it.diastolic.toFloatOrNull() },
        buildLocalTrend("heart_rate", filtered) { it.pulse.toFloatOrNull() }
    )
}

private fun buildLocalTrend(
    type: String,
    readings: List<Reading>,
    valueSelector: (Reading) -> Float?
): TrendDataDto? {
    val points = readings.mapNotNull { reading ->
        val value = valueSelector(reading)?.takeIf { it > 0f } ?: return@mapNotNull null
        TrendDataPoint(timestamp = normalizeTrendTimestamp(reading.timestamp), value = value)
    }

    if (points.isEmpty()) return null

    return TrendDataDto(
        type = type,
        dataPoints = points,
        average = points.map { it.value }.average().toFloat(),
        min = points.minOf { it.value },
        max = points.maxOf { it.value }
    )
}

private fun mergeTrend(
    remoteTrend: TrendDataDto,
    localTrend: TrendDataDto
): TrendDataDto {
    val mergedPoints = linkedMapOf<Long, TrendDataPoint>()

    remoteTrend.dataPoints
        .sortedBy { normalizeTrendTimestamp(it.timestamp) }
        .forEach { point ->
            mergedPoints[normalizeTrendTimestamp(point.timestamp)] = point.copy(
                timestamp = normalizeTrendTimestamp(point.timestamp)
            )
        }

    localTrend.dataPoints
        .sortedBy { normalizeTrendTimestamp(it.timestamp) }
        .forEach { point ->
            mergedPoints[normalizeTrendTimestamp(point.timestamp)] = point.copy(
                timestamp = normalizeTrendTimestamp(point.timestamp)
            )
        }

    val points = mergedPoints.values.toList()
    if (points.isEmpty()) return remoteTrend

    return TrendDataDto(
        type = remoteTrend.type,
        dataPoints = points,
        average = points.map { it.value }.average().toFloat(),
        min = points.minOf { it.value },
        max = points.maxOf { it.value }
    )
}

private fun normalizeTrendTimestamp(timestamp: Long): Long {
    return if (timestamp < 1_000_000_000_000L) timestamp * 1000 else timestamp
}
