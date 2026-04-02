package com.example.finalapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.finalapp.data.model.PpgDataPoint
import com.example.finalapp.data.repository.PpgRepository
import com.example.finalapp.data.repository.RepositoryResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PpgUiState(
    val avgQuality: Int = 0,
    val signalStability: Int = 0,
    val highestQuality: Int = 0,
    val lowestQuality: Int = 0,
    val chartPoints: List<PpgDataPoint> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class PpgViewModel(
    private val repository: PpgRepository = PpgRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(PpgUiState())
    val uiState: StateFlow<PpgUiState> = _uiState.asStateFlow()

    init {
        startPolling()
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (isActive) {
                fetchSignal()
                // Polling at a medium frequency to simulate live updates while 
                // avoiding spamming the mock Rest API heavily.
                delay(3000)
            }
        }
    }

    fun fetchSignal() {
        viewModelScope.launch {
            if (_uiState.value.chartPoints.isEmpty()) {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            }

            when (val result = repository.fetchPpgSignal()) {
                is RepositoryResult.Success -> {
                    _uiState.update {
                        it.copy(
                            avgQuality = result.data.avgQuality,
                            signalStability = result.data.signalStability,
                            highestQuality = result.data.highestQuality,
                            lowestQuality = result.data.lowestQuality,
                            chartPoints = result.data.chartPoints.sortedBy { p -> p.timestamp },
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                }
                is RepositoryResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = if (it.chartPoints.isEmpty()) result.message else it.errorMessage
                        )
                    }
                }
            }
        }
    }
}
