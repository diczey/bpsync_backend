package com.example.finalapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.finalapp.data.repository.Reading
import com.example.finalapp.data.repository.ReadingRepository
import com.example.finalapp.data.repository.RepositoryResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MeasurementsUiState(
    val readings: List<Reading> = emptyList(),
    val totalCount: Int = 0,
    val normalCount: Int = 0,
    val alertCount: Int = 0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class MeasurementsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(MeasurementsUiState())
    val uiState: StateFlow<MeasurementsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            ReadingRepository.readings.collect { readings ->
                _uiState.update {
                    it.copy(
                        readings = readings,
                        totalCount = readings.size,
                        normalCount = readings.count { reading -> reading.status == "Normal" },
                        alertCount = readings.count { reading -> reading.status != "Normal" }
                    )
                }
            }
        }

        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            when (val result = ReadingRepository.syncFromApi()) {
                is RepositoryResult.Success -> {
                    updateReadings(result.data)
                }

                is RepositoryResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = if (it.readings.isEmpty()) result.message else null
                        )
                    }
                }
            }
        }
    }

    private fun updateReadings(readings: List<Reading>) {
        _uiState.update {
            it.copy(
                readings = readings,
                totalCount = readings.size,
                normalCount = readings.count { reading -> reading.status == "Normal" },
                alertCount = readings.count { reading -> reading.status != "Normal" },
                isLoading = false,
                errorMessage = null
            )
        }
    }
}
